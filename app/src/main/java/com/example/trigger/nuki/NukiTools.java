package com.example.trigger.nuki;

import android.bluetooth.BluetoothGattCharacteristic;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_BROADCAST;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static java.lang.Math.min;


public class NukiTools {

    static String getCommand(int command) {
        switch (command) {
            case 0x0001: return "Request Data";
            case 0x0003: return "Public Key";
            case 0x0004: return "Challenge";
            case 0x0005: return "Authorization Authenticator";
            case 0x0006: return "Authorization Data";
            case 0x0007: return "Authorization-ID";
            case 0x0008: return "Remove User Authorization";
            case 0x0009: return "Request Authorization Entries";
            case 0x000A: return "Authorization Entry";
            case 0x000B: return "Authorization Data (Invite)";
            case 0x000C: return "Nuki States";
            case 0x000D: return "Lock Action";
            case 0x000E: return "Status";
            case 0x000F: return "Most Recent Command";
            case 0x0010: return "Openings Closings Summary";
            case 0x0011: return "Battery Report";
            case 0x0012: return "Error Report";
            case 0x0013: return "Set Config";
            case 0x0014: return "Request Config";
            case 0x0015: return "Config";
            case 0x0019: return "Set Security PIN";
            case 0x001A: return "Request Calibration";
            case 0x001D: return "Request Reboot";
            case 0x001E: return "Authorization-ID Confirmation";
            case 0x001F: return "Authorization-ID (Invite)";
            case 0x0020: return "Verify Security PIN";
            case 0x0021: return "Update Time";
            case 0x0025: return "Update User Authorization";
            case 0x0027: return "Authorization Entry Count";
            case 0x0030: return "Request Disconnect";
            case 0x0031: return "Request Log Entries";
            case 0x0032: return "Log Entry";
            case 0x0033: return "Log Entry Count";
            case 0x0034: return "Enable Logging";
            case 0x0035: return "Set Advanced Config";
            case 0x0036: return "Request Advanced Config";
            case 0x0037: return "Advanced Config";
            case 0x0039: return "Add Time Control Entry";
            case 0x003A: return "Time Control Entry ID";
            case 0x003B: return "Remove Time Control Entry";
            case 0x003C: return "Request Time Control Entries";
            case 0x003D: return "Time Control Entry Count";
            case 0x003E: return "Time Control Entry";
            case 0x003F: return "Update Time Control Entry";
            default: return "Unknown";
        }
    }

    static String getError(int errorCode) {
        switch (errorCode) {
            case 0x10: return "Device is not in paring mode.";
            case 0x11: return "P_ERROR_BAD_AUTHENTICATOR";
            case 0x12: return "P_ERROR_BAD_PARAMETER";
            case 0x13: return "P_ERROR_MAX_USER";
            case 0x20: return "K_ERROR_NOT_AUTHORIZED";
            case 0x21: return "K_ERROR_BAD_PIN";
            case 0x22: return "K_ERROR_BAD_NONCE";
            case 0x23: return "K_ERROR_BAD_PARAMETER";
            case 0x24: return "K_ERROR_INVALID_AUTH_ID";
            case 0x25: return "K_ERROR_DISABLED";
            case 0x26: return "K_ERROR_REMOTE_NOT_ALLOWED";
            case 0x27: return "K_ERROR_TIME_NOT_ALLOWED";
            case 0x28: return "K_ERROR_TOO_MANY_PIN_ATTEMPTS";
            case 0x29: return "K_ERROR_TOO_MANY_ENTRIES";
            case 0x2A: return "K_ERROR_CODE_ALREADY_EXISTS";
            case 0x2B: return "K_ERROR_CODE_INVALID";
            case 0x2C: return "K_ERROR_CODE_INVALID_TIMEOUT_1";
            case 0x2D: return "K_ERROR_CODE_INVALID_TIMEOUT_2";
            case 0x2E: return "K_ERROR_CODE_INVALID_TIMEOUT_3";
            case 0x40: return "K_ERROR_AUTO_UNLOCK_TOO_RECENT";
            case 0x41: return "K_ERROR_POSITION_UNKNOWN";
            case 0x42: return "K_ERROR_MOTOR_BLOCKED";
            case 0x43: return "K_ERROR_CLUTCH_FAILURE";
            case 0x44: return "K_ERROR_MOTOR_TIMEOUT";
            case 0x45: return "K_ERROR_BUSY";
            case 0x46: return "K_ERROR_CANCELED";
            case 0x47: return "K_ERROR_NOT_CALIBRATED";
            case 0x48: return "K_ERROR_MOTOR_POSITION_LIMIT";
            case 0x49: return "K_ERROR_MOTOR_LOW_VOLTAGE";
            case 0x4A: return "K_ERROR_MOTOR_POWER_FAILURE";
            case 0x4B: return "K_ERROR_CLUTCH_POWER_FAILURE";
            case 0x4C: return "K_ERROR_VOLTAGE_TOO_LOW";
            case 0x4D: return "K_ERROR_FIRMWARE_UPDATE_NEEDED";
            case 0xFD: return "ERROR_BAD_CRC";
            case 0xFE: return "ERROR_BAD_LENGTH";
            case 0xFF: return "ERROR_UNKNOWN";
            default: return "Unknown";
        }
    }

