package app.trigger.ssh;

import androidx.annotation.NonNull;

import net.schmizz.sshj.Config;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.common.Factory;
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider;
import net.schmizz.sshj.userauth.keyprovider.KeyFormat;
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil;
import net.schmizz.sshj.userauth.password.PasswordFinder;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.Serializable;
import java.io.StringWriter;
import java.security.KeyPair;

import app.trigger.Log;


/*
* A wrapper for a private key in PEM (text) format.
* This is necessary for type inference mechanism.
* The public key is derived/extracted if needed.
*/
public class KeyPairTrigger implements Serializable {
    private static final String TAG = "KeyPairTrigger";
    private final String data;

    KeyPairTrigger(@NonNull String data) {
        this.data = data;
    }

    KeyPair toKeyPair() {
        try {
            Config config = new DefaultConfig();
            String privateKey = this.data;
            String publicKey = null;
            PasswordFinder passwordFinder = null;
            KeyFormat format = KeyProviderUtil.detectKeyFileFormat(privateKey, publicKey != null);
            FileKeyProvider fkp = Factory.Named.Util.create(config.getFileKeyProviderFactories(), format.toString());
            if (fkp == null) {
                Log.e(TAG, "No provider available for " + format + " key file");
                return null;
            }
            fkp.init(privateKey, publicKey, passwordFinder);
            if (fkp.getPublic() != null && fkp.getPrivate() != null) {
                return new KeyPair(fkp.getPublic(), fkp.getPrivate());
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return null;
    }

    String getPrivateKeyPEM() {
        return this.data;
    }

    String getPublicKeyPEM() {
        KeyPair kp = toKeyPair();
        try {
            StringWriter sw = new StringWriter();
            JcaPEMWriter pemWriter = new JcaPEMWriter(sw);
            pemWriter.writeObject(kp.getPublic());
            pemWriter.close();
            return sw.toString();
        } catch (Exception e) {
            Log.e(TAG, "getPublicKeyPEM(): Failed to extract public key");
        }
        return "";
    }

    String getFingerPrint() {
        KeyPair kp = toKeyPair();
        if (kp != null) {
            return net.schmizz.sshj.common.SecurityUtils.getFingerprint(kp.getPublic());
            //return (new Fingerprint(kp.getPublic().getEncoded())).toString();
        } else {
            return "";
        }
    }

    String getData() {
        return data;
    }
}
