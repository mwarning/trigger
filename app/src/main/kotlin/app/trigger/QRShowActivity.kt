package app.trigger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.WriterException
import android.widget.*
import com.google.zxing.EncodeHintType
import java.lang.Exception
import java.util.ArrayList

class QRShowActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrshow)
        val door_id = intent.getIntExtra("door_id", -1)
        val setup = Settings.getDoor(door_id)
        if (setup != null) {
            title = "$title: ${setup.name}"
            try {
                generateQR(setup)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Setup not found.", Toast.LENGTH_LONG).show()
        }
    }

    private fun getJsonKeys(obj: JSONObject): ArrayList<String> {
        val keys = ArrayList<String>()
        val it = obj.keys()
        while (it.hasNext()) {
            keys.add(it.next())
        }
        return keys
    }

    private fun encodeSetup(obj: JSONObject): String {
        // do not export internal id
        obj.remove("id")

        // remove empty strings, images and null values
        val keys = getJsonKeys(obj)
        for (key in keys) {
            val value = obj.opt(key)
            if (value == null) {
                obj.remove(key)
            } else if (key.endsWith("_image")) {
                obj.remove(key)
            } else if (value is String) {
                if (value.length == 0) {
                    obj.remove(key)
                }
            }
        }
        return obj.toString()
    }

    private fun generateQR(door: Door) {
        val multiFormatWriter = MultiFormatWriter()
        var data_length = 0
        try {
            val obj = Settings.toJsonObject(door) ?: throw Exception("Failed to convert setup to JSON")
            val data = encodeSetup(obj)
            data_length = data.length

            // data has to be a string
            val hints =  mapOf(EncodeHintType.CHARACTER_SET to "utf-8")
            val bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080, hints)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.createBitmap(bitMatrix)
            findViewById<ImageView>(R.id.QRView).setImageBitmap(bitmap)
        } catch (e: WriterException) {
            Toast.makeText(this, "${e.message} ($data_length Bytes)", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    companion object {
        private const val TAG = "QRShowActivity"
    }
}