    static String getNukiState(int value) {
        switch (value) {
            case 0x00:
                return "Uninitialized";
            case 0x01:
                return "Pairing Mode";
            case 0x02:
                return "Door Mode";
            case 0x04:
                return "Maintenance Mode";
            default:
                return "Unknown";
        }
    }

    static String getLockState(int value) {
        switch (value) {
            case 0x00:
                return "uncalibrated";
            case 0x01:
                return "locked";
            case 0x02:
                return "unlocking";
            case 0x03:
                return "unlocked";
            case 0x04:
                return "locking";
            case 0x05:
                return "unlatched";
            case 0x06:
                return "unlocked(lock ‘n’ go active)";
            case 0x07:
                return "unlatching";
            case 0xFC:
                return "calibration";
            case 0xFD:
                return "boot run";
            case 0xFE:
                return "motor blocked";
            case 0xFF:
                return "undefined";
            default:
                return "unknown";
        }
    }

    // The trigger, that caused the state change of
    // the unlock mechanism within the Smart Lock
    static String getLockTrigger(int trigger) {
        switch (trigger) {
            case 0x00:
                // via bluetooth command
                return "system";
            case 0x01:
                // by using a key from outside the door
                // by rotating the wheel on the inside
                return "manual";
            case 0x02:
                // by pressing the Smart Locks button
                return "button";
            case 0x03:
                // Executed automatically (e.g. at a specific time)
                // by the Smart Lock
                return "automatic";
            case 0x06:
                // Auto relock of the Smart Lock
                return "auto lock";
            default:
                return "unknown";
        }
    }

    static String getBatteryState(int state) {
        switch (state) {
            case 0x00:
                return "OK";
            case 0x01:
                return "Critical";
            default:
                return "Unknown";
        }
    }

    static byte[] slice(byte[] data, int offset, int length) {
        if (length < 0) {
            length = data.length - offset;
        }
        byte[] ret = new byte[length];
        System.arraycopy(data, offset, ret, 0, ret.length);
        return ret;
    }

    static byte[] from8(int value) {
        return new byte[]{(byte) (value & 0xff)};
    }

    static byte[] from16(int value) {
        byte[] v = new byte[2];
        write16(v, 0, value);
        return v;
    }

    static byte[] from32_auth_id(long value) {
        byte[] v = new byte[4];
        write32_auth_id(v, 0, value);
        return v;
    }

