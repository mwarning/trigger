package app.trigger

import android.preference.PreferenceActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.preference.Preference
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.app.AlertDialog
import org.json.JSONObject
import android.preference.SwitchPreference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference.OnPreferenceClickListener
import android.net.NetworkInfo
import android.net.NetworkInfo.DetailedState
import android.preference.PreferenceGroup
import android.preference.PreferenceCategory
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.CheckBoxPreference
import android.net.Uri
import android.view.View
import android.widget.*
import java.lang.Exception


class BackupActivity : AppCompatActivity() {
    private lateinit var builder: AlertDialog.Builder
    private lateinit var exportButton: Button
    private lateinit var importButton: Button

    private fun showErrorMessage(title: String, message: String) {
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)
        builder = AlertDialog.Builder(this)
        importButton = findViewById<Button>(R.id.ImportButton)
        exportButton = findViewById<Button>(R.id.ExportButton)
        importButton.setOnClickListener(View.OnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            startActivityForResult(intent, READ_REQUEST_CODE)
        })
        exportButton.setOnClickListener(View.OnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_TITLE, "trigger-backup.json")
            intent.type = "application/json"
            startActivityForResult(intent, WRITE_REQUEST_CODE)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            return
        }
        if (data == null || data.data == null) {
            return
        }
        when (requestCode) {
            READ_REQUEST_CODE -> importSetups(data.data)
            WRITE_REQUEST_CODE -> exportSetups(data.data)
        }
    }

    private fun exportSetups(uri: Uri?) {
        try {
            val obj = JSONObject()
            var count = 0
            for (setup in Settings.setups) {
                val json_obj = Settings.toJsonObject(setup)
                json_obj!!.remove("id")
                obj.put(setup.name, json_obj)
                count += 1
            }
            Utils.writeFile(this, uri, obj.toString().toByteArray())
            Toast.makeText(this, "Exported $count entries.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            showErrorMessage("Error", e.toString())
        }
    }

    private fun importSetups(uri: Uri?) {
        try {
            val data = Utils.readFile(this, uri)
            val json_data = JSONObject(
                    String(data, 0, data.size)
            )
            var count = 0
            val keys = json_data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = json_data.getJSONObject(key)
                obj.put("id", Settings.getNewID())
                val setup = Settings.fromJsonObject(obj)
                Settings.addSetup(setup)
                count += 1
            }
            Toast.makeText(this, "Imported setups: $count", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            showErrorMessage("Error", e.toString())
        }
    }

    companion object {
        private const val READ_REQUEST_CODE = 0x01
        private const val WRITE_REQUEST_CODE = 0x02
    }
}
