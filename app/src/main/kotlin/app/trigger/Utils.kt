package app.trigger

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.Html
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.trigger.DoorReply.ReplyCode
import app.trigger.DoorStatus.StateCode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.cert.Certificate
import java.util.UUID
import java.util.regex.Pattern
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.math.absoluteValue

//import java.util.zip.Deflater;
//import java.util.zip.Inflater;

object Utils {
    fun hasCameraPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(
                activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun hasBluetoothConnectPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun readInputStreamWithTimeout(iStream: InputStream, maxLength: Int, timeoutMillis: Int): String {
        val buffer = ByteArray(maxLength)
        var bufferOffset = 0
        val maxTimeMillis = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < maxTimeMillis && bufferOffset < buffer.size) {
            val readLength = Math.min(iStream.available(), buffer.size - bufferOffset)
            // can alternatively use bufferedReader, guarded by isReady():
            val readResult = iStream.read(buffer, bufferOffset, readLength)
            if (readResult == -1) {
                break
            }
            bufferOffset += readResult
        }
        val ret = ByteArray(bufferOffset)
        System.arraycopy(buffer, 0, ret, 0, bufferOffset)
        return String(ret)
    }

    fun getSocketFactoryWithCertificate(cert: Certificate?): SSLSocketFactory {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("ca", cert)
        val tmf = TrustManagerFactory.getInstance("X509")
        tmf.init(keyStore)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, tmf.trustManagers, null)
        return sslContext.socketFactory
    }

    fun getSocketFactoryWithCertificateAndClientKey(cert: Certificate?, clientCertificate: Certificate, privateKey: PrivateKey?): SSLSocketFactory {
        val TEMPORARY_KEY_PASSWORD = UUID.randomUUID().toString().replace("-", "")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("ca", cert)
        keyStore.setCertificateEntry("client-cert", clientCertificate)
        keyStore.setKeyEntry("client-key", privateKey, TEMPORARY_KEY_PASSWORD.toCharArray(), arrayOf(clientCertificate))
        val kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm())
        try {
            kmf.init(keyStore, TEMPORARY_KEY_PASSWORD.toCharArray())
        } catch (e: UnrecoverableKeyException) {
            e.printStackTrace()
        }
        val tmf = TrustManagerFactory.getInstance("X509")
        tmf.init(keyStore)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, null)
        return sslContext.socketFactory
    }

    private fun getFileSize(ctx: Context, uri: Uri?): Long {
        val cursor = ctx.contentResolver.query(uri!!, null, null, null, null)
        cursor!!.moveToFirst()
        val size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE).coerceAtLeast(0))
        cursor.close()
        return size
    }

    fun readFile(ctx: Context, uri: Uri): ByteArray {
        val size = getFileSize(ctx, uri).toInt()
        val iStream = ctx.contentResolver.openInputStream(uri)
        val buffer = ByteArrayOutputStream()
        var nRead: Int
        val data = ByteArray(size)
        while (iStream!!.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        iStream.close()
        return data
    }

    fun writeFile(ctx: Context, uri: Uri, data: ByteArray) {
        val fos = ctx.contentResolver.openOutputStream(uri)
        fos!!.write(data)
        fos.close()
    }

    fun createSocketAddress(addr: String): InetSocketAddress {
        val has_scheme = addr.contains("://")
        val scheme_addr: String

        // parsing only works with a scheme
        scheme_addr = if (has_scheme) {
            addr
        } else {
            "xyz://$addr"
        }
        val uri = URI(scheme_addr)
        return InetSocketAddress(uri.host, uri.port)
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
    fun rebuildAddress(addr: String, default_port: Int): String {
        val has_scheme = addr.contains("://")
        val scheme_addr: String

        // parsing only works with a scheme
        scheme_addr = if (has_scheme) {
            addr
        } else {
            "xyz://$addr"
        }
        val uri = URI(scheme_addr)
        val host = uri.host
        var port = uri.port
        if (port < 0) {
            port = default_port
        }
        if (host == null) {
            throw URISyntaxException(uri.toString(), "URI is invalid: $addr")
        }
        return if (has_scheme) {
            if (port < 0) {
                uri.scheme + "://" + host
            } else {
                uri.scheme + "://" + host + ":" + port
            }
        } else {
            if (port < 0) {
                host
            } else {
                "$host:$port"
            }
        }
    }

    fun serializeBitmap(image: Bitmap?): String? {
        if (image == null) {
            //Log.d("Utils", "serializeBitmap returns empty string");
            return ""
        }
        try {
            val byteStream = ByteArrayOutputStream()
            val success = image.compress(Bitmap.CompressFormat.PNG, 0, byteStream)
            return if (success) {
                Base64.encodeToString(byteStream.toByteArray(), 0)
            } else {
                throw Exception("compress failed")
            }
        } catch (e: Exception) {
            Log.e("Utils", "serializeBitmap: $e")
        }
        return null
    }

    fun deserializeBitmap(str: String?): Bitmap? {
        if (str == null || str.length == 0) {
            return null
        }
        try {
            val data = Base64.decode(str, 0)
            val image = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (image == null) {
                Log.d("Utils", "deserializeBitmap returns null despite string input")
            }
            return image
        } catch (e: Exception) {
            Log.e("Utils", "deserializeBitmap: $e")
        }
        return null
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
    fun isEmpty(str: String?): Boolean {
        return str == null || str.isEmpty()
    }

    fun hexStringToByteArray(s: String?): ByteArray {
        if (s == null) {
            return ByteArray(0)
        }

        return s.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    fun arrayIndexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.size > haystack.size) {
            return -1
        }
        var i = 0
        while (i <= haystack.size - needle.size) {
            var found = true
            var j = 0
            while (j < needle.size) {
                if (haystack[i + j] != needle[j]) {
                    found = false
                    break
                }
                j += 1
            }
            if (found) {
                return i
            }
            i += 1
        }
        return -1
    }

    fun byteArrayToHexString(bytes: ByteArray?): String {
        if (bytes == null) {
            return ""
        } else {
            return bytes.joinToString(separator = "") { eachByte -> "%02X".format(eachByte) }
        }
    }

    private fun match(message: String, pattern: String): Boolean {
        val r = Pattern.compile(pattern)
        val m = r.matcher(message)
        return m.find()
    }

    // parse return from HTTP/SSH/MQTT doors
    fun genericDoorReplyParser(reply: DoorReply, unlocked_pattern_in: String?, locked_pattern_in: String?): DoorStatus {
        // strip HTML from response
        var unlocked_pattern = unlocked_pattern_in
        var locked_pattern = locked_pattern_in
        val msg = Html.fromHtml(reply.message).toString().trim { it <= ' ' }
        if (unlocked_pattern == null) {
            unlocked_pattern = ""
        }
        if (locked_pattern == null) {
            locked_pattern = ""
        }
        return when (reply.code) {
            ReplyCode.LOCAL_ERROR, ReplyCode.REMOTE_ERROR -> DoorStatus(StateCode.UNKNOWN, msg)
            ReplyCode.SUCCESS -> {
                return try {
                    if (match(reply.message, unlocked_pattern)) {
                        // door unlocked
                        DoorStatus(StateCode.OPEN, msg)
                    } else if (match(reply.message, locked_pattern)) {
                        // door locked
                        DoorStatus(StateCode.CLOSED, msg)
                    } else {
                        DoorStatus(StateCode.UNKNOWN, msg)
                    }
                } catch (e: Exception) {
                    DoorStatus(StateCode.UNKNOWN, e.toString())
                }
            }
            ReplyCode.DISABLED -> DoorStatus(StateCode.DISABLED, msg)
        }
    }
}
