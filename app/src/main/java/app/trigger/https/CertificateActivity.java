package app.trigger.https;

import app.trigger.R;
import app.trigger.Utils;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;


public class CertificateActivity extends AppCompatActivity implements CertificateFetchTask.OnTaskCompleted {
    private static final String TAG = "CertificateActivity";
    private static final int READ_REQUEST_CODE = 0x01;
    private static final int WRITE_REQUEST_CODE = 0x02;
    private CertificatePreference preference; // hack
    private AlertDialog.Builder builder;
    private Button importButton;
    private Button exportButton;
    private Button cancelButton;
    private Button okButton;
    private Button deleteButton;
    private Button fetchButton;
    private TextView certificateInfo;
    private EditText certificateUrl;
    private Certificate certificate;
    private String selected_path;

    @Override
    public void onCertificateFetchTaskCompleted(CertificateFetchTask.Result r) {
        if (r.certificate != null) {
            this.certificate = r.certificate;
            Toast.makeText(getApplicationContext(), "Done.", Toast.LENGTH_SHORT).show();
            updateCertificateInfo();
        } else {
            showErrorMessage("Error Fetching Certificate", r.error);
        }
    }

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_certificate);

        this.preference = CertificatePreference.self; // hack, TODO: pass serialized key in bundle
        this.certificate = this.preference.getCertificate();
        //this.certificate = HttpsTools.deserializeCertififcate(
        //    getIntent().getStringExtra("certificate")
        //);

        builder = new AlertDialog.Builder(this);
        importButton = findViewById(R.id.ImportButton);
        exportButton = findViewById(R.id.ExportButton);
        cancelButton = findViewById(R.id.CancelButton);
        okButton = findViewById(R.id.OkButton);
        deleteButton = findViewById(R.id.DeleteButton);
        certificateInfo = findViewById(R.id.CertificateInfo);
        certificateUrl = findViewById(R.id.CertificateUrl);
        fetchButton = findViewById(R.id.FetchButton);
        final CertificateActivity self = this;

        // initialize with url for registering
        certificateUrl.setText(
            getIntent().getStringExtra("register_url")
        );

        importButton.setOnClickListener((View v) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, READ_REQUEST_CODE);
        });

        exportButton.setOnClickListener((View v) -> {
            if (certificate == null) {
                showErrorMessage("No Certificate", "No Certificate loaded to export.");
            } else {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, "cert.pem");
                intent.setType("*/*");
                startActivityForResult(intent, WRITE_REQUEST_CODE);
            }
        });

        fetchButton.setOnClickListener((View v) -> {
            String url = certificateUrl.getText().toString();
            if (url == null || url.isEmpty()) {
                showErrorMessage("Empty URL", "No URL set to fetch a certificate from.");
            } else {
                new CertificateFetchTask(CertificateActivity.this).execute(url);
            }
        });

        okButton.setOnClickListener((View v) -> {
            // update the SwitchPreference switch
            preference.setCertificate(CertificateActivity.this.certificate);
            CertificateActivity.this.finish();
        });

        deleteButton.setOnClickListener((View v) -> {
            builder.setTitle("Confirm");
            builder.setMessage("Really remove certificate?");
            builder.setCancelable(false); // not necessary

            builder.setPositiveButton(R.string.yes, (DialogInterface dialog, int id) -> {
                CertificateActivity.this.certificate = null;
                updateCertificateInfo();
                dialog.cancel();
            });

            builder.setNegativeButton(R.string.no, (DialogInterface dialog, int id) -> {
                dialog.cancel();
            });

            // create dialog box
            AlertDialog alert = builder.create();
            alert.show();
        });

        cancelButton.setOnClickListener((View v) -> {
            // persist your value here
            CertificateActivity.this.finish();
        });

        updateCertificateInfo();
    }

    private void exportCertificateFile(Uri uri) {
        if (certificate == null) {
            showErrorMessage("No Certificate", "No Certificate loaded to export.");
        } else try {
            String pem = HttpsTools.serializeCertificate(certificate);
            Utils.writeFile(this, uri, pem.getBytes());
            Toast.makeText(getApplicationContext(), "Done. Wrote " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show();
            updateCertificateInfo();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    private void importCertificateFile(Uri uri) {
        try {
            byte[] cert = Utils.readFile(this, uri);
            certificate = HttpsTools.deserializeCertificate(new String(cert, "UTF-8"));
            Toast.makeText(getApplicationContext(), "Done. Read " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show();
            updateCertificateInfo();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        if (data == null || data.getData() == null) {
            return;
        }

        switch (requestCode) {
        case WRITE_REQUEST_CODE:
            exportCertificateFile(data.getData());
            break;
        case READ_REQUEST_CODE:
            importCertificateFile(data.getData());
            break;
        }
    }

    private void updateCertificateInfo() {
        String text = "";
        try {
            if (certificate == null) {
                deleteButton.setEnabled(false);
                exportButton.setEnabled(false);
                text = "<no certificate>";
                certificateInfo.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            } else {
                deleteButton.setEnabled(true);
                exportButton.setEnabled(true);
                if (certificate instanceof X509Certificate) {
                    X509Certificate c = (X509Certificate) certificate;
                    if (!HttpsTools.isValid(c)) {
                        text += "Warning: Certificate is not valid.\n";
                    }
                    if (HttpsTools.isSelfSigned(c)) {
                        text += "Info: Certificate is self-signed.\n";
                    }
                    text += "\n";
                }
                text += certificate.toString();
                certificateInfo.setGravity(Gravity.TOP | Gravity.LEFT);
            }
        } catch (Exception e) {
            text = e.getMessage();
            certificateInfo.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        }
        certificateInfo.setText(text);
    }
}
