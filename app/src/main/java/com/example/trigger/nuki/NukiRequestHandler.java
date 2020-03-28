package com.example.trigger.nuki;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.libsodium.jni.Sodium;
import org.libsodium.jni.NaCl;

import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.trigger.NukiDoorSetup;
import com.example.trigger.MainActivity.Action;
import com.example.trigger.DoorReply.ReplyCode;
import com.example.trigger.OnTaskCompleted;
import com.example.trigger.Log;
import com.example.trigger.Settings;
import com.example.trigger.Utils;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGatt.GATT_FAILURE;
import static android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION;
import static android.bluetooth.BluetoothGatt.GATT_READ_NOT_PERMITTED;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;


public class NukiRequestHandler extends Thread {
    static final String TAG = "NukiRequestHandler";
    private final OnTaskCompleted listener;
    private final NukiDoorSetup setup;
    private final Action action;
    private Context context;
    private static Sodium sodium;

    // Client Characteristic Configuration Descriptor
    static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Pairing UUIDs
    static final UUID PAIRING_SERVICE_UUID = UUID.fromString("a92ee100-5501-11e4-916c-0800200c9a66");
    static final UUID PAIRING_GDIO_XTERISTIC_UUID = UUID.fromString("a92ee101-5501-11e4-916c-0800200c9a66");

    // Keyturner UUIDs
    static final UUID KEYTURNER_SERVICE_UUID = UUID.fromString("a92ee200-5501-11e4-916c-0800200c9a66");
    static final UUID KEYTURNER_GDIO_XTERISTIC_UUID = UUID.fromString("a92ee201-5501-11e4-916c-0800200c9a66");
    static final UUID KEYTURNER_USDIO_XTERISTIC_UUID = UUID.fromString("a92ee202-5501-11e4-916c-0800200c9a66");

    private static NukiCommand parse(byte[] data) {
        if (data == null || data.length < 2) {
            return null;
        }

        int command = NukiTools.read16(data, 0);
        switch (command) {
            case 0x0001: {
                if (data.length != 4) {
                    return null;
                }
                int command_id = NukiTools.read16(data, 2);
                return new NukiCommand.NukiRequest(command_id);
            }
            case 0x0003: {
                if (data.length != 34) {
                    return null;
                }
                byte[] public_key = new byte[32];
                System.arraycopy(data, 2, public_key, 0, public_key.length);
                return new NukiCommand.NukiPublicKey(public_key);
            }
            case 0x0004: {
                if (data.length != 34) {
                    return null;
                }
                byte[] nonce = new byte[32];
                System.arraycopy(data, 2, nonce, 0, nonce.length);
                return new NukiCommand.NukiChallenge(nonce);
            }
            case 0x0005: {
                if (data.length != 34) {
                    return null;
                }
                byte[] nonce = new byte[32];
                System.arraycopy(data, 2, nonce, 0, nonce.length);
                return new NukiCommand.NukiAuthAuthentication(nonce);
            }
            case 0x0006: {
                if (data.length != 103) {
                    return null;
                }
                byte[] authenticator = new byte[32];
                byte[] id_type = new byte[1];
                byte[] app_id = new byte[4];
                byte[] name = new byte[32];
                byte[] nonce = new byte[32];
                System.arraycopy(data, 2, authenticator, 0, authenticator.length);
                System.arraycopy(data, 2 + authenticator.length, id_type, 0, id_type.length);
                System.arraycopy(data, 2 + authenticator.length + id_type.length, app_id, 0, app_id.length);
                System.arraycopy(data, 2 + authenticator.length + id_type.length + app_id.length, name, 0, name.length);
                System.arraycopy(data, 2 + authenticator.length + id_type.length + app_id.length + name.length, nonce, 0, nonce.length);
                return new NukiCommand.NukiAuthData(authenticator, id_type[0], NukiTools.read32_app_id(app_id, 0), new String(name), nonce);
            }
            case 0x0007: {
                if (data.length != 86) {
                    return null;
                }
                byte[] authenticator = new byte[32];
                byte[] auth_id = new byte[4];
                byte[] uuid = new byte[16];
                byte[] nonce = new byte[32];
                System.arraycopy(data, 2, authenticator, 0, authenticator.length);
                System.arraycopy(data, 2 + authenticator.length, auth_id, 0, auth_id.length);
                System.arraycopy(data, 2 + authenticator.length + auth_id.length, uuid, 0, uuid.length);
                System.arraycopy(data, 2 + authenticator.length + auth_id.length + uuid.length, nonce, 0, nonce.length);
                return new NukiCommand.NukiAuthID(authenticator, NukiTools.read32_auth_id(auth_id, 0), uuid, nonce);
            }
            case 0x000C: {
                if (data.length != 21) {
                    return null;
                }

                int nuki_state = data[2];
                int lock_state = data[3];
                int trigger = data[4];
                int year = NukiTools.read16(data, 5);
                int month = data[7];
                int day = data[8];
                int hour = data[9];
                int minute = data[10];
                int second = data[11];
                String current_time = String.format(Locale.ENGLISH, "%02d-%02d-%d %02d:%02d:%02d", day, month, year, hour, minute, second);
                int time_offset = NukiTools.read_i16(data, 10);
                int critical_battery = data[14];
                // following fields are ignored here
                return new NukiCommand.NukiStates(nuki_state, lock_state, trigger, current_time, time_offset, critical_battery);
            }
            case 0x001E: {
                if (data.length != 38 && data.length != 70) {
                    return null;
                }
                byte[] authenticator = new byte[32];
                System.arraycopy(data, 2, authenticator, 0, authenticator.length);
                int auth_id = NukiTools.read32_auth_id(data, 2 + authenticator.length);
                return new NukiCommand.NukiAuthIdConfirm(authenticator, auth_id);
            }
            case 0x000E: {
                if (data.length != 3) {
                    return null;
                }
                int status = data[2];
                return new NukiCommand.NukiStatus(status);
            }
            case 0x0012: {
                if (data.length != 5) {
                    return null;
                }
                int error_code = data[2];
                int command_id = NukiTools.read16(data, 3);
                return new NukiCommand.NukiError(error_code, command_id);
            }
            default:
                return null;
        }
    }

