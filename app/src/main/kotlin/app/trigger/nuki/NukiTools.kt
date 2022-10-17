package app.trigger.nuki

import android.bluetooth.BluetoothGattCharacteristic

object NukiTools {
    fun getCommand(command: Int): String {
        return when (command) {
            0x0001 -> "Request Data"
            0x0003 -> "Public Key"
            0x0004 -> "Challenge"
            0x0005 -> "Authorization Authenticator"
            0x0006 -> "Authorization Data"
            0x0007 -> "Authorization-ID"
            0x0008 -> "Remove User Authorization"
            0x0009 -> "Request Authorization Entries"
            0x000A -> "Authorization Entry"
            0x000B -> "Authorization Data (Invite)"
            0x000C -> "Nuki States"
            0x000D -> "Lock Action"
            0x000E -> "Status"
            0x000F -> "Most Recent Command"
            0x0010 -> "Openings Closings Summary"
            0x0011 -> "Battery Report"
            0x0012 -> "Error Report"
            0x0013 -> "Set Config"
            0x0014 -> "Request Config"
            0x0015 -> "Config"
            0x0019 -> "Set Security PIN"
            0x001A -> "Request Calibration"
            0x001D -> "Request Reboot"
            0x001E -> "Authorization-ID Confirmation"
            0x001F -> "Authorization-ID (Invite)"
            0x0020 -> "Verify Security PIN"
            0x0021 -> "Update Time"
            0x0025 -> "Update User Authorization"
            0x0027 -> "Authorization Entry Count"
            0x0030 -> "Request Disconnect"
            0x0031 -> "Request Log Entries"
            0x0032 -> "Log Entry"
            0x0033 -> "Log Entry Count"
            0x0034 -> "Enable Logging"
            0x0035 -> "Set Advanced Config"
            0x0036 -> "Request Advanced Config"
            0x0037 -> "Advanced Config"
            0x0039 -> "Add Time Control Entry"
            0x003A -> "Time Control Entry ID"
            0x003B -> "Remove Time Control Entry"
            0x003C -> "Request Time Control Entries"
            0x003D -> "Time Control Entry Count"
            0x003E -> "Time Control Entry"
            0x003F -> "Update Time Control Entry"
            else -> "Unknown"
        }
    }

    fun getError(errorCode: Int): String {
        return when (errorCode) {
            0x10 -> "Device is not in paring mode."
            0x11 -> "P_ERROR_BAD_AUTHENTICATOR"
            0x12 -> "P_ERROR_BAD_PARAMETER"
            0x13 -> "P_ERROR_MAX_USER"
            0x20 -> "K_ERROR_NOT_AUTHORIZED"
            0x21 -> "K_ERROR_BAD_PIN"
            0x22 -> "K_ERROR_BAD_NONCE"
            0x23 -> "K_ERROR_BAD_PARAMETER"
            0x24 -> "K_ERROR_INVALID_AUTH_ID"
            0x25 -> "K_ERROR_DISABLED"
            0x26 -> "K_ERROR_REMOTE_NOT_ALLOWED"
            0x27 -> "K_ERROR_TIME_NOT_ALLOWED"
            0x28 -> "K_ERROR_TOO_MANY_PIN_ATTEMPTS"
            0x29 -> "K_ERROR_TOO_MANY_ENTRIES"
            0x2A -> "K_ERROR_CODE_ALREADY_EXISTS"
            0x2B -> "K_ERROR_CODE_INVALID"
            0x2C -> "K_ERROR_CODE_INVALID_TIMEOUT_1"
            0x2D -> "K_ERROR_CODE_INVALID_TIMEOUT_2"
            0x2E -> "K_ERROR_CODE_INVALID_TIMEOUT_3"
            0x40 -> "K_ERROR_AUTO_UNLOCK_TOO_RECENT"
            0x41 -> "K_ERROR_POSITION_UNKNOWN"
            0x42 -> "K_ERROR_MOTOR_BLOCKED"
            0x43 -> "K_ERROR_CLUTCH_FAILURE"
            0x44 -> "K_ERROR_MOTOR_TIMEOUT"
            0x45 -> "K_ERROR_BUSY"
            0x46 -> "K_ERROR_CANCELED"
            0x47 -> "K_ERROR_NOT_CALIBRATED"
            0x48 -> "K_ERROR_MOTOR_POSITION_LIMIT"
            0x49 -> "K_ERROR_MOTOR_LOW_VOLTAGE"
            0x4A -> "K_ERROR_MOTOR_POWER_FAILURE"
            0x4B -> "K_ERROR_CLUTCH_POWER_FAILURE"
            0x4C -> "K_ERROR_VOLTAGE_TOO_LOW"
            0x4D -> "K_ERROR_FIRMWARE_UPDATE_NEEDED"
            0xFD -> "ERROR_BAD_CRC"
            0xFE -> "ERROR_BAD_LENGTH"
            0xFF -> "ERROR_UNKNOWN"
            else -> "Unknown"
        }
    }

    fun getNukiState(value: Int): String {
        return when (value) {
            0x00 -> "Uninitialized"
            0x01 -> "Pairing Mode"
            0x02 -> "Door Mode"
            0x04 -> "Maintenance Mode"
            else -> "Unknown"
        }
    }

    fun getLockState(value: Int): String {
        return when (value) {
            0x00 -> "uncalibrated"
            0x01 -> "locked"
            0x02 -> "unlocking"
            0x03 -> "unlocked"
            0x04 -> "locking"
            0x05 -> "unlatched"
            0x06 -> "unlocked(lock ‘n’ go active)"
            0x07 -> "unlatching"
            0xFC -> "calibration"
            0xFD -> "boot run"
            0xFE -> "motor blocked"
            0xFF -> "undefined"
            else -> "unknown"
        }
    }

