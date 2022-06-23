package app.trigger.nuki

import app.trigger.Utils.isEmpty
import app.trigger.DoorReply.ReplyCode
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import org.libsodium.jni.Sodium
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.trigger.nuki.NukiCommand.NukiRequest
import app.trigger.nuki.NukiCommand.NukiPublicKey
import app.trigger.nuki.NukiCommand.NukiChallenge
import app.trigger.nuki.NukiCommand.NukiAuthAuthentication
import app.trigger.nuki.NukiCommand.NukiAuthData
import app.trigger.nuki.NukiCommand.NukiAuthID
import app.trigger.nuki.NukiCommand.NukiStates
import app.trigger.nuki.NukiCommand.NukiAuthIdConfirm
import app.trigger.nuki.NukiCommand.NukiStatus
import app.trigger.nuki.NukiCommand.NukiError
import org.libsodium.jni.NaCl
import app.trigger.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class NukiRequestHandler(private val listener: OnTaskCompleted, private val setup: NukiDoorSetup, private val action: MainActivity.Action) : Thread() {
    private fun getAddress(adapter: BluetoothAdapter, device_name: String): String? {
        val pairedDevices = adapter.bondedDevices
        var address = ""

        for (device in pairedDevices) {
            if (device.name != null && device.name == device_name
                    || device.address.uppercase(Locale.getDefault()) == device_name.uppercase(Locale.getDefault())) {
                address = device.address
            }
        }

        if (address.isEmpty()) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Device not paired yet.")
            return null
        } else {
            return address
        }
    }

    override fun run() {
        if (bluetooth_in_use.get()) {
            Log.w(TAG, "Bluetooth busy => abort action")
            if (action !== MainActivity.Action.fetch_state) {
                listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Bluetooth device is busy.")
            }
            return
        }
        if (!(listener as Context).packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            listener.onTaskResult(setup.id, ReplyCode.DISABLED, "Bluetooth Low Energy is not supported.")
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            listener.onTaskResult(setup.id, ReplyCode.DISABLED, "Bluetooth is disabled.")
            return
        }
        if (setup.device_name.isEmpty()) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "No device name set.")
            return
        }
        if (setup.user_name.isEmpty()) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "No user name set.")
            return
        }
        val address = getAddress(adapter, setup.device_name)
        if (address == null) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "No device found.")
            return
        }
        val device = adapter.getRemoteDevice(address)
        if (device == null) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Device not found.")
            return
        }

        if (isEmpty(setup.shared_key) && action === MainActivity.Action.fetch_state) {
            // ignore query for door state - not paired yet
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "")
            return
        }

        if (!bluetooth_in_use.compareAndSet(false, true)) {
            // setting the variable failed
            return
        }

        val callback: NukiCallback
        if (isEmpty(setup.shared_key)) {
            // initiate paring
            callback = NukiPairingCallback(setup.id, listener, setup)
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Start Pairing.")
        } else when (action) {
            MainActivity.Action.open_door -> callback = NukiLockActionCallback(setup.id, listener, setup, 0x01 /*unlock*/)
            MainActivity.Action.ring_door -> {
                listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Bell not supported.")
                return
            }
            MainActivity.Action.close_door -> callback = NukiLockActionCallback(setup.id, listener, setup, 0x02 /*lock*/)
            MainActivity.Action.fetch_state -> callback = NukiReadLockStateCallback(setup.id, listener, setup)
            else -> callback = NukiReadLockStateCallback(setup.id, listener, setup)
        }

        val gatt: BluetoothGatt? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(listener as Context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(listener as Context, false, callback)
        }

        if (gatt == null) {
            // failed to start connection
            bluetooth_in_use.set(false)
        }
    }

    companion object {
        private const val TAG = "NukiRequestHandler"
        private var sodium: Sodium? = null

        fun parse(data: ByteArray?): NukiCommand? {
            if (data == null || data.size < 2) {
                return null
            }

            val command = NukiTools.read16(data, 0)
            return when (command) {
                0x0001 -> {
                    if (data.size != 4) {
                        return null
                    }
                    val command_id = NukiTools.read16(data, 2)
                    NukiRequest(command_id)
                }
                0x0003 -> {
                    if (data.size != 34) {
                        return null
                    }
                    val public_key = ByteArray(32)
                    System.arraycopy(data, 2, public_key, 0, public_key.size)
                    NukiPublicKey(public_key)
                }
                0x0004 -> {
                    if (data.size != 34) {
                        return null
                    }
                    val nonce = ByteArray(32)
                    System.arraycopy(data, 2, nonce, 0, nonce.size)
                    NukiChallenge(nonce)
                }
                0x0005 -> {
                    if (data.size != 34) {
                        return null
                    }
                    val nonce = ByteArray(32)
                    System.arraycopy(data, 2, nonce, 0, nonce.size)
                    NukiAuthAuthentication(nonce)
                }
                0x0006 -> {
                    if (data.size != 103) {
                        return null
                    }
                    val authenticator = ByteArray(32)
                    val id_type = ByteArray(1)
                    val app_id = ByteArray(4)
                    val name = ByteArray(32)
                    val nonce = ByteArray(32)
                    System.arraycopy(data, 2, authenticator, 0, authenticator.size)
                    System.arraycopy(data, 2 + authenticator.size, id_type, 0, id_type.size)
                    System.arraycopy(data, 2 + authenticator.size + id_type.size, app_id, 0, app_id.size)
                    System.arraycopy(data, 2 + authenticator.size + id_type.size + app_id.size, name, 0, name.size)
                    System.arraycopy(data, 2 + authenticator.size + id_type.size + app_id.size + name.size, nonce, 0, nonce.size)
                    NukiAuthData(authenticator, id_type[0].toInt(), NukiTools.read32_app_id(app_id, 0), String(name), nonce)
                }
                0x0007 -> {
                    if (data.size != 86) {
                        return null
                    }
                    val authenticator = ByteArray(32)
                    val auth_id = ByteArray(4)
                    val uuid = ByteArray(16)
                    val nonce = ByteArray(32)
                    System.arraycopy(data, 2, authenticator, 0, authenticator.size)
                    System.arraycopy(data, 2 + authenticator.size, auth_id, 0, auth_id.size)
                    System.arraycopy(data, 2 + authenticator.size + auth_id.size, uuid, 0, uuid.size)
                    System.arraycopy(data, 2 + authenticator.size + auth_id.size + uuid.size, nonce, 0, nonce.size)
                    NukiAuthID(authenticator, NukiTools.read32_auth_id(auth_id, 0), uuid, nonce)
                }
                0x000C -> {
                    if (data.size != 21) {
                        return null
                    }
                    val nuki_state = data[2].toInt()
                    val lock_state = data[3].toInt()
                    val trigger = data[4].toInt()
                    val year = NukiTools.read16(data, 5)
                    val month = data[7].toInt()
                    val day = data[8].toInt()
                    val hour = data[9].toInt()
                    val minute = data[10].toInt()
                    val second = data[11].toInt()
                    val current_time = String.format(Locale.ENGLISH, "%02d-%02d-%d %02d:%02d:%02d", day, month, year, hour, minute, second)
                    val time_offset = NukiTools.read_i16(data, 10)
                    val critical_battery = data[14].toInt()
                    // following fields are ignored here
                    NukiStates(nuki_state, lock_state, trigger, current_time, time_offset, critical_battery)
                }
                0x001E -> {
                    if (data.size != 38 && data.size != 70) {
                        return null
                    }
                    val authenticator = ByteArray(32)
                    System.arraycopy(data, 2, authenticator, 0, authenticator.size)
                    val auth_id = NukiTools.read32_auth_id(data, 2 + authenticator.size)
                    NukiAuthIdConfirm(authenticator, auth_id)
                }
                0x000E -> {
                    if (data.size != 3) {
                        return null
                    }
                    val status = data[2].toInt()
                    NukiStatus(status)
                }
                0x0012 -> {
                    if (data.size != 5) {
                        return null
                    }
                    val error_code = data[2].toInt()
                    val command_id = NukiTools.read16(data, 3)
                    NukiError(error_code, command_id)
                }
                else -> null
            }
        }

        fun getGattState(state: Int): String {
            return when (state) {
                BluetoothGatt.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
                BluetoothGatt.STATE_CONNECTING -> "STATE_CONNECTING"
                BluetoothGatt.STATE_CONNECTED -> "STATE_CONNECTED"
                BluetoothGatt.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
                else -> Integer.toString(state)
            }
        }

        fun getGattStatus(status: Int): String {
            return when (status) {
                BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
                BluetoothGatt.GATT_FAILURE -> "GATT_FAILURE"
                BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
                BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
                BluetoothGatt.GATT_CONNECTION_CONGESTED -> "GATT_CONNECTION_CONGESTED"
                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
                19 -> "DISCONNECTED_BY_DEVICE"
                133 -> "DEVICE_NOT_FOUND"
                8 -> "CONNECTION_TIMEOUT"
                else -> Integer.toString(status)
            }
        }

        fun getSharedKey(nuki_public_key: ByteArray?, secret_key: ByteArray?): ByteArray? {
            val shared_key = ByteArray(Sodium.crypto_core_hsalsa20_outputbytes())
            if (true) {
                if (Sodium.crypto_box_beforenm(shared_key, nuki_public_key, secret_key) != 0) {
                    Log.e(TAG, "crypto_box_beforenm failed")
                    return null
                }
            } else {
                // alternative to crypto_box_beforenm
                val dhk = ByteArray(Sodium.crypto_scalarmult_curve25519_bytes())
                if (Sodium.crypto_scalarmult_curve25519(dhk, secret_key, nuki_public_key) != 0) {
                    Log.e(TAG, "crypto_scalarmult_curve25519 failed")
                    return null
                }
                val inv = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
                val sigma = "expand 32-byte k".toByteArray()
                if (sigma.size != 16) {
                    Log.e(TAG, "wrong sigma length")
                    return null
                }
                if (Sodium.crypto_core_hsalsa20(shared_key, inv, dhk, sigma) != 0) {
                    Log.e(TAG, "crypto_core_hsalsa20 failed")
                    return null
                }
            }
            return shared_key
        }

        // returns command_id + payload (without auth_id/crc fields)
        fun decrypt_message(shared_key: ByteArray?, msg: ByteArray?): ByteArray? {
            val nonce_length = Sodium.crypto_secretbox_noncebytes()
            val header_length = nonce_length + 4 + 2 // nonce + auth_id + length field size

            // nonce + auth_id + length + encrypted(macbytes + auth_id + command_id + crc)
            val min_msg_length = Sodium.crypto_secretbox_noncebytes() + 4 + 2 + Sodium.crypto_secretbox_macbytes() + 8
            if (msg == null || msg.size < min_msg_length) {
                return null
            }

            val length = NukiTools.read16(msg, nonce_length + 4)
            if (msg.size != header_length + length) {
                return null
            }

            val nonce = ByteArray(nonce_length)
            System.arraycopy(msg, 0, nonce, 0, nonce.size)
            val auth_id = NukiTools.read32_auth_id(msg, nonce.size)
            val encrypted = ByteArray(length)
            System.arraycopy(msg, nonce_length + 4 + 2, encrypted, 0, encrypted.size)
            val decrypted = ByteArray(length - Sodium.crypto_secretbox_macbytes())

            if (Sodium.crypto_secretbox_open_easy(decrypted, encrypted, encrypted.size, nonce, shared_key) != 0) {
                Log.e("decrypt_message", "crypto_secretbox_easy failed")
                return null
            }

            if (decrypted.size < 6) {
                return null
            }

            // check auth_id
            if (auth_id != NukiTools.read32_auth_id(decrypted, 0)) {
                Log.e("decrypt_message", "auth_id mismatch")
                return null
            }

            // check crc
            val crc_calc = NukiTools.crc16(decrypted, 0, decrypted.size - 2)
            val crc_read = NukiTools.read16(decrypted, decrypted.size - 2)
            if (crc_calc != crc_read) {
                Log.e("decrypt_message", "crc mismatch")
                return null
            }

            // strip auth_id and crc
            val ret = ByteArray(decrypted.size - 6)
            System.arraycopy(decrypted, 4, ret, 0, ret.size)

            // return command_id + payload
            return ret
        }

        // data is expected to be command_id + payload
        fun encrypt_message(shared_key: ByteArray?, auth_id: Long, pdata: ByteArray?, nonce: ByteArray?): ByteArray? {
            // nonce is provide only for testing purposes!
            var nonce = nonce
            if (nonce == null) {
                nonce = ByteArray(Sodium.crypto_secretbox_noncebytes())
                Sodium.randombytes(nonce, nonce.size)
            }
            if (nonce.size != Sodium.crypto_secretbox_noncebytes()) {
                Log.e("encrypt_message", "incorrect nonce length: " + nonce.size + " (expected " + Sodium.crypto_secretbox_noncebytes() + ")")
                return null
            }

            // write auth_id
            val message = ByteArray(4 + pdata!!.size + 2)
            NukiTools.write32_auth_id(message, 0, auth_id)

            // write command_id + payload
            System.arraycopy(pdata, 0, message, 4, pdata.size)

            // write crc
            val crc = NukiTools.crc16(message, 0, message.size - 2)
            NukiTools.write16(message, message.size - 2, crc)

            // encrypt
            val encrypted = ByteArray(Sodium.crypto_secretbox_macbytes() + message.size)
            if (Sodium.crypto_secretbox_easy(encrypted, message, message.size, nonce, shared_key) != 0) {
                Log.e("encrypt_message", "crypto_secretbox_easy failed")
                return null
            }

            // assemble encrypted message
            return NukiTools.concat(nonce, NukiTools.from32_auth_id(auth_id), NukiTools.from16(encrypted.size), encrypted)
        }

        fun crc_calc_and_add(data: ByteArray?): ByteArray? {
            if (data == null || data.size < 2) {
                return null
            }
            val crc_calc = NukiTools.crc16(data, 0, data.size)
            val ret = ByteArray(data.size + 2)
            NukiTools.write16(ret, data.size, crc_calc)
            System.arraycopy(data, 0, ret, 0, data.size)
            return ret
        }

        fun crc_check_and_strip(data: ByteArray?): ByteArray? {
            if (data == null || data.size < 2) {
                return null
            }
            val crc_field = NukiTools.read16(data, data.size - 2)
            val crc_calc = NukiTools.crc16(data, 0, data.size - 2)
            val ret = ByteArray(data.size - 2)
            System.arraycopy(data, 0, ret, 0, ret.size)
            return if (crc_field != crc_calc) {
                null
            } else ret
        }

        var bluetooth_in_use = AtomicBoolean(false)
    }

    init {
        if (sodium == null) {
            // load libsodium for JNI access
            sodium = NaCl.sodium()
        }
    }
}