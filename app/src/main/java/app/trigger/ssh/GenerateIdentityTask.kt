package app.trigger.ssh

import android.os.AsyncTask
import app.trigger.Log
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import java.lang.Exception
import java.security.KeyPairGenerator
import java.security.SecureRandom

internal class GenerateIdentityTask(var listener: OnTaskCompleted) : AsyncTask<Any?, Void?, String?>() {
    //var listener: OnTaskCompleted? = null
    var keypair: KeyPairBean? = null

    companion object {
        private const val TAG = "GenerateIdentityTask"

        init {
            Log.d(TAG, "Ed25519Provider.insertIfNeeded2")
            // Since this class deals with Ed25519 keys, we need to make sure this is available.
            Ed25519Provider.insertIfNeeded()
        }
    }

    interface OnTaskCompleted {
        fun onGenerateIdentityTaskCompleted(message: String?, keypair: KeyPairBean?)
    }

    private fun convertAlgorithmName(algorithm: String): String {
        return if ("EdDSA" == algorithm) {
            KeyPairBean.Companion.KEY_TYPE_ED25519
        } else {
            algorithm
        }
    }

    override fun doInBackground(vararg params: Any?): String? {
        if (params.size != 1) {
            Log.e(TAG, "Unexpected number of params.")
            return "Internal Error"
        }
        keypair = try {
            val type = params[0] as String
            if (type == "ED25519") {
                createKeyPair(KeyPairBean.Companion.KEY_TYPE_ED25519, 256)
            } else if (type == "ECDSA-384") {
                createKeyPair(KeyPairBean.Companion.KEY_TYPE_EC, 384)
            } else if (type == "ECDSA-521") {
                createKeyPair(KeyPairBean.Companion.KEY_TYPE_EC, 521)
            } else if (type == "RSA-2048") {
                createKeyPair(KeyPairBean.Companion.KEY_TYPE_RSA, 2048)
            } else if (type == "RSA-4096") {
                createKeyPair(KeyPairBean.Companion.KEY_TYPE_RSA, 4096)
            } else if (type == "DSA-1024") {
                createKeyPair(KeyPairBean.Companion.KEY_TYPE_DSA, 1024)
            } else {
                return "Unknown key type: $type"
            }
        } catch (e: Exception) {
            return e.message
        }
        return "Done"
    }

    override fun onPostExecute(message: String?) {
        listener.onGenerateIdentityTaskCompleted(message, keypair)
    }

    fun createKeyPair(type: String, bits: Int): KeyPairBean? {
        val random = SecureRandom()

        // Work around JVM bug
        //random.nextInt();
        //random.setSeed(entropy); //TODO!
        try {
            val keyPairGen = KeyPairGenerator.getInstance(type)
            keyPairGen.initialize(bits, random)
            val pair = keyPairGen.generateKeyPair()
            val priv = pair.private
            val pub = pair.public

            //Log.d(TAG, "PrivateKey: " + priv.getAlgorithm() + " " + priv.getFormat() + " " + priv.getEncoded().length);
            //Log.d(TAG, "PublicKey: " + pub.getAlgorithm() + " " + pub.getFormat() + " " + pub.getEncoded().length);
            val nickname = ""
            val secret = "" // password for encrypted key

            //Log.d(TAG, "private: " + PubkeyUtils.formatKey(priv));
            Log.d(TAG, "public: " + PubkeyUtils.formatKey(pub)) // public: Key[algorithm=EdDSA, format=X.509, bytes=44]
            val privateKey = PubkeyUtils.getEncodedPrivate(priv, secret).clone()
            val publicKey = pub.encoded.clone()
            return KeyPairBean(type, privateKey, publicKey, false)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return null
    }

    init {
        this.listener = listener
    }
}