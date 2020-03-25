package com.example.trigger.nuki;

import android.util.Log;

import org.libsodium.jni.Sodium;

import java.util.Arrays;


class NukiCommand {
    int command;

    public NukiCommand(int command) {
        this.command = command;
    }

    static class NukiRequest extends NukiCommand {
        int command_id;

        NukiRequest(int command_id) {
            super(0x0001);
            this.command_id = command_id;
        }

        byte[] generate() {
            return NukiTools.concat(NukiTools.from16(command), NukiTools.from16(command_id));
        }
    }

    static class NukiAuthIdConfirm extends NukiCommand {
        byte[] authenticator;
        int auth_id;

        NukiAuthIdConfirm(byte[] authenticator, int auth_id) {
            super(0x001E);
            this.authenticator = authenticator;
            this.auth_id = auth_id;
        }

        byte[] generate() {
            return NukiTools.concat(NukiTools.from16(command), authenticator, NukiTools.from32_auth_id(auth_id));
        }
    }

    static class NukiAuthData extends NukiCommand {
        byte[] authenticator;
        int id_type; // 0x00: App, 0x01: Bridge, 0x02 Fob, 0x03 Keypad
        int app_id; // The ID of the Nuki App, Nuki Bridge or Nuki Fob to be authorized. same as auth_id????
        String name;
        byte[] nonce;

        NukiAuthData(byte[] authenticator, int id_type, int app_id, String name, byte[] nonce) {
            super(0x0006);
            this.authenticator = authenticator;
            this.id_type = id_type;
            this.app_id = app_id;
            this.name = name;
            this.nonce = nonce;
        }

        byte[] generate() {
            return NukiTools.concat(NukiTools.from16(command), authenticator, NukiTools.from8(id_type), NukiTools.from32_app_id(app_id), NukiTools.nameToBytes(name, 32), nonce);
        }
    }

    static class NukiError extends NukiCommand {
        int error_code;
        int command_id;

        NukiError(int error_code, int command_id) {
            super(0x0012);
            this.error_code = error_code;
            this.command_id = command_id;
        }

        String asString() {
            return "Nuki Error: " + NukiTools.getError(error_code);
        }
    }

    static class NukiPublicKey extends NukiCommand {
        byte[] public_key;

        NukiPublicKey(byte[] public_key) {
            super(0x0003);
            this.public_key = public_key;
        }

        byte[] generate() {
            return NukiTools.concat(NukiTools.from16(command), public_key);
        }
    }

    static class NukiChallenge extends NukiCommand {
        byte[] nonce;

        NukiChallenge(byte[] nonce) {
            super(0x0004);
            this.nonce = nonce;
            if (nonce.length != 32) {
                Log.e("NukiChallenge", "invalid nonce length: " + nonce.length + " (expected " + 32 + ")");
            }
        }

        byte[] generate() {
            return NukiTools.concat(NukiTools.from16(command), nonce);
        }
    }

    static class NukiAuthID extends NukiCommand {
        byte[] authenticator;
        int auth_id;
        byte[] uuid;
        byte[] nonce;

        NukiAuthID(byte[] authenticator, int auth_id, byte[] uuid, byte[] nonce) {
            super(0x0007);
            this.authenticator = authenticator;
            this.auth_id = auth_id;
            this.uuid = uuid;
            this.nonce = nonce;
        }

        boolean verify(byte[] shared_key, byte[] nonce) {
            byte[] valueR = NukiTools.concat(NukiTools.from32_auth_id(this.auth_id), this.uuid, this.nonce, nonce);
            byte[] authenticator = new byte[Sodium.crypto_auth_hmacsha256_bytes()];
            if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR.length, shared_key) != 0) {
                Log.e("NukiAuthID", "crypto_auth_hmacsha256 failed");
                return false;
            }

            return Arrays.equals(this.authenticator, authenticator);
        }
    }

    static class NukiStatus extends NukiCommand {
        int status;

        static final int STATUS_COMPLETE = 0x00; // Returned to signal the successful completion of a command
        static final int STATUS_ACCEPTED = 0x01; // Returned to signal that a command has been accepted but the completion status will be signaled later.

        NukiStatus(int status) {
            super(0x000E);
            this.status = status;
        }

        byte[] generate() {
            return NukiTools.concat(NukiTools.from16(command), NukiTools.from8(status));
        }
    }

    static class NukiStates extends NukiCommand {
        int nuki_state;
        int lock_state;
        int lock_trigger;
        String current_time;
        int battery_critical;
        int time_offset;

        NukiStates(int nuki_state, int lock_state, int lock_trigger, String current_time, int time_offset, int battery_critical) {
            super(0x000C);
            this.nuki_state = nuki_state;
            this.lock_state = lock_state;
            this.lock_trigger = lock_trigger;
            this.current_time = current_time;
            this.time_offset = time_offset;
            this.battery_critical = battery_critical;
        }
    }

    static class NukiLockAction extends NukiCommand {
        int lock_action;
        int app_id;
        int flags;
        String name_suffix; // optional
        byte[] nonce;

        NukiLockAction(int lock_action, int app_id, int flags, String name_suffix, byte[] nonce) {
            super(0x000D);
            this.lock_action = lock_action;
            this.app_id = app_id;
            this.flags = flags;
            this.name_suffix = name_suffix;
            this.nonce = nonce;

            if (nonce.length != 32) {
                Log.e("NukiLockAction", "nonce has wrong length: " + nonce.length);
            }
        }

        NukiLockAction(int lock_action, int app_id, int flags, byte[] nonce) {
            this(lock_action, app_id, flags, null, nonce);
        }

        public byte[] generate() {
            byte[] name_suffix_padded;
            if (this.name_suffix == null) {
                name_suffix_padded = new byte[0];
            } else {
                name_suffix_padded = NukiTools.nameToBytes(this.name_suffix, 20);
            }
            return NukiTools.concat(NukiTools.from16(command), NukiTools.from8(lock_action), NukiTools.from32_app_id(app_id), NukiTools.from8(flags), name_suffix_padded, nonce);
        }
    }

    static class NukiAuthAuthentication extends NukiCommand {
        byte[] authenticator;

        NukiAuthAuthentication(byte[] authenticator) {
            super(0x0005);
            this.authenticator = authenticator;
        }

        byte[] generate() {
            return NukiTools.concat(NukiTools.from16(command), authenticator);
        }
    }
}