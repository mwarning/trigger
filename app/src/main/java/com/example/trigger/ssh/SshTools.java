package com.example.trigger.ssh;

import android.util.Base64;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;

import com.example.trigger.Log;


public class SshTools {
    static final String TAG = "SshTools";

    // helper class that holds the content of id_rsa/id_rsa.pub file content (PEM format)
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
            Log.e(TAG ,"keypairtoBytes " + e.toString());
        }
        return null;
    }

    public static String serializeKeyPair(KeyPair keypair) {
        if (keypair == null) {
            return "";
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            keypair.writePrivateKey(baos);
            baos.close();
            return new String(baos.toByteArray());
        } catch (Exception e) {
            Log.e(TAG, "serialize error: " + e.toString());
        }
        return null;
    }

    public static KeyPair deserializeKeyPair(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }

        try {
            JSch jsch = new JSch();
            KeyPair keypair = KeyPair.load(jsch, str.getBytes(), null);
            keypair.setPublicKeyComment(null);

            return keypair;
        } catch (Exception e) {
            Log.e(TAG, "deserialize error: " + e.toString());
        }
        return null;
    }

    // for <= 1.9.1
    public static KeyPair deserializeKeyPairOld(String str) {
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
            KeyPair keypair = KeyPair.load(jsch, obj.prvkey, obj.pubkey);
            if (keypair != null) {
                keypair.setPublicKeyComment(null);
            }
            return keypair;
        } catch (Exception e) {
            Log.e(TAG, "deserialize error: " + e.toString());
        }
        return null;
    }
}
