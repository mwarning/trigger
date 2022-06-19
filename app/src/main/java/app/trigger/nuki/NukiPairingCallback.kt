package app.trigger.nuki

import app.trigger.Utils.byteArrayToHexString
import app.trigger.Settings.addSetup
import app.trigger.DoorReply.ReplyCode
import android.bluetooth.BluetoothGattCharacteristic
import org.libsodium.jni.Sodium
import android.bluetooth.BluetoothGatt
import app.trigger.nuki.NukiCommand
import app.trigger.*
import java.util.*

internal class NukiPairingCallback(setup_id: Int, listener: OnTaskCompleted, var setup: NukiDoorSetup) : NukiCallback(setup_id, listener, NukiCallback.Companion.PAIRING_SERVICE_UUID, NukiCallback.Companion.PAIRING_GDIO_XTERISTIC_UUID) {
    var data: ByteArray? = null
    var secret_key: ByteArray? = null
    var public_key: ByteArray? = null
    var nuki_public_key: ByteArray? = null
    var shared_key: ByteArray? = null
    var auth_id : Long = 0 // unique identifier of the nuki smartlock or bridge (unsigned int)
    var app_id : Long // unique identifier of the app (unsigned int)
    val id_type = 0 // 0 (app), 1 (bridge), 2 (FOB), 3 (Keypad)
    val user_name: String
    var uuid: ByteArray? = null
    var own_nonce = ByteArray(0)
    var challenge_received = false

