package com.example.trigger.ssh;

import com.example.trigger.Utils;
import com.github.isabsent.filepicker.SimpleFilePickerDialog;
import static com.github.isabsent.filepicker.SimpleFilePickerDialog.CompositeMode.FOLDER_ONLY_SINGLE_CHOICE;

import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.trigger.R;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;


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
        final KeyPairActivity self = this;

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
                if (Utils.hasReadPermission(self)) {
                    final String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                    showListItemDialog("Pick Directory", rootPath, FOLDER_ONLY_SINGLE_CHOICE, SELECT_PATH_REQUEST);
                } else {
                    Utils.requestReadPermission(self, REQUEST_PERMISSION);
                    Utils.requestWritePermission(self, REQUEST_PERMISSION);
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
                if (self.keypair != null) {
                    preference.setKeyPair(self.keypair);
                }
                self.finish();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // persist your value here
                self.finish();
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
        } else if (!Utils.hasWritePermission(this)) {
            Utils.requestWritePermission(this, REQUEST_PERMISSION);
        } else try {
            SshTools.KeyPairData data = SshTools.keypairToBytes(KeyPairActivity.this.keypair);

            Utils.writeExternalFile(selected_path + "/id_rsa.pub", data.pubkey);
            Utils.writeExternalFile(selected_path + "/id_rsa", data.prvkey);

            Toast.makeText(getApplicationContext(), "Done. Wrote files 'id_rsa.pub' and 'id_rsa'.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    private void importKeys() {
        if (selected_path == null) {
            showErrorMessage("No Directory Selected", "No directory for import selected.");
        } else if (!Utils.hasReadPermission(this)) {
            Utils.requestReadPermission(this, REQUEST_PERMISSION);
        } else try {
            byte[] prvkey = Utils.readExternalFile(selected_path + "/id_rsa");
            byte[] pubkey = Utils.readExternalFile(selected_path + "/id_rsa.pub");

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
