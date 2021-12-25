package app.trigger.ssh;

import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.crypto.PEMStructure;

import java.io.IOException;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Locale;

import app.trigger.Log;

/*
* A wrapper for a private key in PEM (text) format.
* This is necessary for type inference mechanism.
* The public key is derived/extracted if needed.
*/
public class KeyPairBean implements Serializable {
    private static final String TAG = "KeyPairBean";

    public final static String KEY_TYPE_RSA = "RSA";
    public final static String KEY_TYPE_DSA = "DSA";
    public final static String KEY_TYPE_IMPORTED = "IMPORTED"; // imported PEM key
    public final static String KEY_TYPE_EC = "EC";
    public final static String KEY_TYPE_ED25519 = "ED25519";

    public String type;
    public byte[] privateKey;
    public byte[] publicKey;
    public boolean encrypted = false;
    public String nickname = "";

    public String getType() {
        return type;
    }

    byte[] getPublicKey() {
        return publicKey;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public String getOpenSSHPublicKey() {
        try {
            PublicKey pk = PubkeyUtils.decodePublic(publicKey, type);
            return PubkeyUtils.convertToOpenSSHFormat(pk, nickname);
        } catch (Exception e) {
            Log.e(TAG, "getOpenSSHPublicKey: " + e.getMessage());
        }
        return null;
    }

    public String getOpenSSHPrivateKey() {
        try {
            if (type.equals(KeyPairBean.KEY_TYPE_IMPORTED)) {
                return new String(privateKey);
            } else {
                PrivateKey pk = PubkeyUtils.decodePrivate(privateKey, type);
                return PubkeyUtils.exportPEM(pk, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "getOpenSSHPrivateKey: " + e.getMessage());
        }
        return null;
    }

    public String getDescription() {
        if (KEY_TYPE_IMPORTED.equals(type)) {
            try {
                PEMStructure struct = PEMDecoder.parsePEM(new String(privateKey).toCharArray());
                String type;
                if (struct.pemType == PEMDecoder.PEM_RSA_PRIVATE_KEY) {
                    type = "RSA";
                } else if (struct.pemType == PEMDecoder.PEM_DSA_PRIVATE_KEY) {
                    type = "DSA";
                } else if (struct.pemType == PEMDecoder.PEM_EC_PRIVATE_KEY) {
                    type = "EC";
                } else if (struct.pemType == PEMDecoder.PEM_OPENSSH_PRIVATE_KEY) {
                    type = "OpenSSH";
                } else {
                    throw new RuntimeException("Unexpected key type: " + struct.pemType);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error decoding IMPORTED public key: " + e);
            }
            return String.format("%s unknown-bit", type);
        } else {
            Integer bits = null;
            try {
                bits = PubkeyUtils.getBitStrength(publicKey, type);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException ignored) {
            }

            final StringBuilder sb = new StringBuilder();
            if (KEY_TYPE_RSA.equals(type)) {
                sb.append(String.format(Locale.getDefault(), "RSA %d-bit", bits));
            } else if (KEY_TYPE_DSA.equals(type)) {
                sb.append(String.format(Locale.getDefault(), "DSA %d-bit", 1024));
            } else if (KEY_TYPE_EC.equals(type)) {
                sb.append(String.format(Locale.getDefault(), "EC %d-bit", bits));
            } else if (KEY_TYPE_ED25519.equals(type)) {
                sb.append("ED25519"); // 256 bit, but this might give the wrong imporession regarding security
            } else {
                sb.append("Unknown key type");
            }

            if (encrypted) {
                sb.append(" (encrypted)");
            }

            return sb.toString();
        }
    }
}