    public NukiRequestHandler(OnTaskCompleted listener, NukiDoorSetup setup, Action action) {
        this.listener = listener;
        this.setup = setup;
        this.action = action;
        this.context = (Context) listener;

        if (sodium == null) {
            // load libsodium for JNI access
            sodium = NaCl.sodium();
        }
    }

    public static String getGattStatus(int status) {
        switch (status) {
            case GATT_SUCCESS: return "GATT_SUCCESS";
            case GATT_FAILURE: return "GATT_FAILURE";
            case GATT_INSUFFICIENT_ENCRYPTION: return "GATT_INSUFFICIENT_ENCRYPTION";
            case GATT_READ_NOT_PERMITTED: return "GATT_READ_NOT_PERMITTED";
            case 19: return "DISCONNECTED_BY_DEVICE";
            case 133: return "DEVICE_NOT_FOUND";
            default:
                return Integer.toString(status);
        }
    }

    static byte[] getSharedKey(byte[] nuki_public_key, byte[] secret_key) {
        byte[] shared_key = new byte[Sodium.crypto_core_hsalsa20_outputbytes()];
        if (true) {
            if (Sodium.crypto_box_beforenm(shared_key, nuki_public_key, secret_key) != 0) {
                Log.e(TAG, "crypto_box_beforenm failed");
                return null;
            }
        } else {
            // alternative to crypto_box_beforenm
            byte[] dhk = new byte[Sodium.crypto_scalarmult_curve25519_bytes()];
            if (Sodium.crypto_scalarmult_curve25519(dhk, secret_key, nuki_public_key) != 0) {
                Log.e(TAG, "crypto_scalarmult_curve25519 failed");
                return null;
            }
            byte[] inv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            byte[] sigma = "expand 32-byte k".getBytes();
            if (sigma.length != 16) {
                Log.e(TAG, "wrong sigma length");
                return null;
            }
            if (Sodium.crypto_core_hsalsa20(shared_key, inv, dhk, sigma) != 0) {
                Log.e(TAG, "crypto_core_hsalsa20 failed");
                return null;
            }
        }
        return shared_key;
    }

