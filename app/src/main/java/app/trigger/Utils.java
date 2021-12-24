package app.trigger;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.KeyManagerFactory;
//import java.util.zip.Deflater;
//import java.util.zip.Inflater;


public class Utils {
    public static boolean hasFineLocationPermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    public static boolean hasCameraPermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
    }

    public static void requestFineLocationPermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION}, request_code);
    }

    public static void requestCameraPermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.CAMERA}, request_code);
    }

    public static boolean allGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static String readInputStreamWithTimeout(InputStream is, int maxLength, int timeoutMillis)
            throws IOException  {
        byte[] buffer = new byte[maxLength];
        int bufferOffset = 0;
        long maxTimeMillis = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < maxTimeMillis && bufferOffset < buffer.length) {
            int readLength = java.lang.Math.min(is.available(), buffer.length - bufferOffset);
            // can alternatively use bufferedReader, guarded by isReady():
            int readResult = is.read(buffer, bufferOffset, readLength);
            if (readResult == -1) {
                break;
            }
            bufferOffset += readResult;
        }

        byte[] ret = new byte[bufferOffset];
        System.arraycopy(buffer, 0, ret, 0, bufferOffset);
        return new String(ret);
    }

    public static SSLSocketFactory getSocketFactoryWithCertificate(Certificate cert)
            throws CertificateException, KeyStoreException, IOException,
            NoSuchAlgorithmException, KeyManagementException {

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", cert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }

    public static SSLSocketFactory getSocketFactoryWithCertificateAndClientKey(Certificate cert, Certificate clientCertificate, PrivateKey privateKey)
            throws CertificateException, KeyStoreException, IOException,
            NoSuchAlgorithmException, KeyManagementException {

        String TEMPORARY_KEY_PASSWORD = UUID.randomUUID().toString().replace("-","");

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", cert);
        keyStore.setCertificateEntry("client-cert", clientCertificate);
        keyStore.setKeyEntry("client-key", privateKey, TEMPORARY_KEY_PASSWORD.toCharArray(), new Certificate[]{clientCertificate});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        try {
            kmf.init(keyStore, TEMPORARY_KEY_PASSWORD.toCharArray());
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }

    public static long getFileSize(Context ctx, Uri uri) {
        Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        long size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
        cursor.close();
        return size;
    }

    public static byte[] readFile(Context ctx, Uri uri) throws IOException {
        int size = (int) getFileSize(ctx, uri);
        InputStream is = ctx.getContentResolver().openInputStream(uri);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[size];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        is.close();
        return data;
    }

    public static void writeFile(Context ctx, Uri uri, byte[] data) throws IOException {
        OutputStream fos = ctx.getContentResolver().openOutputStream(uri);
        fos.write(data);
        fos.close();
    }

    public static InetSocketAddress createSocketAddress(String addr)
            throws URISyntaxException {
        final boolean has_scheme = addr.contains("://");
        String scheme_addr;

        // parsing only works with a scheme
        if (has_scheme) {
            scheme_addr = addr;
        } else {
            scheme_addr = "xyz://" + addr;
        }

        URI uri = new URI(scheme_addr);
        return new InetSocketAddress(uri.getHost(), uri.getPort());
    }

    /*
     * parses the following formats:
     * "1.2.3.4"
     * "1.2.3.4:80"
     * "::1"
     * "[::1]:80"
     * "example.com"
     * "example.com:443"
     *
     * Schemas like "https://" etc. are preserved.
     */
    public static String rebuildAddress(String addr, int default_port)
            throws URISyntaxException {
        final boolean has_scheme = addr.contains("://");
        String scheme_addr;

        // parsing only works with a scheme
        if (has_scheme) {
            scheme_addr = addr;
        } else {
            scheme_addr = "xyz://" + addr;
        }

        URI uri = new URI(scheme_addr);
        String host = uri.getHost();
        int port = uri.getPort();

        if (port < 0) {
            port = default_port;
        }

        if (host == null) {
            throw new URISyntaxException(uri.toString(), "URI is invalid: " + addr);
        }

        if (has_scheme) {
            if (port < 0) {
                return uri.getScheme() + "://" + host;
            } else {
                return uri.getScheme() + "://" + host + ":" + port;
            }
        } else {
            if (port < 0) {
                return host;
            } else {
                return host + ":" + port;
            }
        }
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

    // non empty string
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static byte[] hexStringToByteArray(String s) {
        if (s == null) {
            return new byte[0];
        }
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            try {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                        + Character.digit(s.charAt(i + 1), 16));
            } catch (Exception e) {
                Log.e("hexStringtoArray", "i: " + i);
                throw e;
            }
        }
        return data;
    }

    public static int arrayIndexOf(byte[] haystack, byte[] needle) {
        if (needle.length > haystack.length) {
            return -1;
        }

        for (int i = 0; i <= (haystack.length - needle.length); i += 1) {
            boolean found = true;
            for (int j = 0; j < needle.length; j += 1) {
               if (haystack[i+j] != needle[j]) {
                   found = false;
                   break;
               }
            }
            if (found) {
                return i;
            }
        }

        return -1;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String byteArrayToHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static boolean match(String message, String pattern) {
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(message);
        return m.find();
    }

    // parse return from HTTP/SSH/MQTT doors
    public static DoorState genericDoorReplyParser(DoorReply reply, String unlocked_pattern, String locked_pattern) {
        // strip HTML from response
        String msg = android.text.Html.fromHtml(reply.message).toString().trim();

        if (unlocked_pattern == null) {
            unlocked_pattern = "";
        }

        if (locked_pattern == null) {
            locked_pattern = "";
        }

        switch (reply.code) {
            case LOCAL_ERROR:
            case REMOTE_ERROR:
                return new DoorState(DoorState.StateCode.UNKNOWN, msg);
            case SUCCESS:
                try {
                    if (match(reply.message, unlocked_pattern)) {
                        // door unlocked
                        return new DoorState(DoorState.StateCode.OPEN, msg);
                    } else if (match(reply.message, locked_pattern)) {
                        // door locked
                        return new DoorState(DoorState.StateCode.CLOSED, msg);
                    } else {
                        return new DoorState(DoorState.StateCode.UNKNOWN, msg);
                    }
                } catch (Exception e) {
                    return new DoorState(DoorState.StateCode.UNKNOWN, e.toString());
                }
            case DISABLED:
            default:
                return new DoorState(DoorState.StateCode.DISABLED, msg);
        }
    }
}