    // The trigger, that caused the state change of
    // the unlock mechanism within the Smart Lock
    fun getLockTrigger(trigger: Int): String {
        return when (trigger) {
            0x00 ->                 // via bluetooth command
                "system"
            0x01 ->                 // by using a key from outside the door
                // by rotating the wheel on the inside
                "manual"
            0x02 ->                 // by pressing the Smart Locks button
                "button"
            0x03 ->                 // Executed automatically (e.g. at a specific time)
                // by the Smart Lock
                "automatic"
            0x06 ->                 // Auto relock of the Smart Lock
                "auto lock"
            else -> "unknown"
        }
    }

    fun getBatteryState(state: Int): String {
        return when (state) {
            0x00 -> "OK"
            0x01 -> "Critical"
            else -> "Unknown"
        }
    }

    fun slice(data: ByteArray, offset: Int, length: Int): ByteArray {
        var length = length
        if (length < 0) {
            length = data.size - offset
        }
        val ret = ByteArray(length)
        System.arraycopy(data, offset, ret, 0, ret.size)
        return ret
    }

    fun from8(value: Int): ByteArray {
        return byteArrayOf((value and 0xff).toByte())
    }

    fun from16(value: Int): ByteArray {
        val v = ByteArray(2)
        write16(v, 0, value)
        return v
    }

    fun from32_auth_id(value: Long): ByteArray {
        val v = ByteArray(4)
        write32_auth_id(v, 0, value)
        return v
    }

    fun from32_app_id(value: Long): ByteArray {
        val v = ByteArray(4)
        write32_app_id(v, 0, value)
        return v
    }
/*
    static int read_i16(byte[] buffer, int offset) {
        return ((buffer[offset] & 0x80) == 0 ? 1 : -1) * (buffer[offset] & 0x7f)
        + ((buffer[offset + 1] & 0xff) << 8);
    }
*/
    fun read_i16(buffer: ByteArray, offset: Int): Int {
        return ((if ((buffer[offset].toInt() and 0x80) == 0) 1 else -1) * (buffer[offset].toInt() and 0x7f)
                + (buffer[offset + 1].toInt() and 0xff shl 8))
    }

    fun read16(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xff)
                + (buffer[offset + 1].toInt() and 0xff shl 8))
    }

    fun write16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xff).toByte()
        buffer[offset + 1] = (value shr 8 and 0xff).toByte()
    }

    fun read32_app_id(data: ByteArray, offset: Int): Long {
        return ((data[offset + 3].toLong() and 0xff)
                + (data[offset + 2].toLong() and 0xff shl 8)
                + (data[offset + 1].toLong() and 0xff shl 16)
                + (data[offset + 0].toLong() and 0xff shl 24))
    }

    fun write32_app_id(data: ByteArray, offset: Int, value: Long) {
        data[offset + 0] = (value shr 24 and 0xff).toByte()
        data[offset + 1] = (value shr 16 and 0xff).toByte()
        data[offset + 2] = (value shr 8 and 0xff).toByte()
        data[offset + 3] = (value and 0xff).toByte()
    }

    fun read32_auth_id(data: ByteArray, offset: Int): Long {
        return ((data[offset + 0].toLong() and 0xff)
                + (data[offset + 1].toLong() and 0xff shl 8)
                + (data[offset + 2].toLong() and 0xff shl 16)
                + (data[offset + 3].toLong() and 0xff shl 24))
    }

    fun write32_auth_id(data: ByteArray, offset: Int, value: Long) {
        data[offset + 3] = (value shr 24 and 0xff).toByte()
        data[offset + 2] = (value shr 16 and 0xff).toByte()
        data[offset + 1] = (value shr 8 and 0xff).toByte()
        data[offset + 0] = (value and 0xff).toByte()
    }

    fun nameToBytes(name: String?, length: Int): ByteArray {
        val ret = ByteArray(length)
        val bytes = name!!.toByteArray()
        System.arraycopy(bytes, 0, ret, 0, Math.min(bytes.size, ret.size))
        return ret
    }

    fun concat(vararg arrays: ByteArray): ByteArray {
        var length = 0
        run {
            var i = 0
            while (i < arrays.size) {
                length += arrays[i].size
                i += 1
            }
        }
        val data = ByteArray(length)
        var offset = 0
        var i = 0
        while (i < arrays.size) {
            System.arraycopy(arrays[i], 0, data, offset, arrays[i].size)
            offset += arrays[i].size
            i += 1
        }
        return data
    }

    fun crc16(data: ByteArray, offset: Int, count: Int): Int {
        var crc = 0xFFFF // initial value
        val polynomial = 0x1021 // 0001 0000 0010 0001  (0, 5, 12)
        for (j in offset until count) {
            for (i in 0..7) {
                val bit = data[j].toInt() shr 7 - i and 1 == 1
                val c15 = crc shr 15 and 1 == 1
                crc = crc shl 1
                if (c15 xor bit) crc = crc xor polynomial
            }
        }
        return crc and 0xffff
    }

    fun getProperties(c: BluetoothGattCharacteristic): String {
        val p = c.properties
        var ret = ""
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
            ret += "READ "
        }
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            ret += "WRITE "
        }
        if (p and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) {
            ret += "BROADCAST "
        }
        if (p and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) {
            ret += "EXT "
        }
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            ret += "INDICATE "
        }
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            ret += "NOTIFIY "
        }
        if (p and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) {
            ret += "SIGNED_WRITE "
        }
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            ret += "WRITE_NO_RESPONSE "
        }
        return ret
    }
}