    // returns command_id + payload (without auth_id/crc fields)
    static byte[] decrypt_message(byte[] shared_key, byte[] msg) {
        int nonce_length = Sodium.crypto_secretbox_noncebytes();
        int header_length = nonce_length + 4 + 2; // nonce + auth_id + length field size

        // nonce + auth_id + length + encrypted(macbytes + auth_id + command_id + crc)
        int min_msg_length = Sodium.crypto_secretbox_noncebytes() + 4 + 2 + Sodium.crypto_secretbox_macbytes() + 8;
        if (msg == null || msg.length < min_msg_length) {
            return null;
        }

        int length = NukiTools.read16(msg, nonce_length + 4);

        if (msg.length != (header_length + length)) {
            return null;
        }

        byte[] nonce = new byte[nonce_length];
        System.arraycopy(msg, 0, nonce, 0, nonce.length);
        int auth_id = NukiTools.read32_auth_id(msg, nonce.length);

        byte[] encrypted = new byte[length];
        System.arraycopy(msg, nonce_length + 4 + 2, encrypted, 0, encrypted.length);

        byte[] decrypted = new byte[length - Sodium.crypto_secretbox_macbytes()];
        if (Sodium.crypto_secretbox_open_easy(decrypted, encrypted, encrypted.length, nonce, shared_key) != 0) {
            Log.e("decrypt_message", "crypto_secretbox_easy failed");
            return null;
        }

        if (decrypted.length < 6) {
            return null;
        }

        // check auth_id
        if (auth_id != NukiTools.read32_auth_id(decrypted, 0)) {
            Log.e("decrypt_message", "auth_id mismatch");
            return null;
        }

        // check crc
        int crc_calc = NukiTools.crc16(decrypted, 0, decrypted.length - 2);
        int crc_read = NukiTools.read16(decrypted, decrypted.length - 2);
        if (crc_calc != crc_read) {
            Log.e("decrypt_message", "crc mismatch");
            return null;
        }

        // strip auth_id and crc
        byte[] ret = new byte[decrypted.length - 6];
        System.arraycopy(decrypted, 4, ret, 0, ret.length);

        // return command_id + payload
        return ret;
    }

    // data is expected to be command_id + payload
    static byte[] encrypt_message(byte[] shared_key, int auth_id, byte[] pdata, byte[] nonce) {
        // nonce is provide only for testing purposes!
        if (nonce == null) {
            nonce = new byte[Sodium.crypto_secretbox_noncebytes()];
            Sodium.randombytes(nonce, nonce.length);
        }

        if (nonce.length != Sodium.crypto_secretbox_noncebytes()) {
            Log.e("encrypt_message", "incorrect nonce length: " + nonce.length + " (expected " + Sodium.crypto_secretbox_noncebytes() + ")");
            return null;
        }

        // write auth_id
        byte[] message = new byte[4 + pdata.length + 2];
        NukiTools.write32_auth_id(message, 0, auth_id);

        // write command_id + payload
        System.arraycopy(pdata, 0, message, 4, pdata.length);

        // write crc
        int crc = NukiTools.crc16(message, 0, message.length - 2);
        NukiTools.write16(message, message.length - 2, crc);

        // encrypt
        byte[] encrypted = new byte[Sodium.crypto_secretbox_macbytes() + message.length];
        if (Sodium.crypto_secretbox_easy(encrypted, message, message.length, nonce, shared_key) != 0) {
            Log.e("encrypt_message", "crypto_secretbox_easy failed");
            return null;
        }

        // assemble encrypted message
        return NukiTools.concat(nonce, NukiTools.from32_auth_id(auth_id), NukiTools.from16(encrypted.length), encrypted);
    }

    static byte[] crc_calc_and_add(byte[] data) {
        if (data == null || data.length < 2) {
            return null;
        }
        int crc_calc = NukiTools.crc16(data, 0, data.length);
        byte[] ret = new byte[data.length + 2];
        NukiTools.write16(ret, data.length, crc_calc);
        System.arraycopy(data, 0, ret, 0, data.length);
        return ret;
    }

    static byte[] crc_check_and_strip(byte[] data) {
        if (data == null || data.length < 2) {
            return null;
        }

        int crc_field = NukiTools.read16(data, data.length - 2);
        int crc_calc = NukiTools.crc16(data, 0, data.length - 2);

        byte[] ret = new byte[data.length - 2];
        System.arraycopy(data, 0, ret, 0, ret.length);

        if (crc_field != crc_calc) {
            return null;
        }

        return ret;
    }

    static abstract class NukiCallback extends BluetoothGattCallback {
        private static final String TAG = "NukiCallback";
        protected final OnTaskCompleted listener;
        private final UUID service_uuid;
        private final UUID characteristic_uuid;
        protected final int setup_id;

