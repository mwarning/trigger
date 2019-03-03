package com.example.trigger.https;

import com.example.trigger.R;
import com.github.isabsent.filepicker.SimpleFilePickerDialog;
import static com.github.isabsent.filepicker.SimpleFilePickerDialog.CompositeMode.FOLDER_ONLY_SINGLE_CHOICE;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;


public class CertificateActivity extends AppCompatActivity implements
        SimpleFilePickerDialog.InteractionListenerString, CertificateFetchHandler.OnTaskCompleted {
    private static final String SELECT_FILE_REQUEST = "SELECT_FILE_REQUEST";
    private static final int REQUEST_PERMISSION = 0x01;
    private CertificatePreference preference; // hack
    private AlertDialog.Builder builder;
    private Button importButton;
    private Button exportButton;
    private Button cancelButton;
    private Button selectButton;
    private Button okButton;
    private Button fetchButton;
    private TextView certificateInfo;
    private TextView certificateUrl;
    private TextView pathSelection;
    private Certificate certificate;
    private String fetch_url;
    private String selected_path;

    @Override
    public void onTaskCompleted(CertificateFetchHandler.Result r) {
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
        //this.certificate = SshTools.deserializeKeyPair(
        //    getIntent().getStringExtra("certificate")
        //);
        this.fetch_url = getIntent().getStringExtra("fetch_url");

        builder = new AlertDialog.Builder(this);
        importButton = (Button) findViewById(R.id.ImportButton);
        exportButton = (Button) findViewById(R.id.ExportButton);
        selectButton = (Button) findViewById(R.id.SelectPathButton);
        cancelButton = (Button) findViewById(R.id.CancelButton);
        okButton = (Button) findViewById(R.id.OkButton);
        certificateInfo = (TextView) findViewById(R.id.CertificateInfo);
        certificateUrl = (TextView) findViewById(R.id.CertificateUrl);
        pathSelection = (TextView) findViewById(R.id.PathSelection);
        fetchButton = (Button) findViewById(R.id.FetchButton);

        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasReadPermission()) {
                    final String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                    showListItemDialog("Pick Directory", rootPath, FOLDER_ONLY_SINGLE_CHOICE, SELECT_FILE_REQUEST);
                } else {
                    requestReadPermission(REQUEST_PERMISSION);
                    requestWritePermission(REQUEST_PERMISSION);
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
                String url = fetch_url;
                if (url == null || url.isEmpty()) {
                    showErrorMessage("Empty URL", "No URL set to fetch a certificate from.");
                } else {
                    new CertificateFetchHandler(CertificateActivity.this).execute(url);
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

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // persist your value here
                CertificateActivity.this.finish();
            }
        });

        updateCertificateInfo();
        updatePathInfo();
        updateFetchUrl();
    }

    private void exportCertificateFile() {
        String path = this.selected_path;
        if (path == null || path.isEmpty()) {
            showErrorMessage("No Directory Selected", "No directory for export selected.");
        } else if (certificate == null) {
            showErrorMessage("No Certificate", "No Certificate loaded to export.");
        } else if (!hasWritePermission()) {
            requestWritePermission(REQUEST_PERMISSION);
        } else try {
            writeExternalFile(path + "/cert.pem", HttpsTools.serializeCertificate(certificate).getBytes());

            Toast.makeText(getApplicationContext(), "Done. Wrote file 'cert.pem'.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    private void importCertificateFile() {
        String path = this.selected_path;
        if (path == null || path.isEmpty()) {
            showErrorMessage("No Directory Selected", "No directory for import selected.");
        } else if (!hasReadPermission()) {
            requestReadPermission(REQUEST_PERMISSION);
        } else try {
            byte[] cert = readExternalFile(selected_path + "/cert.pem");

            certificate = HttpsTools.deserializeCertificate(new String(cert));

            Toast.makeText(getApplicationContext(), "Done. Read 'cert.pem'.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    // path picker
    @Override
    public void showListItemDialog(String title, String folderPath, SimpleFilePickerDialog.CompositeMode mode, String dialogTag){
        SimpleFilePickerDialog.build(folderPath, mode)
                .title(title)
                .show(this, dialogTag);
    }

    @Override
    public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
        switch (dialogTag) {
            case SELECT_FILE_REQUEST:
                if (extras.containsKey(SimpleFilePickerDialog.SELECTED_SINGLE_PATH)) {
                    String selectedSinglePath = extras.getString(SimpleFilePickerDialog.SELECTED_SINGLE_PATH);
                    this.selected_path = selectedSinglePath;
                    updatePathInfo();
                }
                break;
        }
        return false;
    }

    // write file to external storage
    private static void writeExternalFile(String filepath, byte[] data) throws IOException {
        File file = new File(filepath);
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("Failed to delete existing file: " + filepath);
            }
        }
        file.createNewFile();
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.close();
    }

    // read file from external storage
    private static byte[] readExternalFile(String filepath) throws IOException {
        File file = new File(filepath);
        if (!file.exists()) {
            throw new IOException("File does not exist: " + filepath);
        }
        FileInputStream fis = new FileInputStream(file);

        int nRead;
        byte[] data = new byte[16384];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while ((nRead = fis.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        return buffer.toByteArray();
    }

    private void updateCertificateInfo() {
        String text = "";
        try {
            if (certificate == null) {
                text = "<no certificate>";
            } else {
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
            }
        } catch (Exception e) {
            text = e.getMessage();
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

    private void updateFetchUrl() {
        if (fetch_url == null || fetch_url.isEmpty()) {
            certificateUrl.setText("<no url selected>");
        } else {
            certificateUrl.setText(fetch_url);
        }
    }

    private boolean hasReadPermission() {
        return (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean hasWritePermission() {
        return (ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private void requestReadPermission(int request_code) {
        ActivityCompat.requestPermissions(CertificateActivity.this, new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE }, request_code);
    }

    private void requestWritePermission(int request_code) {
        ActivityCompat.requestPermissions(CertificateActivity.this, new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE }, request_code);
    }

    private static boolean allGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION:
                if (allGranted(grantResults)) {
                    // permissions granted
                    Toast.makeText(getApplicationContext(), "Permissions granted - please try again.", Toast.LENGTH_SHORT).show();
                } else {
                    showErrorMessage("Permissions Required", "Action cannot be performed.");
                }
                break;
        }
    }
}
