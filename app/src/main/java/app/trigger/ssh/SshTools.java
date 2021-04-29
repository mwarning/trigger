package app.trigger.ssh;

import net.schmizz.sshj.Config;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.common.Factory;
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider;
import net.schmizz.sshj.userauth.keyprovider.KeyFormat;
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil;
import net.schmizz.sshj.userauth.password.PasswordFinder;

import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.StringWriter;
import java.security.PrivateKey;

import app.trigger.Log;


public class SshTools {
    static final String TAG = "SshTools";

    public static String serializeKeyPair(KeyPairTrigger keypair, String passphrase) {
        if (keypair == null) {
            return "";
        } else {
            return keypair.getData();
        }
    }

    public static KeyPairTrigger deserializeKeyPair(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }
        return new KeyPairTrigger(str);
    }

    public static String privateKeyToPEM(PrivateKey pk) {
        try {
            AsymmetricKeyParameter akp = PrivateKeyFactory.createKey(pk.getEncoded());
            byte[] content = OpenSSHPrivateKeyUtil.encodePrivateKey(akp);
            PemObject o = new PemObject("OPENSSH PRIVATE KEY", content);
            StringWriter sw = new StringWriter();
            PemWriter w = new PemWriter(sw);
            w.writeObject(o);
            w.close();
            return sw.toString();
        } catch (Exception e) {
            Log.e(TAG, "privateKeyToPEM(): " + e);
        }
        return null;
    }

    public static boolean isPrivateKey(String privateKey) {
        if (privateKey == null || privateKey.length() == 0) {
            return false;
        }

        try {
            final Config config = new DefaultConfig();
            final String publicKey = null;
            final PasswordFinder passwordFinder = null;
            final KeyFormat format = KeyProviderUtil.detectKeyFileFormat(privateKey, publicKey != null);
            final FileKeyProvider fkp = Factory.Named.Util.create(config/*trans.getConfig()*/.getFileKeyProviderFactories(), format.toString());
            if (fkp == null) {
                Log.e(TAG, "No provider available for " + format + " key file");
                return false;
            }
            fkp.init(privateKey, publicKey, passwordFinder);
            return (fkp.getPublic() != null && fkp.getPrivate() != null);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return false;
        }
    }

    // for <= 2.2.0
    public static KeyPairTrigger deserializeKeyPair220(String str) {
        if (isPrivateKey(str)) {
            return new KeyPairTrigger(str);
        } else {
            return null;
        }
    }
}
