package com.example.trigger.ssh;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.example.trigger.R;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;


public class KeyPairActivity extends AppCompatActivity {
    private KeyPairPreference preference; // hack
    private AlertDialog.Builder builder;
    private Button createButton;
    private Button importButton;
    private Button exportButton;
    private Button cancelButton;
    private Button okButton;
    private TextView fingerprint;
    private TextView publicKey;
    private TextView pathSelection;
    private KeyPair keypair;
    private Uri path_uri;

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

        builder = new AlertDialog.Builder(this);
        createButton = (Button) findViewById(R.id.CreateButton);
        importButton = (Button) findViewById(R.id.ImportButton);
        exportButton = (Button) findViewById(R.id.ExportButton);
        okButton = (Button) findViewById(R.id.OkButton);
        cancelButton = (Button) findViewById(R.id.CancelButton);
        fingerprint = (TextView) findViewById(R.id.Fingerprint);
        publicKey = (TextView) findViewById(R.id.PublicKey);
        pathSelection = (TextView) findViewById(R.id.PathSelection);

        // only active after path was selected
        exportButton.setEnabled(false);
        importButton.setEnabled(false);

        pathSelection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFileSelectActivity();
            }
        });

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

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (KeyPairActivity.this.keypair == null) {
                    showErrorMessage("No Key", "No key loaded to export.");
                    return;
                }

                if (path_uri == null) {
                    showErrorMessage("Missing Target", "No output folder was selected.");
                    return;
                }

                try {
                    SshTools.KeyPairData data = SshTools.keypairToBytes(KeyPairActivity.this.keypair);
                    writeExternalFile(getApplicationContext(), path_uri, "id_rsa.pub", data.pubkey);
                    writeExternalFile(getApplicationContext(), path_uri, "id_rsa", data.prvkey);

                    // report all done
                    Toast.makeText(getApplicationContext(), "Done exporting files 'id_rsa.pub' and 'id_rsa'.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    showErrorMessage("Error", e.toString());
                }
            }
        });

        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (path_uri == null) {
                    showErrorMessage("Missing Target", "No public key file was selected.");
                    return;
                }

                try {
                    byte[] prvkey = readExternalFile(getApplicationContext(), path_uri, "id_rsa");
                    byte[] pubkey = readExternalFile(getApplicationContext(), path_uri, "id_rsa.pub");

                    JSch jsch = new JSch();
                    KeyPairActivity.this.keypair = KeyPair.load(jsch, prvkey, pubkey);

                    Toast.makeText(getApplicationContext(), "Done importing files 'id_rsa.pub' and 'id_rsa'.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    showErrorMessage("Error", "Error occured while processing key file: " + e.toString());
                }
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
    }

    // write file to external storage
    private static void writeExternalFile(Context context, Uri dirUri, String filename, byte[] data) throws IOException {
        String path = dirUri.toString();
        dirUri = Uri.parse(path.substring(0, path.lastIndexOf("%2F")));
        Uri fileUri = Uri.withAppendedPath(dirUri, filename);

        // Create a new file and write into it
        OutputStream out = context.getContentResolver().openOutputStream(fileUri);
        out.write(data);
        out.close();
    }

    // read file from external storage
    private static byte[] readExternalFile(Context context, Uri dirUri, String filename) throws IOException {
        String path = dirUri.toString();
        dirUri = Uri.parse(path.substring(0, path.lastIndexOf("%2F")));
        Uri fileUri = Uri.withAppendedPath(dirUri, filename);

        InputStream input = context.getContentResolver().openInputStream(dirUri);

        if (input == null) {
            throw new IOException("File not found.");
        }

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        int size;

        while ((length = input.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }

        return result.toByteArray();
    }

    void updateKeyInfo() {
        if (this.keypair == null) {
            this.fingerprint.setText("<no key loaded>");
            this.publicKey.setText("<no key loaded>");
        } else {
            SshTools.KeyPairData data = SshTools.keypairToBytes(this.keypair);

            this.fingerprint.setText(this.keypair.getFingerPrint());
            this.publicKey.setText(new String(data.pubkey));
        }
    }

    private static final int SELECT_PATH_ACTIVITY_REQUEST = 0x1;

    private static final int READ_EXTERNAL_STORAGE_PERMISSION_REQUEST = 0x1;
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST = 0x2;
    private static final int CAMERA_PERMISSION_REQUEST = 0x3;

    private void startFileSelectActivity() {
        Intent documentIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        documentIntent.setType("*/*");
        documentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(documentIntent, SELECT_PATH_ACTIVITY_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Activity.RESULT_CANCELED == resultCode) {
            return;
        }

        switch (requestCode) {
            case SELECT_PATH_ACTIVITY_REQUEST:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) {
                    ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        READ_EXTERNAL_STORAGE_PERMISSION_REQUEST);
                } else {
                    path_uri = data.getData();
                    if (path_uri == null) {
                        pathSelection.setText("none");
                        exportButton.setEnabled(false);
                        importButton.setEnabled(false);
                    } else {
                        pathSelection.setText(path_uri.toString());
                        exportButton.setEnabled(true);
                        importButton.setEnabled(true);
                    }
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST:
                if(grantResults.length > 0 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                    // permissions granted
                } else {
                    showErrorMessage("Write Permissions Required", "External storage write permissions are required for this action.");
                }
                break;
            case READ_EXTERNAL_STORAGE_PERMISSION_REQUEST:
                if(grantResults.length > 0 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                    // permissions granted
                } else {
                    showErrorMessage("Read Permissions Required", "External storage read permissions are required for this action.");
                }
                break;
            case CAMERA_PERMISSION_REQUEST:
                if(grantResults.length > 0 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                    // permissions granted
                } else {
                    showErrorMessage("Camera Permissions Required", "Camera access permissions for external storage are required for this action.");
                }
                break;
        }
    }
}
