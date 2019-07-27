package com.example.trigger;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
//import java.util.zip.Deflater;
//import java.util.zip.Inflater;


public class Utils {
    public static boolean hasReadPermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    public static boolean hasWritePermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    public static boolean hasCameraPermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
    }

    public static void requestCameraPermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.CAMERA}, request_code);
    }

    public static void requestReadPermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE}, request_code);
    }

    public static void requestWritePermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, request_code);
    }

    public static boolean allGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static SSLSocketFactory getFactoryWithCertificate(Certificate cert)
            throws CertificateException, KeyStoreException, IOException,
            NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", cert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }

    // write file to external storage
    public static void writeExternalFile(String filepath, byte[] data) throws IOException {
        File file = new File(filepath);
        if (file.exists() && file.isFile()) {
            if (!file.delete()) {
                throw new IOException("Failed to delete existing file: " + filepath);
            }
        }
        file.createNewFile();
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.close();
    }

    // read file from external storage
    public static byte[] readExternalFile(String filepath) throws IOException {
        File file = new File(filepath);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File does not exist: " + filepath);
        }
        FileInputStream fis = new FileInputStream(file);

        int nRead;
        byte[] data = new byte[16384];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while ((nRead = fis.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        return buffer.toByteArray();
    }

    // parse "1.2.3.4" / "1.2.3.4:80" / "::1" / "[::1]:80"
    public static InetSocketAddress parseSocketAddress(String addr, int default_port)
            throws URISyntaxException {
        URI uri = new URI("my://" + addr);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            port = default_port;
        }

        if (host == null || port == -1) {
            throw new URISyntaxException(uri.toString(), "URI must have host and port parts");
        }

      // validation succeeded
      return new InetSocketAddress(host, port);
    }

    public static String serializeBitmap(Bitmap image) {
        if (image == null) {
            //Log.d("Utils", "serializeBitmap returns empty string");
            return "";
        }

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            boolean success = image.compress(Bitmap.CompressFormat.PNG, 0, byteStream);
            if (success) {
                return Base64.encodeToString(byteStream.toByteArray(), 0);
            } else {
                throw new Exception("compress failed");
            }
        } catch (Exception e) {
            Log.e("Utils", "serializeBitmap: " + e.toString());
        }

        return null;
    }

    public static Bitmap deserializeBitmap(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }

        try {
            byte[] data = Base64.decode(str, 0);
            Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (image == null) {
                Log.d("Utils", "deserializeBitmap returns null despite string input");
            }
            return image;
        } catch (Exception e) {
            Log.e("Utils", "deserializeBitmap: " + e.toString());
        }
        return null;
    }

/*
    // deflate compression
    public static byte[] deflateCompressString(String inputString) {
        try {
            byte[] input = inputString.getBytes("UTF-8");
            // Compress the bytes
            byte[] output = new byte[input.length];
            Deflater compresser = new Deflater();
            compresser.setInput(input);
            compresser.finish();
            int compressedDataLength = compresser.deflate(output);
            compresser.end();
            // create output array of exact size
            byte[] out = new byte[compressedDataLength];
            System.arraycopy(output, 0, out, 0, compressedDataLength);
            return out;
        } catch (Exception e) {

        }
        return null;
    }

    // deflate decompress
    public static String deflateDecompressString(byte[] input) {
        try {
            // Decompress the bytes
            Inflater decompresser = new Inflater();
            decompresser.setInput(input, 0, input.length);
            byte[] result = new byte[input.length * 10];
            int resultLength = decompresser.inflate(result);
            decompresser.end();

            return new String(result, 0, resultLength, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
*/
}
