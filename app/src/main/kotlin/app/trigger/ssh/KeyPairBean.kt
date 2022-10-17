package app.trigger.ssh

import app.trigger.Log
import com.trilead.ssh2.crypto.PEMDecoder
import java.io.IOException
import java.io.Serializable
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.util.*

/*
* A wrapper for a private key in PEM (text) format.
* This is necessary for type inference mechanism.
* The public key is derived/extracted if needed.
*/
class KeyPairBean(val type: String, val privateKey: ByteArray, val publicKey: ByteArray, val encrypted: Boolean) : Serializable {
    /*
    var type: String? = null
    var privateKey: ByteArray
    var publicKey: ByteArray
    var encrypted = false
    */
    var nickname = ""
    val openSSHPublicKey: String?
        get() {
            try {
                val pk = PubkeyUtils.decodePublic(publicKey, type)
                return PubkeyUtils.convertToOpenSSHFormat(pk, nickname)
            } catch (e: Exception) {
                Log.e(TAG, "getOpenSSHPublicKey: " + e.message)
            }
            return null
        }
    val openSSHPrivateKey: String?
        get() {
            try {
                return if (type == KEY_TYPE_IMPORTED) {
                    String(privateKey)
                } else {
                    val pk = PubkeyUtils.decodePrivate(privateKey, type)
                    PubkeyUtils.exportPEM(pk, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "getOpenSSHPrivateKey: " + e.message)
            }
            return null
        }

    // 256 bit, but this might give the wrong impression regarding security
    val description: String
        get() = if (KEY_TYPE_IMPORTED == type) {
            var type = ""
            try {
                val struct = PEMDecoder.parsePEM(String(privateKey).toCharArray())
                type = when (struct.pemType) {
                    PEMDecoder.PEM_RSA_PRIVATE_KEY -> "RSA"
                    PEMDecoder.PEM_DSA_PRIVATE_KEY -> "DSA"
                    PEMDecoder.PEM_EC_PRIVATE_KEY -> "EC"
                    PEMDecoder.PEM_OPENSSH_PRIVATE_KEY -> "OpenSSH"
                    else -> throw RuntimeException("Unexpected key type: ${struct.pemType}")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error decoding IMPORTED public key: $e")
            }
            String.format("%s unknown-bit", type)
        } else {
            var bits: Int? = null
            try {
                bits = PubkeyUtils.getBitStrength(publicKey, type)
            } catch (ignored: NoSuchAlgorithmException) {
            } catch (ignored: InvalidKeySpecException) {
            }
            val sb = StringBuilder()
            if (KEY_TYPE_RSA == type) {
                sb.append(String.format(Locale.getDefault(), "RSA %d-bit", bits))
            } else if (KEY_TYPE_DSA == type) {
                sb.append(String.format(Locale.getDefault(), "DSA %d-bit", 1024))
            } else if (KEY_TYPE_EC == type) {
                sb.append(String.format(Locale.getDefault(), "EC %d-bit", bits))
            } else if (KEY_TYPE_ED25519 == type) {
                sb.append("ED25519") // 256 bit, but this might give the wrong impression regarding security
            } else {
                sb.append("Unknown key type")
            }
            if (encrypted) {
                sb.append(" (encrypted)")
            }
            sb.toString()
        }

    companion object {
        private const val TAG = "KeyPairBean"
        const val KEY_TYPE_RSA = "RSA"
        const val KEY_TYPE_DSA = "DSA"
        const val KEY_TYPE_IMPORTED = "IMPORTED" // imported PEM key
        const val KEY_TYPE_EC = "EC"
        const val KEY_TYPE_ED25519 = "ED25519"
    }
}