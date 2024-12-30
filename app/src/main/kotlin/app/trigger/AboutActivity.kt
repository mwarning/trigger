package app.trigger

import android.os.Bundle
import android.widget.TextView
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity


class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        title = getString(R.string.menu_about)

        findViewById<TextView>(R.id.versionTv).text = if (BuildConfig.DEBUG) {
            BuildConfig.VERSION_NAME + " (debug)"
        } else {
            BuildConfig.VERSION_NAME
        }

        findViewById<TextView>(R.id.licenseTV).setOnClickListener {
            val intent = Intent(this, LicenseActivity::class.java)
            startActivity(intent)
        }
    }
}
