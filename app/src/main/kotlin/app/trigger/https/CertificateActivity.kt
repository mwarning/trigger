package app.trigger.https

import app.trigger.Utils.writeFile
import app.trigger.Utils.readFile
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import app.trigger.R
import android.content.Intent
import android.content.DialogInterface
import android.view.Gravity
import android.net.Uri
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.lang.Exception
import java.nio.charset.Charset
import java.security.cert.Certificate
import java.security.cert.X509Certificate

class CertificateActivity : AppCompatActivity(), CertificateFetchTask.OnTaskCompleted {
    private lateinit var preference : CertificatePreference // hack
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
    private val selected_path: String? = null

    override fun onCertificateFetchTaskCompleted(r: CertificateFetchTask.Result) {
        val cert = r.certificate
        if (cert != null) {
            certificate = cert
            Toast.makeText(applicationContext, "Done.", Toast.LENGTH_SHORT).show()
            updateCertificateInfo()
        } else {
            showErrorMessage("Error Fetching Certificate", r.error)
        }
    }
    private fun showErrorMessage(title: String, message: String?) {
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_certificate)
        preference = CertificatePreference.Companion.self!! // hack, TODO: pass serialized key in bundle
        certificate = preference.certificate
        val title = findViewById<TextView>(R.id.CertificateTitle)
        title.text = intent.getStringExtra("certificate_preference_title")
        builder = AlertDialog.Builder(this)
        importButton = findViewById(R.id.ImportButton)
        exportButton = findViewById(R.id.ExportButton)
        cancelButton = findViewById(R.id.CancelButton)
        okButton = findViewById(R.id.OkButton)
        deleteButton = findViewById(R.id.DeleteButton)
        certificateInfo = findViewById(R.id.CertificateInfo)
        certificateUrl = findViewById(R.id.CertificateUrl)
        fetchButton = findViewById(R.id.FetchButton)
        val self = this

        // initialize with url for registering
        certificateUrl.setText(
                intent.getStringExtra("register_url")
        )
        importButton.setOnClickListener(View.OnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            startActivityForResult(intent, READ_REQUEST_CODE)
        })
        exportButton.setOnClickListener(View.OnClickListener { v: View? ->
            if (certificate == null) {
                showErrorMessage("No Certificate", "No Certificate loaded to export.")
            } else {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.putExtra(Intent.EXTRA_TITLE, "cert.pem")
                intent.type = "*/*"
                startActivityForResult(intent, WRITE_REQUEST_CODE)
            }
        })
        fetchButton.setOnClickListener(View.OnClickListener { v: View? ->
            val url = certificateUrl.getText().toString()
            if (url.isEmpty()) {
                showErrorMessage("Empty URL", "No URL set to fetch a certificate from.")
            } else if (!url.startsWith("https://")) {
                showErrorMessage("Invalid URL", "URL needs to start with 'https://'")
            } else {
                CertificateFetchTask(this@CertificateActivity).execute(url)
            }
        })
        okButton.setOnClickListener(View.OnClickListener { v: View? ->
            // update the SwitchPreference switch
            preference.certificate = certificate
            finish()
        })
        deleteButton.setOnClickListener(View.OnClickListener { v: View? ->
            builder.setTitle("Confirm")
            builder.setMessage("Really remove certificate?")
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
        })
        cancelButton.setOnClickListener(View.OnClickListener { v: View? ->
            // persist your value here
            finish()
        })
        updateCertificateInfo()
    }

    private fun exportCertificateFile(uri: Uri?) {
        if (certificate == null) {
            showErrorMessage("No Certificate", "No Certificate loaded to export.")
        } else try {
            val pem = HttpsTools.serializeCertificate(certificate)
            writeFile(this, uri, pem.toByteArray())
            Toast.makeText(applicationContext, "Done. Wrote " + uri!!.lastPathSegment, Toast.LENGTH_SHORT).show()
            updateCertificateInfo()
        } catch (e: Exception) {
            showErrorMessage("Error", e.message)
        }
    }

    private fun importCertificateFile(uri: Uri?) {
        try {
            val cert = readFile(this, uri)
            certificate = HttpsTools.deserializeCertificate(String(cert, Charsets.UTF_8))
            Toast.makeText(applicationContext, "Done. Read " + uri!!.lastPathSegment, Toast.LENGTH_SHORT).show()
            updateCertificateInfo()
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
            WRITE_REQUEST_CODE -> exportCertificateFile(data.data)
            READ_REQUEST_CODE -> importCertificateFile(data.data)
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
                certificateInfo.gravity = Gravity.TOP or Gravity.LEFT
            }
        } catch (e: Exception) {
            text = e.message.toString()
            certificateInfo.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        certificateInfo.text = text
    }

    companion object {
        private const val TAG = "CertificateActivity"
        private const val READ_REQUEST_CODE = 0x01
        private const val WRITE_REQUEST_CODE = 0x02
    }
}