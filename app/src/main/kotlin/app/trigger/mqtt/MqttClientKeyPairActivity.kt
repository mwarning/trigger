package app.trigger.mqtt

import app.trigger.Utils.writeFile
import app.trigger.Utils.readFile
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import app.trigger.R
import android.content.Intent
import android.content.DialogInterface
import app.trigger.ssh.KeyPairBean
import android.content.ClipData
import app.trigger.ssh.SshTools
import android.content.ClipboardManager
import android.net.Uri
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.lang.Exception


class MqttClientKeyPairActivity : AppCompatActivity() {
    private var preference : MqttClientKeyPairPreference? = null // hack
    private lateinit var builder: AlertDialog.Builder
    private lateinit var clipboard: ClipboardManager
    private lateinit var importPrivateKeyButton: Button
    private lateinit var exportPrivateKeyButton: Button
    private lateinit var useClipboardCheckBox: CheckBox
    private lateinit var useFilesystemCheckBox: CheckBox
    private lateinit var cancelButton: Button
    private lateinit var okButton: Button
    private lateinit var deleteButton: Button
    private lateinit var privateKey: TextView
    private var keypair : KeyPairBean? = null // only contains private key here

    private fun showErrorMessage(title: String, message: String?) {
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mqtt_client_keypair)
        preference = MqttClientKeyPairPreference.self // hack, TODO: pass serialized key in bundle
        keypair = preference!!.getKeyPair()
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        builder = AlertDialog.Builder(this)
        importPrivateKeyButton = findViewById(R.id.ImportPrivateKeyButton)
        exportPrivateKeyButton = findViewById(R.id.ExportPrivateKeyButton)
        useClipboardCheckBox = findViewById(R.id.UseClipboardCheckBox)
        useFilesystemCheckBox = findViewById(R.id.UseFilesystemCheckBox)
        cancelButton = findViewById(R.id.CancelButton)
        okButton = findViewById(R.id.OkButton)
        deleteButton = findViewById(R.id.DeleteButton)
        privateKey = findViewById(R.id.PrivateKey)

        // toggle between both checkboxes
        useClipboardCheckBox.setOnClickListener(View.OnClickListener { v: View? -> useFilesystemCheckBox.setChecked(!useClipboardCheckBox.isChecked()) })

        // toggle between both checkboxes
        useFilesystemCheckBox.setOnClickListener(View.OnClickListener { v: View? -> useClipboardCheckBox.setChecked(!useFilesystemCheckBox.isChecked()) })

        exportPrivateKeyButton.setOnClickListener(View.OnClickListener { v: View? ->
            if (keypair == null) {
                showErrorMessage("No Key", "No key loaded to export.")
            } else if (useClipboardCheckBox.isChecked()) {
                val privateKey = keypair!!.openSSHPrivateKey
                val clip = ClipData.newPlainText(keypair!!.description, privateKey)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Done.", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
                intent.type = "*/*"
                startActivityForResult(intent, EXPORT_PRIVATE_KEY_CODE)
            }
        })

        importPrivateKeyButton.setOnClickListener(View.OnClickListener { v: View? ->
            if (useClipboardCheckBox.isChecked()) {
                if (clipboard.hasPrimaryClip()) {
                    val privateKey = clipboard.primaryClip!!.getItemAt(0).text.toString()
                    val kp = SshTools.parsePrivateKeyPEM(privateKey)
                    if (kp != null) {
                        Toast.makeText(applicationContext, "Done.", Toast.LENGTH_SHORT).show()
                        updateKeyInfo(kp)
                    } else {
                        Toast.makeText(applicationContext, "Import Failed.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
                }
            } else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
                intent.type = "*/*"
                startActivityForResult(intent, IMPORT_PRIVATE_KEY_CODE)
            }
        })

        okButton.setOnClickListener(View.OnClickListener { v: View? ->
            // update the SwitchPreference
            preference!!.setKeyPair(keypair)
            finish()
        })

        deleteButton.setOnClickListener(View.OnClickListener { v: View? ->
            builder.setTitle("Confirm")
            builder.setMessage("Really remove client private key?")
            builder.setCancelable(false) // not necessary
            builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, id: Int ->
                keypair = null
                updateKeyInfo(null)
                dialog.cancel()
            }
            builder.setNegativeButton(R.string.no) { dialog: DialogInterface, id: Int -> dialog.cancel() }

            // create dialog box
            val alert = builder.create()
            alert.show()
        })

        cancelButton.setOnClickListener(View.OnClickListener { v: View? ->
            // persist your value here
            finish()
        })

        updateKeyInfo(keypair)
    }

    private fun exportPrivateKey(uri: Uri) {
        val kp = keypair

        if (kp == null) {
            showErrorMessage("No Key", "No key loaded to export.")
        } else {
            try {
                writeFile(this, uri, kp.openSSHPrivateKey!!.toByteArray())
                Toast.makeText(applicationContext, "Done. Wrote private key: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showErrorMessage("Error", e.message)
            }
        }
    }

    private fun importPrivateKey(uri: Uri) {
        try {
            val privateKeyPEM = String(readFile(this, uri))
            val kp = SshTools.parsePrivateKeyPEM(privateKeyPEM)
                    ?: throw Exception("Not a valid key!")
            updateKeyInfo(kp)
            Toast.makeText(applicationContext, "Done. Read ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage("Error", e.message)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) {
            return
        }

        val d = data?.data
        if (d != null) {
            when (requestCode) {
                EXPORT_PRIVATE_KEY_CODE -> exportPrivateKey(d)
                IMPORT_PRIVATE_KEY_CODE -> importPrivateKey(d)
            }
        }
    }

    private fun updateKeyInfo(kp: KeyPairBean?) {
        keypair = kp
        val res = resources
        val tv = findViewById<TextView>(R.id.PrivateKeyTextView)
        if (keypair == null) {
            deleteButton.isEnabled = false
            exportPrivateKeyButton.isEnabled = false
            privateKey.text = "<no key loaded>"
            tv.text = res.getString(R.string.private_key, "")
        } else {
            deleteButton.isEnabled = true
            exportPrivateKeyButton.isEnabled = true
            privateKey.setText(keypair!!.openSSHPublicKey)
            tv.text = keypair!!.description
            tv.text = res.getString(R.string.private_key, keypair!!.description)
        }
    }

    companion object {
        private const val IMPORT_PRIVATE_KEY_CODE = 0x01
        private const val EXPORT_PRIVATE_KEY_CODE = 0x03
    }
}