        NukiCallback(int setup_id, OnTaskCompleted listener, UUID service_uuid, UUID characteristic_uuid) {
            this.setup_id = setup_id;
            this.listener = listener;
            this.service_uuid = service_uuid;
            this.characteristic_uuid = characteristic_uuid;
        }

        /*
         * This is mostly called to end the connection quickly
         * instead of waiting for the other side to connection
        */
        protected void closeConnection(BluetoothGatt gatt) {
            gatt.close();
            bluetooth_in_use.set(false);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //Log.i(TAG, "status: " + getGattStatus(status) + ", newState: " + newState);

            if (status == GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        closeConnection(gatt);
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                    case BluetoothProfile.STATE_DISCONNECTING:
                        break;
                }
            } else {
                Log.d(TAG, "Connection error: " + getGattStatus(status));
                closeConnection(gatt);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(this.service_uuid);
                if (service == null) {
                    Log.w(TAG, "service not found: " + this.service_uuid);
                    return;
                }

                BluetoothGattCharacteristic characteristic = service.getCharacteristic(this.characteristic_uuid);
                if (characteristic == null) {
                    Log.w(TAG, "characteristic not found: " + this.characteristic_uuid);
                    return;
                }

                gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID);
                if (descriptor == null) {
                    Log.w(TAG, "descriptor not found: " + CCC_DESCRIPTOR_UUID);
                    return;
                }
               
                //Log.i(TAG, "characteristic properties: " + NukiTools.getProperties(characteristic));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                boolean ok = gatt.writeDescriptor(descriptor);
                if (!ok) {
                    Log.e(TAG, "descriptor write failed");
                    closeConnection(gatt);
                }
            } else {
                Log.e(TAG, "client not found: " + getGattStatus(status));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //Log.i(TAG, "uiid: " + descriptor.getUuid() + ": " + Utils.byteArrayToHexString(descriptor.getValue()));
            if (status == GATT_SUCCESS) {
                onConnected(gatt, descriptor.getCharacteristic());
            } else {
                Log.e(TAG, "failed to write to client: " + getGattStatus(status));
            }
        }

