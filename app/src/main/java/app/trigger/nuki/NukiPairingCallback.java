package app.trigger.nuki;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import org.libsodium.jni.Sodium;
import org.libsodium.jni.NaCl;

import app.trigger.DoorReply.ReplyCode;
import app.trigger.Log;
import app.trigger.NukiDoorSetup;
import app.trigger.OnTaskCompleted;
import app.trigger.Settings;
import app.trigger.Utils;

import java.util.Random;

class NukiPairingCallback extends NukiCallback {
    static final String TAG = "PairingCallback";
    NukiDoorSetup setup;
    byte[] data;

    byte[] secret_key;
    byte[] public_key;
    byte[] nuki_public_key;
    byte[] shared_key;
    long auth_id; // unique identifier of the nuki smartlock or bridge (unsigned int)
    long app_id; // unique identifier of the app (unsigned int)

    final int id_type = 0; // 0 (app), 1 (bridge), 2 (FOB), 3 (Keypad)
    final String user_name;
    byte[] uuid;
    byte[] own_nonce = new byte[0];

    int challenge = 0;

    NukiPairingCallback(int setup_id, OnTaskCompleted listener, NukiDoorSetup setup) {
        super(setup_id, listener, PAIRING_SERVICE_UUID, PAIRING_GDIO_XTERISTIC_UUID);
        this.setup = setup;
        this.user_name = setup.user_name;
        //this.auth_id = setup.auth_id; // we get this from the nuki
        this.app_id = setup.app_id;
    }

    public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (!this.setup.shared_key.isEmpty()) {
            this.listener.onTaskResult(setup_id, ReplyCode.LOCAL_ERROR, "Already paired to some device!");
            closeConnection(gatt);
            return;
        }

        NukiCommand.NukiRequest nr = new NukiCommand.NukiRequest(0x03);
        characteristic.setValue(NukiRequestHandler.crc_calc_and_add(nr.generate()));
        boolean ok = gatt.writeCharacteristic(characteristic);
        if (!ok) {
            Log.e(TAG, "writeCharacteristic failed for NukiRequest");
            closeConnection(gatt);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        //Log.i(TAG, "uiid: " + characteristic.getUuid() + ": " + Utils.byteArrayToHexString(characteristic.getValue()));
        if (data == null) {
            data = characteristic.getValue();
        } else {
            data = NukiTools.concat(data, characteristic.getValue());
        }

        NukiCommand m = NukiRequestHandler.parse(NukiRequestHandler.crc_check_and_strip(data));
        if (m == null) {
            return;
        } else {
            data = null;
        }

        if (m instanceof NukiCommand.NukiError) {
            NukiCommand.NukiError ne = (NukiCommand.NukiError) m;
            this.listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, ne.asString());
            closeConnection(gatt);
        } else if (m instanceof NukiCommand.NukiPublicKey) {
            NukiCommand.NukiPublicKey npk = (NukiCommand.NukiPublicKey) m;
            nuki_public_key = npk.public_key;

            public_key = new byte[Sodium.crypto_box_publickeybytes()];
            secret_key = new byte[Sodium.crypto_box_secretkeybytes()];
            Sodium.crypto_box_keypair(public_key, secret_key);

            // send own public key
            NukiCommand.NukiPublicKey pk = new NukiCommand.NukiPublicKey(public_key);
            characteristic.setValue(NukiRequestHandler.crc_calc_and_add(pk.generate()));
            boolean ok = gatt.writeCharacteristic(characteristic);
            if (!ok) {
                Log.e(TAG, "writeCharacteristic failed for NukiPublicKey");
                closeConnection(gatt);
                return;
            }

            shared_key = NukiRequestHandler.getSharedKey(nuki_public_key, secret_key);
        } else if (m instanceof NukiCommand.NukiChallenge) {
            NukiCommand.NukiChallenge nc = (NukiCommand.NukiChallenge) m;
            if (challenge == 0) {
                challenge = 1;
                //Log.i(TAG, "NukiCommand: NukiChallenge: nonce: " + Utils.byteArrayToHexString(nuki_nonce));

                byte[] valueR = NukiTools.concat(public_key, nuki_public_key, nc.nonce);
                byte[] authenticator = new byte[Sodium.crypto_auth_hmacsha256_bytes()];
                if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR.length, shared_key) != 0) {
                    Log.e(TAG, "crypto_auth_hmacsha256 failed");
                    closeConnection(gatt);
                    return;
                }

