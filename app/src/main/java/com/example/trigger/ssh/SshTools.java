package com.example.trigger.ssh;

import android.util.Base64;
import android.util.Log;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


public class SshTools {
    // helper class that holds the content of id_rsa/id_rsa.pub file content
    public static class KeyPairData implements Serializable {
        public final byte[] prvkey;
        public final byte[] pubkey;

        KeyPairData(byte[] prvkey, byte[] pubkey) {
            this.prvkey = prvkey;
            this.pubkey = pubkey;
        }
    };

    public static KeyPairData keypairToBytes(KeyPair keypair) {
        try {
            ByteArrayOutputStream prvstream = new ByteArrayOutputStream();
            ByteArrayOutputStream pubstream = new ByteArrayOutputStream();

            keypair.writePrivateKey(prvstream);
            keypair.writePublicKey(pubstream, keypair.getPublicKeyComment());
            prvstream.close();
            pubstream.close();

            return new KeyPairData(prvstream.toByteArray(), pubstream.toByteArray());
        } catch (Exception e) {
            Log.e("SshTools.keypairtoBytes", e.toString());
        }
        return null;
    }

    public static String serializeKeyPair(KeyPair keypair) {
        if (keypair == null) {
            return "";
        }

        try {
            // KeyPair to KeyPairData
            KeyPairData obj = keypairToBytes(keypair);

            // serialize KeyPairData to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.close();

            // bytes to base64 string
            return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            Log.e("SshTools", "serialize error: " + e.toString());
        }
        return null;
    }

    public static KeyPair deserializeKeyPair(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }

        try {
            // base64 string to bytes
            byte[] bytes = Base64.decode(str, Base64.DEFAULT);

            // bytes to KeyPairData
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ios = new ObjectInputStream(bais);
            KeyPairData obj = (KeyPairData) ios.readObject();

            // KeyParData to KeyPair
            JSch jsch = new JSch();
            return KeyPair.load(jsch, obj.prvkey, obj.pubkey);
        } catch (Exception e) {
            Log.e("SshTools", "deserialize error: " + e.toString());
        }
        return null;
    }

    public static final void writeKeyFiles(String targetPath, KeyPair keypair) throws Exception {
        File targetDir = new File(targetPath);
        File privateKeyFile = new File(targetDir, "id_rsa");
        File publicKeyFile = new File(targetDir, "id_rsa.pub");

        if (privateKeyFile.exists()) {
            privateKeyFile.delete();
        }

        if (publicKeyFile.exists()) {
            publicKeyFile.delete();
        }

        keypair.writePrivateKey(new FileOutputStream(privateKeyFile));
        keypair.writePublicKey(new FileOutputStream(publicKeyFile), "none");
    }
}
