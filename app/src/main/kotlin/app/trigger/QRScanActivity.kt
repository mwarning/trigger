package app.trigger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import android.widget.Toast
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import android.os.Build
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import android.content.pm.PackageManager
import org.json.JSONException
import app.trigger.ssh.SshTools
import com.journeyapps.barcodescanner.BarcodeResult
import com.google.zxing.ResultPoint
import com.google.zxing.BarcodeFormat;
import android.view.View
import java.lang.Exception
import java.net.URI


class QRScanActivity : AppCompatActivity(), BarcodeCallback {
    private lateinit var barcodeView: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)
        barcodeView = findViewById(R.id.barcodeScannerView)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            if (Utils.hasCameraPermission(this)) {
                startScan()
            } else {
                Utils.requestCameraPermission(this, CAMERA_REQUEST_CODE)
            }
        } else {
            startScan()
        }
    }

    private fun startScan() {
        val formats: Collection<BarcodeFormat> = listOf(BarcodeFormat.QR_CODE)
        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
        barcodeView.initializeFromIntent(intent)
        barcodeView.decodeContinuous(this)
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    fun pause(view: View?) {
        barcodeView.pause()
    }

    fun resume(view: View?) {
        barcodeView.resume()
    }

    fun triggerScan(view: View?) {
        barcodeView.decodeSingle(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan()
            } else {
                Toast.makeText(this, "Camera permissions required for QR code scan.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun decodeSetup(data: String): JSONObject {
        try {
            val kp = SshTools.parsePrivateKeyPEM(data)
            if (kp != null) {
                val obj = JSONObject()
                obj.put("type", SshDoorSetup.TYPE)
                obj.put("name", Settings.getNewName("SSH Door"))
                obj.put("keypair", SshTools.serializeKeyPair(kp))
                return obj
            } else {
                // assume raw link
                val uri = URI(data.trim { it <= ' ' })
                val scheme = uri.scheme
                val domain = uri.host
                val path = uri.path
                val query = uri.query
                val port = uri.port
                when (scheme) {
                    "http", "https" -> {
                        val http_server = domain + if (port > 0) ":$port" else ""
                        val obj = JSONObject()
                        obj.put("type", HttpsDoorSetup.TYPE)
                        obj.put("name", Settings.getNewName(domain))
                        obj.put("open_query", data)
                        return obj
                    }
                    "mqtt", "mqtts" -> {
                        val mqtt_server = scheme + "://" + domain + if (port > 0) ":$port" else ""
                        val obj = JSONObject()
                        obj.put("type", MqttDoorSetup.TYPE)
                        obj.put("name", Settings.getNewName(domain))
                        obj.put("server", mqtt_server)
                        obj.put("command_topic", path)
                        obj.put("open_command", query)
                        return obj
                    }
                    "ssh" -> {
                        val obj = JSONObject()
                        obj.put("type", SshDoorSetup.TYPE)
                        obj.put("name", Settings.getNewName(domain))
                        obj.put("host", domain)
                        obj.put("port", port)
                        obj.put("open_command", query)
                        return obj
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            // continue
        }

        // assume json data, throws exception otherwise
        return JSONObject(data)
    }

    override fun barcodeResult(result: BarcodeResult) {
        try {
            val obj = decodeSetup(result.text)
            // give entry a new id
            obj.put("id", Settings.getNewID())
            val setup = Settings.fromJsonObject(obj)
            if (setup != null) {
                Settings.addSetup(setup)
                Toast.makeText(this, "Added ${setup.name}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show()
            }
        } catch (e: JSONException) {
            Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show()
        } catch (e: IllegalAccessException) {
            Toast.makeText(this, "Incompatible QR Code", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}

    companion object {
        private const val TAG = "QRScanActivity"
        private const val CAMERA_REQUEST_CODE = 0x01
    }
}