                // send authenticator
                NukiCommand.NukiAuthAuthentication naa = new NukiCommand.NukiAuthAuthentication(authenticator);
                characteristic.setValue(NukiRequestHandler.crc_calc_and_add(naa.generate()));
                boolean ok = gatt.writeCharacteristic(characteristic);
                if (!ok) {
                    Log.e(TAG, "writeCharacteristic failed for NukiAuthAuthentication");
                    closeConnection(gatt);
                    return;
                }
            } else {
                own_nonce = new byte[32];
                Sodium.randombytes(own_nonce, own_nonce.length);

                if (this.app_id == 0) {
                    // get random unsigned int
                    this.app_id = (new Random()).nextLong() & 0xffffffffL;
                }
                byte[] valueR = NukiTools.concat(NukiTools.from8(id_type), NukiTools.from32_app_id(app_id), NukiTools.nameToBytes(user_name, 32), own_nonce, nc.nonce);
                byte[] authenticator = new byte[Sodium.crypto_auth_hmacsha256_bytes()];
                if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR.length, shared_key) != 0) {
                    Log.e(TAG, "crypto_auth_hmacsha256 failed");
                    closeConnection(gatt);
                    return;
                }

                NukiCommand.NukiAuthData nad = new NukiCommand.NukiAuthData(authenticator, id_type, app_id, user_name, own_nonce);
                characteristic.setValue(NukiRequestHandler.crc_calc_and_add(nad.generate()));
                boolean ok = gatt.writeCharacteristic(characteristic);
                if (!ok) {
                    Log.e(TAG, "writeCharacteristic failed for NukiAuthData");
                    closeConnection(gatt);
                    return;
                }
            }
        } else if (m instanceof NukiCommand.NukiAuthID) {
            NukiCommand.NukiAuthID nai = (NukiCommand.NukiAuthID) m;
            Log.d(TAG, "NukiAuthID: auth_id: " + nai.auth_id);
            auth_id = nai.auth_id;
            uuid = nai.uuid;

            //check authenticator
            if (!nai.verify(shared_key, own_nonce)) {
                Log.e(TAG, "authenticator invalid!");
                closeConnection(gatt);
                return;
            }

            byte[] shared_key = NukiRequestHandler.getSharedKey(nuki_public_key, secret_key);
            byte[] valueR = NukiTools.concat(NukiTools.from32_auth_id(auth_id), nai.nonce);
            byte[] authenticator = new byte[Sodium.crypto_auth_hmacsha256_bytes()];
            if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR.length, shared_key) != 0) {
                app.trigger.Log.e(TAG, "crypto_auth_hmacsha256 failed");
                closeConnection(gatt);
                return;
            }

            NukiCommand.NukiAuthIdConfirm naidc = new NukiCommand.NukiAuthIdConfirm(authenticator, auth_id);
            characteristic.setValue(NukiRequestHandler.crc_calc_and_add(naidc.generate()));
            boolean ok = gatt.writeCharacteristic(characteristic);
            if (!ok) {
                Log.e(TAG, "writeCharacteristic failed for NukiAuthIdConfirm");
                closeConnection(gatt);
                return;
            }
        } else if (m instanceof NukiCommand.NukiStatus) {
            NukiCommand.NukiStatus ns = (NukiCommand.NukiStatus) m;
            if (ns.status != 0) {
                this.listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, "Pairing failed.");
                closeConnection(gatt);
                return;
            }

            if (shared_key != null && shared_key.length == 32
                && uuid != null && uuid.length == 16) {
                setup.auth_id = this.auth_id;
                setup.app_id = this.app_id;
                setup.shared_key = Utils.byteArrayToHexString(shared_key);
                Settings.addSetup(setup);
                this.listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, "Pairing complete.");
                closeConnection(gatt);
            } else {
                this.listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, "Failed to set authorization data.");
                closeConnection(gatt);
            }
        } else {
            Log.e(TAG, "Unhandled command");
            closeConnection(gatt);
        }
    }
}
