package app.trigger

import app.trigger.Utils.writeFile
import app.trigger.Utils.readFile
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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


abstract class AbstractClientKeyPairActivity : AppCompatActivity() {
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

    abstract fun getKeyPair(): KeyPairBean?
    abstract fun setKeyPair(keyPair: KeyPairBean?)

    protected fun showMessage(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    protected fun showErrorMessageDialog(title: String, message: String?) {
        builder.setTitle(title)
        builder.setMessage(message ?: "")
        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_abstract_client_keypair)

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
        keypair = getKeyPair()

        // toggle between both checkboxes
        useClipboardCheckBox.setOnClickListener { useFilesystemCheckBox.isChecked = !useClipboardCheckBox.isChecked }

        // toggle between both checkboxes
        useFilesystemCheckBox.setOnClickListener { useClipboardCheckBox.isChecked = !useFilesystemCheckBox.isChecked }

        exportPrivateKeyButton.setOnClickListener {
            if (keypair == null) {
                showErrorMessageDialog("No Key", "No key loaded to export.")
            } else if (useClipboardCheckBox.isChecked) {
                val privateKey = keypair!!.openSSHPrivateKey
                val clip = ClipData.newPlainText(keypair!!.description, privateKey)
                clipboard.setPrimaryClip(clip)
                showMessage("Done.")
            } else {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
                intent.type = "*/*"
                startActivityForResult(intent, EXPORT_PRIVATE_KEY_CODE)
            }
        }

        importPrivateKeyButton.setOnClickListener {
            if (useClipboardCheckBox.isChecked) {
                if (clipboard.hasPrimaryClip()) {
                    val privateKey = clipboard.primaryClip!!.getItemAt(0).text.toString()
                    val kp = SshTools.parsePrivateKeyPEM(privateKey)
                    if (kp != null) {
                        showMessage("Done")
                        updateKeyInfo(kp)
                    } else {
                        showMessage("Import Failed.")
                    }
                } else {
                    showMessage("Clipboard is empty.")
                }
            } else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
                intent.type = "*/*"
                startActivityForResult(intent, IMPORT_PRIVATE_KEY_CODE)
            }
        }

        okButton.setOnClickListener {
            // update the SwitchPreference
            setKeyPair(keypair)
            //mqttDoorSetup.client_keypair = keypair
            //preference!!.setKeyPair(keypair)
            finish()
        }

        deleteButton.setOnClickListener {
            builder.setTitle("Confirm")
            builder.setMessage("Really remove client private key?")
            builder.setCancelable(false) // not necessary
            builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, _: Int ->
                //mqttDoorSetup.client_keypair = null
                setKeyPair(null)
                updateKeyInfo(null)
                dialog.cancel()
            }
            builder.setNegativeButton(R.string.no) { dialog: DialogInterface, _: Int -> dialog.cancel() }

            // create dialog box
            val alert = builder.create()
            alert.show()
        }

        cancelButton.setOnClickListener {
            // persist your value here
            finish()
        }

        updateKeyInfo(keypair)
    }

    private fun exportPrivateKey(uri: Uri) {
        val kp = keypair

        if (kp == null) {
            showErrorMessageDialog("No Key", "No key loaded to export.")
        } else {
            try {
                writeFile(this, uri, kp.openSSHPrivateKey!!.toByteArray())
                showMessage("Done. Wrote private key: ${uri.lastPathSegment}")
            } catch (e: Exception) {
                showErrorMessageDialog("Error", e.message)
            }
        }
    }

    private fun importPrivateKey(uri: Uri) {
        try {
            val privateKeyPEM = String(readFile(this, uri))
            val kp = SshTools.parsePrivateKeyPEM(privateKeyPEM)
                    ?: throw Exception("Not a valid key!")
            updateKeyInfo(kp)
            showMessage("Done. Read ${uri.lastPathSegment}")
        } catch (e: Exception) {
            showErrorMessageDialog("Error", e.message)
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
            privateKey.text = keypair!!.openSSHPublicKey
            tv.text = keypair!!.description
            tv.text = res.getString(R.string.private_key, keypair!!.description)
        }
    }

    companion object {
        private const val IMPORT_PRIVATE_KEY_CODE = 0x01
        private const val EXPORT_PRIVATE_KEY_CODE = 0x03
    }
}