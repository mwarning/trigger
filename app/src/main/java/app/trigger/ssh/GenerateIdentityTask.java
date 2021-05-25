package app.trigger.ssh;

import android.os.AsyncTask;

import com.trilead.ssh2.crypto.keys.Ed25519Provider;

import app.trigger.Log;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;


class GenerateIdentityTask extends AsyncTask<Object, Void, String> {
    private static final String TAG = "GenerateIdentityTask";
    public OnTaskCompleted listener = null;
    public KeyPairBean keypair = null;

    static {
        android.util.Log.d(TAG, "Ed25519Provider.insertIfNeeded2");
        // Since this class deals with Ed25519 keys, we need to make sure this is available.
        Ed25519Provider.insertIfNeeded();
    }

    public interface OnTaskCompleted {
        void onGenerateIdentityTaskCompleted(String message, KeyPairBean keypair);
    }

    public GenerateIdentityTask(OnTaskCompleted listener) {
        this.listener = listener;
    }

    private String convertAlgorithmName(String algorithm) {
        if ("EdDSA".equals(algorithm)) {
            return KeyPairBean.KEY_TYPE_ED25519;
        } else {
            return algorithm;
        }
    }

    @Override
    protected String doInBackground(Object... params) {
        if (params.length != 1) {
            Log.e(TAG, "Unexpected number of params.");
            return "Internal Error";
        }

        try {
            String type = (String) params[0];

            if (type.equals("ED25519")) {
                keypair = createKeyPair(KeyPairBean.KEY_TYPE_ED25519, 256);
            } else if (type.equals("ECDSA-384")) {
                keypair = createKeyPair(KeyPairBean.KEY_TYPE_EC, 384);
            } else if (type.equals("ECDSA-521")) {
                keypair = createKeyPair(KeyPairBean.KEY_TYPE_EC, 521);
            } else if (type.equals("RSA-2048")) {
                keypair = createKeyPair(KeyPairBean.KEY_TYPE_RSA, 2048);
            } else if (type.equals("RSA-4096")) {
                keypair = createKeyPair(KeyPairBean.KEY_TYPE_RSA, 4096);
            } else if (type.equals("DSA-1024")) {
                keypair = createKeyPair(KeyPairBean.KEY_TYPE_DSA, 1024);
            } else {
                return "Unknown key type: " + type;
            }
        } catch (Exception e) {
            return e.getMessage();
        }

        return "Done";
    }

    @Override
    protected void onPostExecute(String message) {
    	listener.onGenerateIdentityTaskCompleted(message, keypair);
    }

    KeyPairBean createKeyPair(String type, int bits) {
        SecureRandom random = new SecureRandom();

        // Work around JVM bug
        //random.nextInt();
        //random.setSeed(entropy); //TODO!

        try {
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(type);

            keyPairGen.initialize(bits, random);

            KeyPair pair = keyPairGen.generateKeyPair();

            PrivateKey priv = pair.getPrivate();
            PublicKey pub = pair.getPublic();

            //Log.d(TAG, "PrivateKey: " + priv.getAlgorithm() + " " + priv.getFormat() + " " + priv.getEncoded().length);
            //Log.d(TAG, "PublicKey: " + pub.getAlgorithm() + " " + pub.getFormat() + " " + pub.getEncoded().length);

            String nickname = "";
            String secret = ""; // password for encrypted key

            //Log.d(TAG, "private: " + PubkeyUtils.formatKey(priv));
            Log.d(TAG, "public: " + PubkeyUtils.formatKey(pub)); // public: Key[algorithm=EdDSA, format=X.509, bytes=44]

            byte[] privateKey = PubkeyUtils.getEncodedPrivate(priv, secret).clone();
            byte[] publicKey = pub.getEncoded().clone();

            KeyPairBean kpt = new KeyPairBean();
            kpt.type = type;
            kpt.publicKey = publicKey;
            kpt.privateKey = privateKey;
            return kpt;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        return null;
    }
}