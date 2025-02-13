package app.trigger

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import app.trigger.ssh.SshTools
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import org.json.JSONException
import org.json.JSONObject
import java.net.URI

class QRScanActivity : AppCompatActivity(), BarcodeCallback {
    private lateinit var barcodeView: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        barcodeView = findViewById(R.id.barcodeScannerView)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            if (Utils.hasCameraPermission(this)) {
                startScan()
            } else {
                enabledCameraForResult.launch(Manifest.permission.CAMERA)
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
        if (Utils.hasCameraPermission(this)) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Utils.hasCameraPermission(this)) {
            barcodeView.pause()
        }
    }

    private val enabledCameraForResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            startScan()
        } else {
            Toast.makeText(this, R.string.missing_camera_permission, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun decodeSetup(data: String): JSONObject {
        try {
            val kp = SshTools.parsePrivateKeyPEM(data)
            if (kp != null) {
                val obj = JSONObject()
                obj.put("type", SshDoor.TYPE)
                obj.put("name", Settings.getNewDoorName("SSH Door"))
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
                        obj.put("type", HttpsDoor.TYPE)
                        obj.put("name", Settings.getNewDoorName(domain))
                        obj.put("open_query", data)
                        return obj
                    }
                    "mqtt", "mqtts" -> {
                        val mqtt_server = scheme + "://" + domain + if (port > 0) ":$port" else ""
                        val obj = JSONObject()
                        obj.put("type", MqttDoor.TYPE)
                        obj.put("name", Settings.getNewDoorName(domain))
                        obj.put("server", mqtt_server)
                        obj.put("command_topic", path)
                        obj.put("open_command", query)
                        return obj
                    }
                    "ssh" -> {
                        val obj = JSONObject()
                        obj.put("type", SshDoor.TYPE)
                        obj.put("name", Settings.getNewDoorName(domain))
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
            obj.put("id", Settings.getNewDoorIdentifier())
            val setup = Settings.fromJsonObject(obj)
            if (setup != null) {
                Settings.storeDoorSetup(setup)
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
    }
}