    static byte[] from32_app_id(long value) {
        byte[] v = new byte[4];
        write32_app_id(v, 0, value);
        return v;
    }

    static int read_i16(byte[] buffer, int offset) {
        return ((buffer[offset] & 0x80) == 0 ? 1 : -1) * (buffer[offset] & 0x7f)
                + ((buffer[offset + 1] & 0xff) << 8);
    }

    static int read16(byte[] buffer, int offset) {
        return (buffer[offset] & 0xff)
                + ((buffer[offset + 1] & 0xff) << 8);
    }

    static void write16(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xff);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xff);
    }

    static long read32_app_id(byte[] data, int offset) {
        return (data[offset + 3] & 0xff)
                + ((data[offset + 2] & 0xff) << 8)
                + ((data[offset + 1] & 0xff) << 16)
                + ((data[offset + 0] & 0xff) << 24);
    }

    static void write32_app_id(byte[] data, int offset, long value) {
        data[offset + 0] = (byte) ((value >> 24) & 0xff);
        data[offset + 1] = (byte) ((value >> 16) & 0xff);
        data[offset + 2] = (byte) ((value >> 8) & 0xff);
        data[offset + 3] = (byte) (value & 0xff);
    }

    static long read32_auth_id(byte[] data, int offset) {
        return (data[offset + 0] & 0xff)
                + ((data[offset + 1] & 0xff) << 8)
                + ((data[offset + 2] & 0xff) << 16)
                + ((data[offset + 3] & 0xff) << 24);
    }

    static void write32_auth_id(byte[] data, int offset, long value) {
        data[offset + 3] = (byte) ((value >> 24) & 0xff);
        data[offset + 2] = (byte) ((value >> 16) & 0xff);
        data[offset + 1] = (byte) ((value >> 8) & 0xff);
        data[offset + 0] = (byte) (value & 0xff);
    }

    static byte[] nameToBytes(String name, int length) {
        byte[] ret = new byte[length];
        byte[] bytes = name.getBytes();
        System.arraycopy(bytes, 0, ret, 0, min(bytes.length, ret.length));
        return ret;
    }

    static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (int i = 0; i < arrays.length; i += 1) {
            length += arrays[i].length;
        }
        byte[] data = new byte[length];
        int offset = 0;
        for (int i = 0; i < arrays.length; i += 1) {
            System.arraycopy(arrays[i], 0, data, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return data;
    }

    static int crc16(final byte[] data, int offset, int count) {
        int crc = 0xFFFF;          // initial value
        int polynomial = 0x1021;   // 0001 0000 0010 0001  (0, 5, 12)

        for (int j = offset; j < count; j++){
            for (int i = 0; i < 8; i++) {
                boolean bit = ((data[j] >> (7 - i) & 1) == 1);
                boolean c15 = ((crc >> 15 & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) crc ^= polynomial;
            }
        }

        return crc & 0xffff;
    }

    static String getProperties(BluetoothGattCharacteristic c) {
        int p = c.getProperties();
        String ret = "";
        if ((p & PROPERTY_READ) != 0) {
            ret += "READ ";
        }
        if ((p & PROPERTY_WRITE) != 0) {
            ret += "WRITE ";
        }
        if ((p & PROPERTY_BROADCAST) != 0) {
            ret += "BROADCAST ";
        }
        if ((p & PROPERTY_EXTENDED_PROPS) != 0) {
            ret += "EXT ";
        }
        if ((p & PROPERTY_INDICATE) != 0) {
            ret += "INDICATE ";
        }
        if ((p & PROPERTY_NOTIFY) != 0) {
            ret += "NOTIFIY ";
        }
        if ((p & PROPERTY_SIGNED_WRITE) != 0) {
            ret += "SIGNED_WRITE ";
        }
        if ((p & PROPERTY_WRITE_NO_RESPONSE) != 0) {
            ret += "WRITE_NO_RESPONSE ";
        }
        return ret;
    }
}