    override fun onConnected(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "onConnected")
        if (!setup.shared_key.isEmpty()) {
            listener.onTaskResult(setup_id, ReplyCode.LOCAL_ERROR, "Already paired to some device!")
            closeConnection(gatt)
            return
        }
        val nr = NukiCommand.NukiRequest(0x03)
        characteristic.setValue(NukiRequestHandler.Companion.crc_calc_and_add(nr.generate()))
        val ok = gatt.writeCharacteristic(characteristic)
        if (!ok) {
            Log.e(TAG, "writeCharacteristic failed for NukiRequest")
            closeConnection(gatt)
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "onCharacteristicChanged, uiid: " + characteristic.uuid + ": " + byteArrayToHexString(characteristic.value))
        data = if (data == null) {
            characteristic.value
        } else {
            NukiTools.concat(data!!, characteristic.value)
        }
        val m: NukiCommand? = NukiRequestHandler.parse(NukiRequestHandler.Companion.crc_check_and_strip(data))
        if (m == null) {
            return
        } else {
            data = ByteArray(0)
        }
        if (m is NukiCommand.NukiError) {
            Log.d(TAG, "NukiCommand.NukiError")
            listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, m.asString())
            closeConnection(gatt)
        } else if (m is NukiCommand.NukiPublicKey) {
            Log.d(TAG, "NukiCommand.NukiPublicKey")
            nuki_public_key = m.public_key
            public_key = ByteArray(Sodium.crypto_box_publickeybytes())
            secret_key = ByteArray(Sodium.crypto_box_secretkeybytes())
            Sodium.crypto_box_keypair(public_key, secret_key)

            // send own public key
            val pk = NukiCommand.NukiPublicKey(public_key!!)
            characteristic.setValue(NukiRequestHandler.Companion.crc_calc_and_add(pk.generate()))
            val ok = gatt.writeCharacteristic(characteristic)
            if (!ok) {
                Log.e(TAG, "writeCharacteristic failed for NukiPublicKey")
                closeConnection(gatt)
                return
            }
            shared_key = NukiRequestHandler.Companion.getSharedKey(nuki_public_key, secret_key)
        } else if (m is NukiCommand.NukiChallenge) {
            Log.d(TAG, "NukiCommand.NukiChallenge challenge_received: $challenge_received")
            val nc = m
            if (challenge_received == false) {
                challenge_received = true
                //Log.i(TAG, "NukiCommand: NukiChallenge: nonce: " + Utils.byteArrayToHexString(nuki_nonce));
                val valueR = NukiTools.concat(public_key!!, nuki_public_key!!, nc.nonce)
                val authenticator = ByteArray(Sodium.crypto_auth_hmacsha256_bytes())
                if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR.size, shared_key) != 0) {
                    Log.e(TAG, "crypto_auth_hmacsha256 failed")
                    closeConnection(gatt)
                    return
                }

                // send authenticator
                val naa = NukiCommand.NukiAuthAuthentication(authenticator)
                characteristic.setValue(NukiRequestHandler.Companion.crc_calc_and_add(naa.generate()))
                val ok = gatt.writeCharacteristic(characteristic)
                if (!ok) {
                    Log.e(TAG, "writeCharacteristic failed for NukiAuthAuthentication")
                    closeConnection(gatt)
                    return
                }
            } else {
                own_nonce = ByteArray(32)
                Sodium.randombytes(own_nonce, own_nonce.size)
                if (app_id == 0L) {
                    // get random unsigned int
                    app_id = Random().nextLong() and 0xffffffffL
                }
                val valueR = NukiTools.concat(NukiTools.from8(id_type), NukiTools.from32_app_id(app_id), NukiTools.nameToBytes(user_name, 32), own_nonce, nc.nonce)
                val authenticator = ByteArray(Sodium.crypto_auth_hmacsha256_bytes())
                if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR!!.size, shared_key) != 0) {
                    Log.e(TAG, "crypto_auth_hmacsha256 failed")
                    closeConnection(gatt)
                    return
                }
                val nad =
                    NukiCommand.NukiAuthData(authenticator, id_type, app_id, user_name, own_nonce)
                characteristic.setValue(NukiRequestHandler.Companion.crc_calc_and_add(nad.generate()))
                val ok = gatt.writeCharacteristic(characteristic)
                if (!ok) {
                    Log.e(TAG, "writeCharacteristic failed for NukiAuthData")
                    closeConnection(gatt)
                    return
                }
            }
        } else if (m is NukiCommand.NukiAuthID) {
            Log.d(TAG, "NukiCommand.NukiAuthID: auth_id: $auth_id")
            val nai = m
            auth_id = nai.auth_id
            uuid = nai.uuid

            //check authenticator
            if (!nai.verify(shared_key, own_nonce)) {
                Log.e(TAG, "authenticator invalid!")
                closeConnection(gatt)
                return
            }
            val shared_key: ByteArray? = NukiRequestHandler.getSharedKey(nuki_public_key, secret_key)
            val valueR = NukiTools.concat(NukiTools.from32_auth_id(auth_id), nai.nonce)
            val authenticator = ByteArray(Sodium.crypto_auth_hmacsha256_bytes())
            if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR!!.size, shared_key) != 0) {
                Log.e(TAG, "crypto_auth_hmacsha256 failed")
                closeConnection(gatt)
                return
            }
            val naidc = NukiCommand.NukiAuthIdConfirm(authenticator, auth_id)
            characteristic.setValue(NukiRequestHandler.Companion.crc_calc_and_add(naidc.generate()))
            val ok = gatt.writeCharacteristic(characteristic)
            if (!ok) {
                Log.e(TAG, "writeCharacteristic failed for NukiAuthIdConfirm")
                closeConnection(gatt)
                return
            }
        } else if (m is NukiCommand.NukiStatus) {
            Log.d(TAG, "NukiCommand.NukiStatus")
            if (m.status != 0) {
                listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, "Pairing failed.")
                closeConnection(gatt)
                return
            }
            if (shared_key != null && shared_key!!.size == 32 && uuid != null && uuid!!.size == 16) {
                setup.auth_id = auth_id
                setup.app_id = app_id
                setup.shared_key = byteArrayToHexString(shared_key)
                addSetup(setup)
                listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, "Pairing complete.")
                closeConnection(gatt)
            } else {
                listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, "Failed to set authorization data.")
                closeConnection(gatt)
            }
        } else {
            Log.e(TAG, "Unhandled command")
            closeConnection(gatt)
        }
    }

    companion object {
        private const val TAG = "PairingCallback"
    }

    init {
        user_name = setup.user_name
        //this.auth_id = setup.auth_id; // we get this from the nuki
        app_id = setup.app_id
    }
}