package app.trigger.ssh

import app.trigger.Utils.writeFile
import app.trigger.Utils.readFile
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import app.trigger.R
import android.content.Intent
import android.content.DialogInterface
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import app.trigger.Log
import java.lang.Exception

class SshKeyPairActivity : AppCompatActivity(), RegisterIdentityTask.OnTaskCompleted, GenerateIdentityTask.OnTaskCompleted {
    private var preference: SshKeyPairPreference? = null // hack
    private lateinit var builder: AlertDialog.Builder
    private lateinit var clipboard: ClipboardManager
    private lateinit var createButton: Button
    private lateinit var importPrivateKeyButton: Button
    private lateinit var exportPublicKeyButton: Button
    private lateinit var exportPrivateKeyButton: Button
    private lateinit var useClipboardCheckBox: CheckBox
    private lateinit var useFilesystemCheckBox: CheckBox
    private lateinit var cancelButton: Button
    private lateinit var registerButton: Button
    private lateinit var okButton: Button
    private lateinit var deleteButton: Button
    private lateinit var publicKey: TextView
    private lateinit var registerAddress: EditText
    private lateinit var keyTypeSpinner: Spinner
    private var keypair: KeyPairBean? = null
    private var keyGenInProgress = false
    private fun showErrorMessage(title: String, message: String?) {
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keypair)
        preference = SshKeyPairPreference.self // hack, TODO: pass serialized key in bundle
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        builder = AlertDialog.Builder(this)
        createButton = findViewById(R.id.CreateButton)
        importPrivateKeyButton = findViewById(R.id.ImportPrivateKeyButton)
        exportPublicKeyButton = findViewById(R.id.ExportPublicKeyButton)
        exportPrivateKeyButton = findViewById(R.id.ExportPrivateKeyButton)
        useClipboardCheckBox = findViewById(R.id.UseClipboardCheckBox)
        useFilesystemCheckBox = findViewById(R.id.UseFilesystemCheckBox)
        cancelButton = findViewById(R.id.CancelButton)
        registerButton = findViewById(R.id.RegisterButton)
        okButton = findViewById(R.id.OkButton)
        deleteButton = findViewById(R.id.DeleteButton)
        publicKey = findViewById(R.id.PublicKey)
        registerAddress = findViewById(R.id.RegisterAddress)
        keyTypeSpinner = findViewById(R.id.KeyTypeSpinner)
        val self = this
        val dataAdapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item,
                resources.getStringArray(R.array.SshKeyTypes)
        )
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        keyTypeSpinner.setAdapter(dataAdapter)
        keyTypeSpinner.setSelection(0)
        registerAddress.setText(
                intent.getStringExtra("register_url")
        )

        // toggle between both checkboxes
        useClipboardCheckBox.setOnClickListener(View.OnClickListener { v: View? -> useFilesystemCheckBox.setChecked(!useClipboardCheckBox.isChecked()) })

        // toggle between both checkboxes
        useFilesystemCheckBox.setOnClickListener(View.OnClickListener { v: View? -> useClipboardCheckBox.setChecked(!useFilesystemCheckBox.isChecked()) })
        registerButton.setOnClickListener(View.OnClickListener { v: View? ->
            val address = registerAddress.getText().toString()
            if (address.isEmpty()) {
                showErrorMessage("Address Empty", "Address and port needed to send public key to destination.")
            } else if (keypair == null) {
                showErrorMessage("Key Pair Empty", "No public key available to register.")
            } else {
                val task = RegisterIdentityTask(self, address, keypair!!)
                task.start()
            }
        })
        createButton.setOnClickListener(View.OnClickListener { v: View? ->
            if (keyGenInProgress) {
                showErrorMessage("Busy", "Key generation already in progress. Please wait.")
            } else {
                keyGenInProgress = true
                val type = when (self.keyTypeSpinner!!.selectedItemPosition) {
                    0 -> "ED25519"
                    1 -> "ECDSA-384"
                    2 -> "ECDSA-521"
                    3 -> "RSA-2048"
                    4 -> "RSA-4096"
                    else -> {
                        Log.e(TAG, "Invalid selected item position")
                        ""
                    }
                }
                if (type.isNotEmpty()) {
                    GenerateIdentityTask(self).execute(type)
                }
            }
        })
        exportPublicKeyButton.setOnClickListener(View.OnClickListener { v: View? ->
            if (keypair == null) {
                showErrorMessage("No Key", "No key loaded to export.")
            } else if (useClipboardCheckBox.isChecked()) {
                val publicKey = keypair!!.openSSHPublicKey
                val clip = ClipData.newPlainText(keypair!!.description, publicKey)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Done.", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa.pub");
                intent.type = "*/*"
                startActivityForResult(intent, EXPORT_PUBLIC_KEY_CODE)
            }
        })
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
            // persist your value here
            preference!!.keyPair = self.keypair
            self.finish()
        })
        deleteButton.setOnClickListener(View.OnClickListener { v: View? ->
            builder.setTitle("Confirm")
            builder.setMessage("Really remove key pair?")
            builder.setCancelable(false) // not necessary
            builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, id: Int ->
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
            self.finish()
        })
        updateKeyInfo(preference!!.keyPair)
    }

    override fun onGenerateIdentityTaskCompleted(message: String?, keypair: KeyPairBean?) {
        keyGenInProgress = false
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

        // only set if successful
        keypair?.let { updateKeyInfo(it) }
    }

    override fun onRegisterIdentityTaskCompleted(message: String?) {
        runOnUiThread { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    private fun exportPublicKey(uri: Uri?) {
        if (keypair == null) {
            showErrorMessage("No Key Pair", "No key loaded to export.")
            return
        }
        try {
            writeFile(this, uri, keypair!!.openSSHPublicKey!!.toByteArray())
            Toast.makeText(applicationContext, "Done. Wrote public key: " + uri!!.lastPathSegment, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage("Error", e.message)
        }
    }

    private fun exportPrivateKey(uri: Uri?) {
        if (keypair == null) {
            showErrorMessage("No Key", "No key loaded to export.")
            return
        }
        try {
            writeFile(this, uri, keypair!!.openSSHPrivateKey!!.toByteArray())
            Toast.makeText(applicationContext, "Done. Wrote private key: " + uri!!.lastPathSegment, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage("Error", e.message)
        }
    }

    private fun importPrivateKey(uri: Uri?) {
        try {
            val privateKeyPEM = String(readFile(this, uri))
            val kp = SshTools.parsePrivateKeyPEM(privateKeyPEM)
                    ?: throw Exception("Not a valid key!")
            updateKeyInfo(kp)
            Toast.makeText(applicationContext, "Done. Read " + uri!!.lastPathSegment, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage("Error", e.message)
        }
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
            EXPORT_PUBLIC_KEY_CODE -> exportPublicKey(data.data)
            EXPORT_PRIVATE_KEY_CODE -> exportPrivateKey(data.data)
            IMPORT_PRIVATE_KEY_CODE -> importPrivateKey(data.data)
        }
    }

    private fun updateKeyInfo(kp: KeyPairBean?) {
        keypair = kp
        val res = resources
        val tv = findViewById<TextView>(R.id.PublicKeyTextView)
        if (keypair == null) {
            deleteButton.isEnabled = false
            exportPublicKeyButton.isEnabled = false
            exportPrivateKeyButton.isEnabled = false
            publicKey.text = "<no key loaded>"
            tv.text = res.getString(R.string.public_key, "")
        } else {
            deleteButton.isEnabled = true
            exportPublicKeyButton.isEnabled = true
            exportPrivateKeyButton.isEnabled = true
            publicKey.setText(keypair!!.openSSHPublicKey)
            tv.text = keypair!!.description
            tv.text = res.getString(R.string.public_key, keypair!!.description)
        }
    }

    companion object {
        private const val TAG = "KeyPairActivity"
        private const val IMPORT_PRIVATE_KEY_CODE = 0x01
        private const val EXPORT_PUBLIC_KEY_CODE = 0x02
        private const val EXPORT_PRIVATE_KEY_CODE = 0x03
    }
}