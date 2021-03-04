package app.trigger.https;

import app.trigger.R;
import app.trigger.Utils;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.codekidlabs.storagechooser.StorageChooser;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;


public class CertificateActivity extends AppCompatActivity implements
        StorageChooser.OnSelectListener, StorageChooser.OnCancelListener, CertificateFetchTask.OnTaskCompleted {
    private static final int REQUEST_PERMISSION = 0x01;
    private CertificatePreference preference; // hack
    private AlertDialog.Builder builder;
    private Button importButton;
    private Button exportButton;
    private Button cancelButton;
    private Button selectButton;
    private Button okButton;
    private Button deleteButton;
    private Button fetchButton;
    private TextView certificateInfo;
    private EditText certificateUrl;
    private TextView pathSelection;
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
        selectButton = findViewById(R.id.SelectPathButton);
        cancelButton = findViewById(R.id.CancelButton);
        okButton = findViewById(R.id.OkButton);
        deleteButton = findViewById(R.id.DeleteButton);
        certificateInfo = findViewById(R.id.CertificateInfo);
        certificateUrl = findViewById(R.id.CertificateUrl);
        pathSelection = findViewById(R.id.PathSelection);
        fetchButton = findViewById(R.id.FetchButton);
        final CertificateActivity self = this;

        // initialize with url for registering
        certificateUrl.setText(
                getIntent().getStringExtra("register_url")
        );

        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.hasReadPermission(self)) {
                    StorageChooser chooser = new StorageChooser.Builder()
                            .withActivity(CertificateActivity.this)
                            .withFragmentManager(getFragmentManager())
                            .allowCustomPath(true)
                            .setType(StorageChooser.DIRECTORY_CHOOSER)
                            .build();
                    chooser.show();

                    // get path that the user has chosen
                    chooser.setOnSelectListener(CertificateActivity.this);
                    chooser.setOnCancelListener(CertificateActivity.this);
                } else {
                    Utils.requestReadPermission(self, REQUEST_PERMISSION);
                    Utils.requestWritePermission(self, REQUEST_PERMISSION);
                }
            }
        });

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportCertificateFile();
            }
        });

        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importCertificateFile();
            }
        });

        fetchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = certificateUrl.getText().toString();
                if (url == null || url.isEmpty()) {
                    showErrorMessage("Empty URL", "No URL set to fetch a certificate from.");
                } else {
                    new CertificateFetchTask(CertificateActivity.this).execute(url);
                }
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // persist your value here
                if (CertificateActivity.this.certificate != null) {
                    preference.setCertificate(CertificateActivity.this.certificate);
                }
                CertificateActivity.this.finish();
            }
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
        updatePathInfo();
    }

    private void exportCertificateFile() {
        String path = this.selected_path;
        if (path == null || path.isEmpty()) {
            showErrorMessage("No Directory Selected", "No directory for export selected.");
        } else if (certificate == null) {
            showErrorMessage("No Certificate", "No Certificate loaded to export.");
        } else if (!Utils.hasWritePermission(this)) {
            Utils.requestWritePermission(this, REQUEST_PERMISSION);
        } else try {
            Utils.writeExternalFile(path + "/cert.pem", HttpsTools.serializeCertificate(certificate).getBytes());

            Toast.makeText(getApplicationContext(), "Done. Wrote file 'cert.pem'.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    private void importCertificateFile() {
        String path = this.selected_path;
        if (path == null || path.isEmpty()) {
            showErrorMessage("No Directory Selected", "No directory for import selected.");
        } else if (!Utils.hasReadPermission(this)) {
            Utils.requestReadPermission(this, REQUEST_PERMISSION);
        } else try {
            byte[] cert = Utils.readExternalFile(selected_path + "/cert.pem");

            certificate = HttpsTools.deserializeCertificate(new String(cert));

            Toast.makeText(getApplicationContext(), "Done. Read 'cert.pem'.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    // for StorageChooser
    @Override
    public void onSelect(String path) {
        this.selected_path = path;
        updatePathInfo();
    }

    // for StorageChooser
    @Override
    public void onCancel() {
        // nothing to do
    }

    private void updateCertificateInfo() {
        String text = "";
        try {
            if (certificate == null) {
                deleteButton.setEnabled(false);
                text = "<no certificate>";
                certificateInfo.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            } else {
                deleteButton.setEnabled(true);
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

    private void updatePathInfo() {
        if (selected_path == null || selected_path.isEmpty()) {
            pathSelection.setText("<none selected>");
            exportButton.setEnabled(false);
            importButton.setEnabled(false);
        } else {
            pathSelection.setText(selected_path);
            exportButton.setEnabled(true);
            importButton.setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION:
                if (Utils.allGranted(grantResults)) {
                    // permissions granted
                    Toast.makeText(getApplicationContext(), "Permissions granted - please try again.", Toast.LENGTH_SHORT).show();
                } else {
                    showErrorMessage("Permissions Required", "Action cannot be performed.");
                }
                break;
        }
    }
}
