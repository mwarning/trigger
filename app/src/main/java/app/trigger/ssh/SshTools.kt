package app.trigger.ssh

import app.trigger.Log
import app.trigger.Utils.byteArrayToHexString
import app.trigger.Utils.hexStringToByteArray
import org.json.JSONObject
import org.json.JSONException
import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.Base64
import java.io.*
import java.lang.Exception
import java.security.KeyPair

object SshTools {
    private const val TAG = "SshTools"
    fun serializeKeyPair(keypair: KeyPairBean?): String? {
        if (keypair == null) {
            return ""
        }
        try {
            val obj = JSONObject()
            obj.put("type", keypair.type)
            obj.put("privateKey", byteArrayToHexString(keypair.privateKey))
            obj.put("publicKey", byteArrayToHexString(keypair.publicKey))
            obj.put("encrypted", keypair.encrypted)
            return obj.toString()
        } catch (e: JSONException) {
            Log.e(TAG, "serializeKeyPair: $e")
        }
        return null
    }

    fun deserializeKeyPair(str: String?): KeyPairBean? {
        return if (str == null || str.length == 0) {
            null
        } else try {
            val obj = JSONObject(str)
            KeyPairBean(
                obj.getString("type"),
                hexStringToByteArray(obj.getString("privateKey")),
                hexStringToByteArray(obj.getString("publicKey")),
                obj.getBoolean("encrypted")
            )
        } catch (e: JSONException) {
            Log.e(TAG, "deserializeKeyPair: $e")

            // fallback for old
            deserializeKeyPair_3_2_3(str)
        }
    }

    fun deserializeKeyPair_3_2_3(str: String?): KeyPairBean? {
        if (str == null || str.length == 0) {
            return null
        }
        try {
            return parsePrivateKeyPEM(str)
        } catch (e: Exception) {
            Log.e(TAG, "deserialize error: $e")
        }
        return null
    }

    // for <= 1.9.1
    fun deserializeKeyPair_1_9_1(str: String?): KeyPairBean? {
        if (str == null || str.length == 0) {
            return null
        }
        try {
            // base64 string to bytes
            val bytes = Base64.decode(str.toCharArray())

            // bytes to KeyPairData
            val bais = ByteArrayInputStream(bytes)
            val ios = ObjectInputStream(bais)
            val obj = ios.readObject() as KeyPairData
            return parsePrivateKeyPEM(String(obj.prvkey))
        } catch (e: Exception) {
            Log.e(TAG, "deserialize error: $e")
        }
        return null
    }

    private fun readPKCS8Key(keyData: ByteArray): KeyPair? {
        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(keyData)))

        // parse the actual key once to check if its encrypted
        // then save original file contents into our database
        try {
            val keyBytes = ByteArrayOutputStream()
            var line: String
            var inKey = false
            while (reader.readLine().also { line = it } != null) {
                if (line == PubkeyUtils.PKCS8_START) {
                    inKey = true
                } else if (line == PubkeyUtils.PKCS8_END) {
                    break
                } else if (inKey) {
                    keyBytes.write(line.toByteArray(charset("US-ASCII")))
                }
            }
            if (keyBytes.size() > 0) {
                val decoded = Base64.decode(keyBytes.toString().toCharArray())
                return PubkeyUtils.recoverKeyPair(decoded)
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }

    private fun convertAlgorithmName(algorithm: String): String {
        return if ("EdDSA" == algorithm) {
            KeyPairBean.Companion.KEY_TYPE_ED25519
        } else {
            algorithm
        }
    }

    fun parsePrivateKeyPEM(keyData: String): KeyPairBean? {
        var kp: KeyPair
        if (readPKCS8Key(keyData.toByteArray()).also { kp = it!! } != null) {
            val algorithm = convertAlgorithmName(kp.private.algorithm)
            return KeyPairBean(algorithm, kp.private.encoded, kp.public.encoded, false)
        } else {
            try {
                val struct = PEMDecoder.parsePEM(keyData.toCharArray())
                val encrypted = PEMDecoder.isPEMEncrypted(struct)
                return if (!encrypted) {
                    kp = PEMDecoder.decode(struct, null)
                    val algorithm = convertAlgorithmName(kp.private.algorithm)
                    KeyPairBean(algorithm, kp.private.encoded, kp.public.encoded, encrypted)
                } else {
                    KeyPairBean(KeyPairBean.Companion.KEY_TYPE_IMPORTED,  keyData.toByteArray(), ByteArray(0), encrypted)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Problem parsing imported private key: $e")
            }
        }
        return null
    }

    // helper class that holds the content of the old id_rsa/id_rsa.pub file content (PEM format)
    private class KeyPairData internal constructor(val prvkey: ByteArray, val pubkey: ByteArray) : Serializable
}