package app.trigger

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class LicenseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        title = getString(R.string.title_license)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // reading the license file can be slow => use a thread
        Thread {
            try {
                val buffer = StringBuffer()
                val reader = BufferedReader(InputStreamReader(assets.open("license.txt")))
                while (true) {
                    val line = reader.readLine()
                    if (line != null) {
                        if (line.trim().isEmpty()){
                            buffer.append("\n")
                        } else {
                            buffer.append(line + "\n")
                        }
                    } else {
                        break
                    }
                }
                reader.close()
                runOnUiThread {
                    findViewById<ProgressBar>(R.id.licenseLoadingBar).visibility = View.GONE
                    findViewById<TextView>(R.id.licenceText).text = buffer.toString()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }
}