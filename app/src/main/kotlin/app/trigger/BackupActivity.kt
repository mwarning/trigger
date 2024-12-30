package app.trigger

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.app.AlertDialog
import org.json.JSONObject
import android.net.Uri
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
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

        importButton = findViewById(R.id.ImportButton)
        exportButton = findViewById(R.id.ExportButton)

        importButton.setOnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            importFileLauncher.launch(intent)
        }

        exportButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_TITLE, "trigger-backup.json")
            intent.type = "application/json"
            exportFileLauncher.launch(intent)
        }
    }

    private var importFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            val uri = intent.data ?: return@registerForActivityResult
            importBackup(uri)
        }
    }

    private var exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            val uri: Uri = intent.data ?: return@registerForActivityResult
            exportBackup(uri)
        }
    }

    private fun exportBackup(uri: Uri) {
        try {
            val obj = JSONObject()
            var count = 0
            for (door in Settings.doors) {
                val json_obj = Settings.toJsonObject(door)
                json_obj!!.remove("id")
                obj.put(door.name, json_obj)
                count += 1
            }
            Utils.writeFile(this, uri, obj.toString().toByteArray())
            Toast.makeText(this, "Exported $count entries.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            showErrorMessage("Error", e.toString())
        }
    }

    private fun importBackup(uri: Uri) {
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
                obj.put("id", Settings.getNewDoorIdentifier())
                val door = Settings.fromJsonObject(obj)
                if (door != null) {
                    Settings.addDoor(door)
                }
                count += 1
            }
            Toast.makeText(this, "Imported $count doors", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            showErrorMessage("Error", e.toString())
        }
    }
}