        public abstract void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
    }

    static class ReadLockStateCallback extends NukiCallback {
        static final String TAG = "ReadLockStateCallback";
        int auth_id;
        byte[] shared_key;
        byte[] data;

        ReadLockStateCallback(int setup_id, OnTaskCompleted listener, NukiDoorSetup setup) {
            super(setup_id, listener, KEYTURNER_SERVICE_UUID, KEYTURNER_USDIO_XTERISTIC_UUID);
            this.shared_key = Utils.hexStringToByteArray(setup.shared_key);
            this.auth_id = setup.auth_id;
        }

        public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            NukiCommand.NukiRequest nr = new NukiCommand.NukiRequest(0x0c);
            byte[] request = encrypt_message(this.shared_key, this.auth_id, nr.generate(), null);
            characteristic.setValue(request);
            boolean ok = gatt.writeCharacteristic(characteristic);
            if (!ok) {
                Log.e(TAG, "initial writeCharacteristic failed");
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

            byte[] message = decrypt_message(shared_key, data);
            NukiCommand m = parse(message);

            if (m == null) {
                return;
            } else {
                data = null;
            }

            if (m instanceof NukiCommand.NukiStates) {
                NukiCommand.NukiStates ns = (NukiCommand.NukiStates) m;

                String extra = "";
                if (ns.battery_critical == 0x01) {
                    extra = " (Battery Critical!)";
                }
                listener.onTaskResult(setup_id, ReplyCode.SUCCESS, NukiTools.getLockState(ns.lock_state) + extra);

                // do not wait until the Nuki closes the connection
                closeConnection(gatt);
            } else if (m instanceof NukiCommand.NukiError) {
                NukiCommand.NukiError ns = (NukiCommand.NukiError) m;
                this.listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, ns.asString());
                closeConnection(gatt);
            } else {
                Log.e(TAG, "Unhandled command.");
                closeConnection(gatt);
            }
        }
    }

    static class LockActionCallback extends NukiCallback {
        static final String TAG = "LockActionCallback";
        int auth_id;
        int app_id;
        int lock_action;
        byte[] shared_key;
        byte[] data;

        LockActionCallback(int setup_id, OnTaskCompleted listener, NukiDoorSetup setup, int lock_action) {
            super(setup_id, listener, KEYTURNER_SERVICE_UUID, KEYTURNER_USDIO_XTERISTIC_UUID);
            this.shared_key = Utils.hexStringToByteArray(setup.shared_key);
            this.auth_id = setup.auth_id;
            this.app_id = setup.app_id;
            this.lock_action = lock_action;
        }

        public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            NukiCommand.NukiRequest nr = new NukiCommand.NukiRequest(0x04);
            byte[] request = encrypt_message(this.shared_key, this.auth_id, nr.generate(), null);
            characteristic.setValue(request);
            boolean ok = gatt.writeCharacteristic(characteristic);
            if (!ok) {
                Log.e(TAG, "initial writeCharacteristic failed");
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

            byte[] message = decrypt_message(shared_key, data);
            NukiCommand command = parse(message);

            if (command == null) {
                return;
            } else {
                data = null;
            }

            if (command instanceof NukiCommand.NukiChallenge) {
                NukiCommand.NukiChallenge nc = (NukiCommand.NukiChallenge) command;
                NukiCommand.NukiLockAction nla = new NukiCommand.NukiLockAction(this.lock_action, this.app_id, 0x00, nc.nonce);
                byte[] response = encrypt_message(this.shared_key, this.auth_id, nla.generate(), null);

                characteristic.setValue(response);
                boolean ok = gatt.writeCharacteristic(characteristic);
                if (!ok) {
                    Log.e(TAG, "writeCharacteristic failed for NukiLockAction");
                    closeConnection(gatt);
                }
            } else if (command instanceof  NukiCommand.NukiStatus) {
                NukiCommand.NukiStatus ns = (NukiCommand.NukiStatus) command;
                if (ns.status == NukiCommand.NukiStatus.STATUS_COMPLETE) {
                    // do not wait until the Nuki closes the connection
                    closeConnection(gatt);
                }
            } else if (command instanceof NukiCommand.NukiStates) {
                NukiCommand.NukiStates ns = (NukiCommand.NukiStates) command;
                String extra = "";
                if (ns.battery_critical == 0x01) {
                    extra = " (Battery Critical!)";
                }
                listener.onTaskResult(setup_id, ReplyCode.SUCCESS, NukiTools.getLockState(ns.lock_state) + extra);
            } else if (command instanceof NukiCommand.NukiError) {
                NukiCommand.NukiError ne = (NukiCommand.NukiError) command;
                this.listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, ne.asString());
                closeConnection(gatt);
            } else {
                Log.e(TAG, "Unhandled command");
            }
        }
    }

    static class PairingCallback extends NukiCallback {
        static final String TAG = "PairingCallback";
        NukiDoorSetup setup;
        byte[] data;

        byte[] secret_key;
        byte[] public_key;
        byte[] nuki_public_key;
        byte[] shared_key;
        int auth_id; // unique identifier of the nuki smartlock or bridge
        int app_id; // unique identifier of the app

        final int id_type = 1; // 0 (app), 1 (bridge), 2 (FOB), 3 (Keypad)
        final String user_name;
        byte[] uuid;
        byte[] own_nonce = new byte[0];

        int challenge = 0;

        PairingCallback(int setup_id, OnTaskCompleted listener, NukiDoorSetup setup) {
            super(setup_id, listener, PAIRING_SERVICE_UUID, PAIRING_GDIO_XTERISTIC_UUID);
            this.setup = setup;
            this.user_name = setup.user_name;
        }

        public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (!this.setup.shared_key.isEmpty()) {
                this.listener.onTaskResult(setup_id, ReplyCode.LOCAL_ERROR, "Already paired to some device!");
                closeConnection(gatt);
                return;
            }

            NukiCommand.NukiRequest nr = new NukiCommand.NukiRequest(0x03);
            characteristic.setValue(crc_calc_and_add(nr.generate()));
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

            NukiCommand m = parse(crc_check_and_strip(data));
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
                characteristic.setValue(crc_calc_and_add(pk.generate()));
                boolean ok = gatt.writeCharacteristic(characteristic);
                if (!ok) {
                    Log.e(TAG, "writeCharacteristic failed for NukiPublicKey");
                    closeConnection(gatt);
                    return;
                }

                shared_key = getSharedKey(nuki_public_key, secret_key);
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
                    characteristic.setValue(crc_calc_and_add(naa.generate()));
                    boolean ok = gatt.writeCharacteristic(characteristic);
                    if (!ok) {
                        Log.e(TAG, "writeCharacteristic failed for NukiAuthAuthentication");
                        closeConnection(gatt);
                        return;
                    }
                } else {
                    own_nonce = new byte[32];
                    Sodium.randombytes(own_nonce, own_nonce.length);

                    // TODO: move into NukiAuthData class
                    this.app_id = (new Random()).nextInt(250);
                    byte[] valueR = NukiTools.concat(NukiTools.from8(id_type), NukiTools.from32_app_id(app_id), NukiTools.nameToBytes(user_name, 32), own_nonce, nc.nonce);
                    byte[] authenticator = new byte[Sodium.crypto_auth_hmacsha256_bytes()];
                    if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR.length, shared_key) != 0) {
                        Log.e(TAG, "crypto_auth_hmacsha256 failed");
                        closeConnection(gatt);
                        return;
                    }

                    NukiCommand.NukiAuthData nad = new NukiCommand.NukiAuthData(authenticator, id_type, app_id, user_name, own_nonce);
                    characteristic.setValue(crc_calc_and_add(nad.generate()));
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

                byte[] shared_key = getSharedKey(nuki_public_key, secret_key);
                byte[] valueR = NukiTools.concat(NukiTools.from32_auth_id(auth_id), nai.nonce);
                byte[] authenticator = new byte[Sodium.crypto_auth_hmacsha256_bytes()];
                if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR.length, shared_key) != 0) {
                    com.example.trigger.Log.e(TAG, "crypto_auth_hmacsha256 failed");
                    closeConnection(gatt);
                    return;
                }

                NukiCommand.NukiAuthIdConfirm naidc = new NukiCommand.NukiAuthIdConfirm(authenticator, auth_id);
                characteristic.setValue(crc_calc_and_add(naidc.generate()));
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

    String getAddress(BluetoothAdapter adapter, String device_name) {
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

        String address = "";
        for (BluetoothDevice device : pairedDevices) {
            if ((device.getName() != null && device.getName().equals(device_name))
                    || device.getAddress().toUpperCase().equals(device_name.toUpperCase())) {
                address = device.getAddress();
            }
        }

        if (address.isEmpty()) {
            listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Device not paired yet.");
            return null;
        }

        return address;
    }

    private static AtomicBoolean bluetooth_in_use = new AtomicBoolean(false);

    public void run() {
        if (bluetooth_in_use.get()) {
            Log.w(TAG, "Bluetooth busy => abort action");
            return;
        }

        if (!((Context) this.listener).getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            listener.onTaskResult(setup.getId(), ReplyCode.DISABLED, "Bluetooth Low Energy is not supported.");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            // request to enable
            this.listener.onTaskResult(setup.getId(), ReplyCode.DISABLED, "Bluetooth is disabled.");
            return;
        }

        if (setup.device_name.isEmpty()) {
            listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "No device name set.");
            return;
        }

        if (setup.user_name.isEmpty()) {
            listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "No user name set.");
            return;
        }

        String address = getAddress(adapter, setup.device_name);
        if (address == null) {
            listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "No Device found.");
            return;
        }

        BluetoothDevice device = adapter.getRemoteDevice(address);
        if (device == null) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Device not found.");
            return;
        }

        if (!bluetooth_in_use.compareAndSet(false, true)) {
            // setting the variable failed
            return;
        }

        BluetoothGattCallback callback;
        if (action != Action.fetch_state && (setup.shared_key == null || setup.shared_key.length() != 64)) {
            // initiate pairing
            callback = new PairingCallback(setup.getId(), this.listener, setup);
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Start Pairing.");
        } else switch (action) {
            case open_door:
                callback = new LockActionCallback(setup.getId(), this.listener, setup, 0x01);
                break;
            case ring_door:
                this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Bell not supported.");
                return;
            case close_door:
                callback = new LockActionCallback(setup.getId(), this.listener, setup, 0x02);
                break;
            default:
            case fetch_state:
                callback = new ReadLockStateCallback(setup.getId(), this.listener, setup);
                break;
        }

        BluetoothGatt gatt;

        if (Build.VERSION.SDK_INT >= 23) {
            gatt = device.connectGatt(this.context, false, callback, TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(this.context, false, callback);
        }

        if (gatt == null) {
            // failed to start connection
            bluetooth_in_use.set(false);
        }
    }
}
