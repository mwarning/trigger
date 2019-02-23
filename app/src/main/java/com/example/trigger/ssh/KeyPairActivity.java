package com.example.trigger.ssh;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.example.trigger.R;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import org.apache.commons.io.IOUtils;


public class KeyPairActivity extends AppCompatActivity implements
        SimpleFilePickerDialog.InteractionListenerString {
    private static final String SELECT_PATH_REQUEST = "SELECT_PATH_REQUEST";
    private static final int REQUEST_PERMISSION = 0x01;
    private KeyPairPreference preference; // hack
    private AlertDialog.Builder builder;
    private Button createButton;
    private Button importButton;
    private Button exportButton;
    private Button cancelButton;
    private Button selectButton;
    private Button okButton;
    private TextView fingerprint;
    private TextView publicKey;
    private TextView pathSelection;
    private KeyPair keypair;
    private String selected_path;

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keypair);

        this.preference = KeyPairPreference.self; // hack, TODO: pass serialized key in bundle
        this.keypair = this.preference.getKeyPair();
        //this.keypair = SshTools.deserializeKeyPair(
        //    getIntent().getStringExtra("keypair")
        //);

        builder = new AlertDialog.Builder(this);
        createButton = (Button) findViewById(R.id.CreateButton);
        importButton = (Button) findViewById(R.id.ImportButton);
        exportButton = (Button) findViewById(R.id.ExportButton);
        cancelButton = (Button) findViewById(R.id.CancelButton);
        selectButton = (Button) findViewById(R.id.SelectButton);
        okButton = (Button) findViewById(R.id.OkButton);
        fingerprint = (TextView) findViewById(R.id.Fingerprint);
        publicKey = (TextView) findViewById(R.id.PublicKey);
        pathSelection = (TextView) findViewById(R.id.PathSelection);

        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    JSch jsch = new JSch();
                    keypair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);

                    Toast.makeText(getApplicationContext(), "Done", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("KeyPairActivity", e.toString());
                }

                updateKeyInfo();
            }
        });

        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasReadPermission()) {
                    final String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                    showListItemDialog("Pick Directory", rootPath, FOLDER_ONLY_SINGLE_CHOICE, SELECT_PATH_REQUEST);
                } else {
                    requestReadPermission(REQUEST_PERMISSION);
                    requestWritePermission(REQUEST_PERMISSION);
                }
            }
        });

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportKeys();
            }
        });

        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importKeys();
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // persist your value here
                if (KeyPairActivity.this.keypair != null) {
                    preference.setKeyPair(KeyPairActivity.this.keypair);
                }
                KeyPairActivity.this.finish();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // persist your value here
                KeyPairActivity.this.finish();
            }
        });

        updateKeyInfo();
        updatePathInfo();
    }

    private void exportKeys() {
        if (selected_path == null) {
            showErrorMessage("No Directory Selected", "No directory for export selected.");
        } else if (keypair == null) {
            showErrorMessage("No Key Pair", "No keys loaded to export.");
        } else if (!hasWritePermission()) {
            requestWritePermission(REQUEST_PERMISSION);
        } else try {
            SshTools.KeyPairData data = SshTools.keypairToBytes(KeyPairActivity.this.keypair);

            writeExternalFile(selected_path + "/id_rsa.pub", data.pubkey);
            writeExternalFile(selected_path + "/id_rsa", data.prvkey);

            Toast.makeText(getApplicationContext(), "Done. Wrote files 'id_rsa.pub' and 'id_rsa'.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    private void importKeys() {
        if (selected_path == null) {
            showErrorMessage("No Directory Selected", "No directory for import selected.");
        } else if (!hasReadPermission()) {
            requestReadPermission(REQUEST_PERMISSION);
        } else try {
            byte[] prvkey = readExternalFile(selected_path + "/id_rsa");
            byte[] pubkey = readExternalFile(selected_path + "/id_rsa.pub");

            JSch jsch = new JSch();
            KeyPairActivity.this.keypair = KeyPair.load(jsch, prvkey, pubkey);

            Toast.makeText(getApplicationContext(), "Done. Read 'id_rsa.pub' and 'id_rsa'.", Toast.LENGTH_SHORT).show();
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
            case SELECT_PATH_REQUEST:
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
        IOUtils.write(data, fos);
        fos.close();
    }

    // read file from external storage
    private static byte[] readExternalFile(String filepath) throws IOException {
        File file = new File(filepath);
        if (!file.exists()) {
            throw new IOException("File does not exist: " + filepath);
        }
        FileInputStream fis = new FileInputStream(file);
        return IOUtils.toByteArray(fis);
    }

    private void updateKeyInfo() {
        if (keypair == null) {
            fingerprint.setText("<no key loaded>");
            publicKey.setText("<no key loaded>");
        } else {
            SshTools.KeyPairData data = SshTools.keypairToBytes(keypair);

            fingerprint.setText(keypair.getFingerPrint());
            publicKey.setText(new String(data.pubkey));
        }
    }

    private void updatePathInfo() {
        if (selected_path == null) {
            pathSelection.setText("<none selected>");
            exportButton.setEnabled(false);
            importButton.setEnabled(false);
        } else {
            pathSelection.setText(this.selected_path);
            exportButton.setEnabled(true);
            importButton.setEnabled(true);
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
        ActivityCompat.requestPermissions(KeyPairActivity.this, new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE }, request_code);
    }

    private void requestWritePermission(int request_code) {
        ActivityCompat.requestPermissions(KeyPairActivity.this, new String[] {
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
