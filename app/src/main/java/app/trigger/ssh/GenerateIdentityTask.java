package app.trigger.ssh;

import android.os.AsyncTask;

import app.trigger.Log;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;


class GenerateIdentityTask extends AsyncTask<Object, Void, String> {
    private static final String TAG = "GenerateIdentityTask";
    public OnTaskCompleted listener = null;
    public KeyPairTrigger keypair = null;

    public interface OnTaskCompleted {
        void onGenerateIdentityTaskCompleted(String message, KeyPairTrigger keypair);
    }

    public GenerateIdentityTask(OnTaskCompleted listener) {
        this.listener = listener;
    }

    private static KeyPair convertBcToJceKeyPair(AsymmetricCipherKeyPair bcKeyPair) throws Exception {
        byte[] pkcs8Encoded = PrivateKeyInfoFactory.createPrivateKeyInfo(bcKeyPair.getPrivate()).getEncoded();
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(pkcs8Encoded);
        byte[] spkiEncoded = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(bcKeyPair.getPublic()).getEncoded();
        X509EncodedKeySpec spkiKeySpec = new X509EncodedKeySpec(spkiEncoded);
        KeyFactory keyFac = KeyFactory.getInstance("RSA");
        return new KeyPair(keyFac.generatePublic(spkiKeySpec), keyFac.generatePrivate(pkcs8KeySpec));
    }

    private KeyPairTrigger createEd25519KeyPair() throws Exception {
        Ed25519KeyPairGenerator generator = new Ed25519KeyPairGenerator();
        generator.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair kp = generator.generateKeyPair();
        KeyPair keypair = convertBcToJceKeyPair(kp);
        String pem = SshTools.privateKeyToPEM(keypair.getPrivate());
        return (pem != null) ? new KeyPairTrigger(pem) : null;
    }

    private KeyPairTrigger createRSAKeyPair(int keyLength) throws Exception {
        throw new Exception("createECDSAKeyPair(): not implemented");
    }

    private KeyPairTrigger createECDSAKeyPair(int keyLength) throws Exception {
        throw new Exception("createECDSAKeyPair(): not implemented");
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
                keypair = createEd25519KeyPair();
            } else if (type.equals("ECDSA-384")) {
                keypair = createECDSAKeyPair(384);
            } else if (type.equals("ECDSA-521")) {
                keypair = createECDSAKeyPair(521);
            } else if (type.equals("RSA-2048")) {
                keypair = createRSAKeyPair(2048);
            } else if (type.equals("RSA-4096")) {
                keypair = createRSAKeyPair(4096);
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
}