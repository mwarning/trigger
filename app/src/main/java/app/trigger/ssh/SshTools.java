package app.trigger.ssh;

import com.trilead.ssh2.crypto.Base64;
import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.crypto.PEMStructure;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.security.KeyPair;

import app.trigger.Log;
import app.trigger.Utils;


public class SshTools {
    static final String TAG = "SshTools";

    public static String serializeKeyPair(KeyPairBean keypair) {
        if (keypair == null) {
            return "";
        }

        try {
            JSONObject obj = new JSONObject();
            obj.put("type", keypair.type);
            obj.put("privateKey", Utils.byteArrayToHexString(keypair.privateKey));
            obj.put("publicKey", Utils.byteArrayToHexString(keypair.publicKey));
            obj.put("encrypted", keypair.encrypted);
            return obj.toString();
        } catch (JSONException e) {
            Log.e(TAG, "serializeKeyPair: " + e);
        }
        return null;
    }

    public static KeyPairBean deserializeKeyPair(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }

        try {
            JSONObject obj = new JSONObject(str);

            KeyPairBean kp = new KeyPairBean();
            kp.type = obj.getString("type");
            kp.privateKey = Utils.hexStringToByteArray(obj.getString("privateKey"));
            kp.publicKey = Utils.hexStringToByteArray(obj.getString("publicKey"));
            kp.encrypted = obj.getBoolean("encrypted");
            return kp;
        } catch (JSONException e) {
            Log.e(TAG, "deserializeKeyPair: " + e);
        }
        return null;
    }

    public static KeyPairBean deserializeKeyPair_3_2_3(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }

        try {
            //JSch jsch = new JSch();
            //KeyPair keypair = KeyPair.load(jsch, str.getBytes(), null);
            //keypair.setPublicKeyComment(null);
            //return keypair;
            return parsePrivateKeyPEM(str);
        } catch (Exception e) {
            Log.e(TAG, "deserialize error: " + e.toString());
        }
        return null;
    }

    // for <= 1.9.1
    public static KeyPairBean deserializeKeyPair_1_9_1(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }

        try {
            // base64 string to bytes
            byte[] bytes = Base64.decode(str.toCharArray());

            // bytes to KeyPairData
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ios = new ObjectInputStream(bais);
            KeyPairData obj = (KeyPairData) ios.readObject();

            return parsePrivateKeyPEM(new String(obj.prvkey));

            // KeyParData to KeyPair
            //JSch jsch = new JSch();
            //KeyPair keypair = KeyPair.load(jsch, obj.prvkey, obj.pubkey);
            //return keypair;
        } catch (Exception e) {
            Log.e(TAG, "deserialize error: " + e.toString());
        }
        return null;
    }

    // helper class that holds the content of the old id_rsa/id_rsa.pub file content (PEM format)
    private static class KeyPairData implements Serializable {
        public final byte[] prvkey;
        public final byte[] pubkey;

        KeyPairData(byte[] prvkey, byte[] pubkey) {
            this.prvkey = prvkey;
            this.pubkey = pubkey;
        }
    };

    private static KeyPair readPKCS8Key(byte[] keyData) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(keyData)));

        // parse the actual key once to check if its encrypted
        // then save original file contents into our database
        try {
            ByteArrayOutputStream keyBytes = new ByteArrayOutputStream();

            String line;
            boolean inKey = false;
            while ((line = reader.readLine()) != null) {
                if (line.equals(PubkeyUtils.PKCS8_START)) {
                    inKey = true;
                } else if (line.equals(PubkeyUtils.PKCS8_END)) {
                    break;
                } else if (inKey) {
                    keyBytes.write(line.getBytes("US-ASCII"));
                }
            }

            if (keyBytes.size() > 0) {
                byte[] decoded = Base64.decode(keyBytes.toString().toCharArray());

                return PubkeyUtils.recoverKeyPair(decoded);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static String convertAlgorithmName(String algorithm) {
        if ("EdDSA".equals(algorithm)) {
            return KeyPairBean.KEY_TYPE_ED25519;
        } else {
            return algorithm;
        }
    }

    public static KeyPairBean parsePrivateKeyPEM(String keyData) {
        KeyPair kp;

        if ((kp = readPKCS8Key(keyData.getBytes())) != null) {
            String algorithm = convertAlgorithmName(kp.getPrivate().getAlgorithm());
            KeyPairBean pubkey = new KeyPairBean();
            pubkey.type = algorithm;
            pubkey.privateKey = kp.getPrivate().getEncoded();
            pubkey.publicKey = kp.getPublic().getEncoded();
            return pubkey;
        } else {
            try {
                PEMStructure struct = PEMDecoder.parsePEM(keyData.toCharArray());
                boolean encrypted = PEMDecoder.isPEMEncrypted(struct);

                if (!encrypted) {
                    kp = PEMDecoder.decode(struct, null);
                    String algorithm = convertAlgorithmName(kp.getPrivate().getAlgorithm());
                    KeyPairBean pk = new KeyPairBean();
                    pk.type = algorithm;
                    pk.privateKey = kp.getPrivate().getEncoded();
                    pk.publicKey = kp.getPublic().getEncoded();
                    pk.encrypted = encrypted;
                    return pk;
                } else {
                    KeyPairBean pk = new KeyPairBean();
                    pk.type = KeyPairBean.KEY_TYPE_IMPORTED;
                    pk.privateKey = keyData.getBytes();
                    pk.encrypted = encrypted;
                    return pk;
                }
            } catch (IOException e) {
                Log.e(TAG, "Problem parsing imported private key: " + e);
            }
        }
        return null;
    }
}
