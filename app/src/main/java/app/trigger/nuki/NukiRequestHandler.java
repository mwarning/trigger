package app.trigger.nuki;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import app.trigger.NukiDoorSetup;
import app.trigger.MainActivity.Action;
import app.trigger.DoorReply.ReplyCode;
import app.trigger.OnTaskCompleted;
import app.trigger.Log;
import app.trigger.Utils;

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

    static NukiCommand parse(byte[] data) {
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
                long auth_id = NukiTools.read32_auth_id(data, 2 + authenticator.length);
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
            case 8: return "CONNECTION_TIMEOUT";
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
        long auth_id = NukiTools.read32_auth_id(msg, nonce.length);

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
    static byte[] encrypt_message(byte[] shared_key, long auth_id, byte[] pdata, byte[] nonce) {
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

    static AtomicBoolean bluetooth_in_use = new AtomicBoolean(false);

    public void run() {
        if (bluetooth_in_use.get()) {
            Log.w(TAG, "Bluetooth busy => abort action");
            if (action != Action.fetch_state) {
                listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Bluetooth device is busy.");
            }
            return;
        }

        if (!((Context) this.listener).getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            listener.onTaskResult(setup.getId(), ReplyCode.DISABLED, "Bluetooth Low Energy is not supported.");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
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
            listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "No device found.");
            return;
        }

        BluetoothDevice device = adapter.getRemoteDevice(address);
        if (device == null) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Device not found.");
            return;
        }

        if (Utils.isEmpty(setup.shared_key) && action == Action.fetch_state) {
            // ignore query for door state - not paired yet
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "");
            return;
        }

        if (!bluetooth_in_use.compareAndSet(false, true)) {
            // setting the variable failed
            return;
        }

        NukiCallback callback;
        if (Utils.isEmpty(setup.shared_key)) {
            // initiate paring
            callback = new NukiPairingCallback(setup.getId(), this.listener, setup);
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Start Pairing.");
        } else switch (action) {
            case open_door:
                callback = new NukiLockActionCallback(setup.getId(), this.listener, setup, 0x01 /*unlock*/);
                break;
            case ring_door:
                this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Bell not supported.");
                return;
            case close_door:
                callback = new NukiLockActionCallback(setup.getId(), this.listener, setup, 0x02 /*lock*/);
                break;
            default:
            case fetch_state:
                callback = new NukiReadLockStateCallback(setup.getId(), this.listener, setup);
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
