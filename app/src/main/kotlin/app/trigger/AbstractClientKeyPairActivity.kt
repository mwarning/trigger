/*
* Copyright (C) 2025 The Trigger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package app.trigger

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import app.trigger.Utils.readFile
import app.trigger.Utils.writeFile
import app.trigger.ssh.KeyPairBean
import app.trigger.ssh.SshTools

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

    protected fun showMessage(textId: Int) {
        Toast.makeText(applicationContext, textId, Toast.LENGTH_SHORT).show()
    }

    protected fun showErrorMessageDialog(message: String?) {
        builder.setTitle(getString(R.string.error))
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_abstract_client_keypair)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

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
                showErrorMessageDialog("No key loaded to export.")
            } else if (useClipboardCheckBox.isChecked) {
                val privateKey = keypair!!.openSSHPrivateKey
                val clip = ClipData.newPlainText(keypair!!.description, privateKey)
                clipboard.setPrimaryClip(clip)
                showMessage(R.string.done)
            } else {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
                intent.type = "*/*"
                exportPrivateKeyLauncher.launch(intent)
            }
        }

        importPrivateKeyButton.setOnClickListener {
            if (useClipboardCheckBox.isChecked) {
                if (clipboard.hasPrimaryClip()) {
                    val privateKey = clipboard.primaryClip!!.getItemAt(0).text.toString()
                    val kp = SshTools.parsePrivateKeyPEM(privateKey)
                    if (kp != null) {
                        showMessage(R.string.done)
                        updateKeyInfo(kp)
                    } else {
                        showMessage(R.string.error_import_failed)
                    }
                } else {
                    showMessage(R.string.error_empty_clipboard)
                }
            } else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
                intent.type = "*/*"
                importPrivateKeyLauncher.launch(intent)
            }
        }

        okButton.setOnClickListener {
            // update the SwitchPreference
            setKeyPair(keypair)
            finish()
        }

        deleteButton.setOnClickListener {
            builder.setTitle(R.string.confirm)
            builder.setMessage(R.string.dialog_really_remove_client_private_key)
            builder.setCancelable(false) // not necessary
            builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, _: Int ->
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
            showErrorMessageDialog("No key loaded to export.")
        } else {
            try {
                writeFile(this, uri, kp.openSSHPrivateKey!!.toByteArray())
                showMessage("Done. Wrote private key: ${uri.lastPathSegment}")
            } catch (e: Exception) {
                showErrorMessageDialog(e.message)
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
            showErrorMessageDialog(e.message)
        }
    }

    private var importPrivateKeyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            val uri = intent.data ?: return@registerForActivityResult
            importPrivateKey(uri)
        }
    }

    private var exportPrivateKeyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            val uri: Uri = intent.data ?: return@registerForActivityResult
            exportPrivateKey(uri)
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
}