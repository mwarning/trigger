package app.trigger

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import app.trigger.Utils.readFile
import app.trigger.Utils.writeFile
import app.trigger.https.CertificateFetchTask
import app.trigger.https.HttpsTools
import java.security.cert.Certificate
import java.security.cert.X509Certificate

abstract class AbstractCertificateActivity : AppCompatActivity(), CertificateFetchTask.OnTaskCompleted {
    private lateinit var builder: AlertDialog.Builder
    private lateinit var importButton: Button
    private lateinit var exportButton: Button
    private lateinit var cancelButton: Button
    private lateinit var okButton: Button
    private lateinit var deleteButton: Button
    private lateinit var fetchButton: Button
    private lateinit var certificateInfo: TextView
    private lateinit var certificateUrl: EditText
    private var certificate: Certificate? = null

    protected abstract fun getDoor(): Door
    protected abstract fun getCertificate(): Certificate?
    protected abstract fun setCertificate(certificate: Certificate?)

    override fun onCertificateFetchTaskCompleted(result: CertificateFetchTask.Result) {
        val cert = result.certificate
        if (cert != null) {
            certificate = cert
            Toast.makeText(applicationContext, R.string.done, Toast.LENGTH_SHORT).show()
            updateCertificateInfo()
        } else {
            showErrorMessage("Error Fetching Certificate: ${result.error}")
        }
    }

    private fun showErrorMessage(message: String?) {
        builder.setTitle(getString(R.string.error))
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_abstract_certificate)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        certificate = getCertificate()
        builder = AlertDialog.Builder(this)
        importButton = findViewById(R.id.ImportButton)
        exportButton = findViewById(R.id.ExportButton)
        cancelButton = findViewById(R.id.CancelButton)
        okButton = findViewById(R.id.OkButton)
        deleteButton = findViewById(R.id.DeleteButton)
        certificateInfo = findViewById(R.id.CertificateInfo)
        certificateUrl = findViewById(R.id.CertificateUrl)
        fetchButton = findViewById(R.id.FetchButton)

        // initialize with URL for registering
        val door = getDoor()
        certificateUrl.setText(door.getRegisterUrl())

        importButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            importFileLauncher.launch(intent)
        }

        exportButton.setOnClickListener {
            if (certificate == null) {
                showErrorMessage("No Certificate loaded to export.")
            } else {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.putExtra(Intent.EXTRA_TITLE, "cert.pem")
                intent.type = "*/*"
                exportFileLauncher.launch(intent)
            }
        }

        fetchButton.setOnClickListener {
            val url = certificateUrl.text.toString()
            if (url.isEmpty()) {
                showErrorMessage("No URL set to fetch a certificate from.")
            } else if (!url.startsWith("https://")) {
                showErrorMessage("URL needs to start with 'https://'")
            } else {
                CertificateFetchTask(this).execute(url)
            }
        }

        okButton.setOnClickListener {
            setCertificate(certificate)
            finish()
        }

        deleteButton.setOnClickListener {
            builder.setTitle(R.string.confirm)
            builder.setMessage(R.string.dialog_really_remove_certificate)
            builder.setCancelable(false) // not necessary
            builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, id: Int ->
                certificate = null
                updateCertificateInfo()
                dialog.cancel()
            }
            builder.setNegativeButton(R.string.no) { dialog: DialogInterface, id: Int -> dialog.cancel() }

            // create dialog box
            val alert = builder.create()
            alert.show()
        }

        cancelButton.setOnClickListener {
            // persist your value here
            finish()
        }

        updateCertificateInfo()
    }

    private fun exportCertificateFile(uri: Uri) {
        if (certificate == null) {
            showErrorMessage("No Certificate loaded to export.")
        } else try {
            val pem = HttpsTools.serializeCertificate(certificate)
            writeFile(this, uri, pem.toByteArray())
            Toast.makeText(applicationContext, "Done. Wrote " + uri.lastPathSegment, Toast.LENGTH_SHORT).show()
            updateCertificateInfo()
        } catch (e: Exception) {
            showErrorMessage(e.message)
        }
    }

    private fun importCertificateFile(uri: Uri) {
        try {
            val cert = readFile(this, uri)
            certificate = HttpsTools.deserializeCertificate(String(cert, Charsets.UTF_8))
            Toast.makeText(applicationContext, "Done. Read " + uri.lastPathSegment, Toast.LENGTH_SHORT).show()
            updateCertificateInfo()
        } catch (e: Exception) {
            showErrorMessage(e.message)
        }
    }

    private var importFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            val uri = intent.data ?: return@registerForActivityResult
            importCertificateFile(uri)
        }
    }

    private var exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            val uri: Uri = intent.data ?: return@registerForActivityResult
            exportCertificateFile(uri)
        }
    }

    private fun updateCertificateInfo() {
        var text = ""
        try {
            if (certificate == null) {
                deleteButton.isEnabled = false
                exportButton.isEnabled = false
                text = "<no certificate>"
                certificateInfo.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            } else {
                deleteButton.isEnabled = true
                exportButton.isEnabled = true
                if (certificate is X509Certificate) {
                    val c = certificate as X509Certificate
                    if (!HttpsTools.isValid(c)) {
                        text += "Warning: Certificate is not valid.\n"
                    }
                    if (HttpsTools.isSelfSigned(c)) {
                        text += "Info: Certificate is self-signed.\n"
                    }
                    text += "\n"
                }
                text += certificate.toString()
                certificateInfo.gravity = Gravity.TOP or Gravity.START
            }
        } catch (e: Exception) {
            text = e.message.toString()
            certificateInfo.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        certificateInfo.text = text
    }

    companion object {
        private const val TAG = "CertificateActivity"
    }
}
