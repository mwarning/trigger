package app.trigger.nuki

import org.libsodium.jni.Sodium
import android.util.Log
import java.util.*

open class NukiCommand(var command: Int) {
    internal class NukiRequest(var command_id: Int) : NukiCommand(0x0001) {
        fun generate(): ByteArray {
            return NukiTools.concat(NukiTools.from16(command), NukiTools.from16(command_id))
        }
    }

    internal class NukiAuthIdConfirm(var authenticator: ByteArray, var auth_id: Long) : NukiCommand(0x001E) {
        fun generate(): ByteArray {
            return NukiTools.concat(NukiTools.from16(command), authenticator, NukiTools.from32_auth_id(auth_id))
        }
    }

    internal class NukiAuthData(var authenticator: ByteArray, // 0x00: App, 0x01: Bridge, 0x02 Fob, 0x03 Keypad
                                var id_type: Int, // The ID of the Nuki App, Nuki Bridge or Nuki Fob to be authorized. same as auth_id????
                                var app_id: Long, var name: String, var nonce: ByteArray) : NukiCommand(0x0006) {
        fun generate(): ByteArray {
            return NukiTools.concat(NukiTools.from16(command), authenticator, NukiTools.from8(id_type), NukiTools.from32_app_id(app_id), NukiTools.nameToBytes(name, 32), nonce)
        }
    }

    internal class NukiError(var error_code: Int, var command_id: Int) : NukiCommand(0x0012) {
        fun asString(): String {
            return "Nuki Error: " + NukiTools.getError(error_code)
        }
    }

    internal class NukiPublicKey(var public_key: ByteArray) : NukiCommand(0x0003) {
        fun generate(): ByteArray {
            return NukiTools.concat(NukiTools.from16(command), public_key)
        }
    }

    internal class NukiChallenge(var nonce: ByteArray) : NukiCommand(0x0004) {
        fun generate(): ByteArray {
            return NukiTools.concat(NukiTools.from16(command), nonce)
        }

        init {
            if (nonce.size != 32) {
                Log.e("NukiChallenge", "invalid nonce length: " + nonce.size + " (expected " + 32 + ")")
            }
        }
    }

    internal class NukiAuthID(var authenticator: ByteArray, var auth_id: Long, var uuid: ByteArray, var nonce: ByteArray) : NukiCommand(0x0007) {
        fun verify(shared_key: ByteArray?, nonce: ByteArray?): Boolean {
            val valueR = NukiTools.concat(NukiTools.from32_auth_id(auth_id), uuid, this.nonce, nonce!!)
            val authenticator = ByteArray(Sodium.crypto_auth_hmacsha256_bytes())
            if (Sodium.crypto_auth_hmacsha256(authenticator, valueR, valueR.size, shared_key) != 0) {
                Log.e("NukiAuthID", "crypto_auth_hmacsha256 failed")
                return false
            }
            return Arrays.equals(this.authenticator, authenticator)
        }
    }

    internal class NukiStatus(var status: Int) : NukiCommand(0x000E) {
        fun generate(): ByteArray {
            return NukiTools.concat(NukiTools.from16(command), NukiTools.from8(status))
        }

        companion object {
            const val STATUS_COMPLETE = 0x00 // Returned to signal the successful completion of a command
            const val STATUS_ACCEPTED = 0x01 // Returned to signal that a command has been accepted but the completion status will be signaled later.
        }
    }

    internal class NukiStates(var nuki_state: Int, var lock_state: Int, var lock_trigger: Int, var current_time: String, var time_offset: Int, var battery_critical: Int) : NukiCommand(0x000C)
    internal class NukiLockAction(var lock_action: Int, var app_id: Long, var flags: Int, // optional
                                  var name_suffix: String?, var nonce: ByteArray?) : NukiCommand(0x000D) {
        constructor(lock_action: Int, app_id: Long, flags: Int, nonce: ByteArray?) : this(lock_action, app_id, flags, null, nonce)

        fun generate(): ByteArray {
            val name_suffix_padded = if (name_suffix == null) {
                ByteArray(0)
            } else {
                NukiTools.nameToBytes(name_suffix, 20)
            }
            return NukiTools.concat(NukiTools.from16(command), NukiTools.from8(lock_action), NukiTools.from32_app_id(app_id), NukiTools.from8(flags), name_suffix_padded, nonce!!)
        }

        init {
            if (nonce!!.size != 32) {
                Log.e("NukiLockAction", "nonce has wrong length: " + nonce!!.size)
            }
        }
    }

    internal class NukiAuthAuthentication(private var authenticator: ByteArray) : NukiCommand(0x0005) {
        fun generate(): ByteArray {
            return NukiTools.concat(NukiTools.from16(command), authenticator)
        }
    }
}