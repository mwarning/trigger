package com.example.trigger.ssh;

import com.example.trigger.Log;
import com.example.trigger.Utils;
import com.github.isabsent.filepicker.SimpleFilePickerDialog;
import static com.github.isabsent.filepicker.SimpleFilePickerDialog.CompositeMode.FOLDER_ONLY_SINGLE_CHOICE;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.trigger.R;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;


public class KeyPairActivity extends AppCompatActivity implements
        SimpleFilePickerDialog.InteractionListenerString,
        RegisterIdentityTask.OnTaskCompleted,
        GenerateIdentityTask.OnTaskCompleted {
    private static final String SELECT_PATH_REQUEST = "SELECT_PATH_REQUEST";
    private static final int REQUEST_PERMISSION = 0x01;
    private KeyPairPreference preference; // hack
    private AlertDialog.Builder builder;
    private Button createButton;
    private Button importButton;
    private Button exportButton;
    private Button cancelButton;
    private Button selectButton;
    private Button registerButton;
    private Button okButton;
    private Button deleteButton;
    private TextView fingerprint;
    private TextView publicKey;
    private TextView pathSelection;
    private EditText registerAddress;
    private Spinner keypairStrengthSpinner;

    private KeyPair keypair;
    private String selected_path;
    private boolean keyGenInProgress = false;

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
        createButton = findViewById(R.id.CreateButton);
        importButton = findViewById(R.id.ImportButton);
        exportButton = findViewById(R.id.ExportButton);
        cancelButton = findViewById(R.id.CancelButton);
        selectButton = findViewById(R.id.SelectButton);
        registerButton = findViewById(R.id.RegisterButton);
        okButton = findViewById(R.id.OkButton);
        deleteButton = findViewById(R.id.DeleteButton);
        fingerprint = findViewById(R.id.Fingerprint);
        publicKey = findViewById(R.id.PublicKey);
        pathSelection = findViewById(R.id.PathSelection);
        registerAddress = findViewById(R.id.RegisterAddress);
        keypairStrengthSpinner = findViewById(R.id.keypair_strength_spinner);
        final KeyPairActivity self = this;

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.KeypairStrengths)
        );
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keypairStrengthSpinner.setAdapter(dataAdapter);
        keypairStrengthSpinner.setSelection(1);

        registerAddress.setText(
            getIntent().getStringExtra("register_url")
        );

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String address = registerAddress.getText().toString();
                if (address == null || address.length() == 0) {
                    showErrorMessage("Address Empty", "Address and port needed to send public key to destination.");
                } else if (keypair == null) {
                    showErrorMessage("Key Pair Empty", "No public key available to register.");
                } else {
                    new RegisterIdentityTask(self).execute(address, keypair);
                }
            }
        });

        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (keyGenInProgress) {
                    showErrorMessage("In Progress", "Key generation already in progress. Please wait.");
                } else {
                    keyGenInProgress = true;
                    int key_strength = 0;
                    switch (self.keypairStrengthSpinner.getSelectedItemPosition()) {
                        case 0:
                            key_strength = 1024;
                            break;
                        case 1:
                            key_strength = 2048;
                            break;
                        case 2:
                            key_strength = 4096;
                            break;
                        default:
                            Log.e("KeyPairActivity", "Invalid selected item position");
                            key_strength = 0;
                            break;
                    }

                    if (key_strength > 0) {
                        new GenerateIdentityTask(self).execute(key_strength);
                    }
                }
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
                preference.setKeyPair(self.keypair);
                self.finish();
            }
        });

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                builder.setTitle("Confirm");
                builder.setMessage("Really remove key pair?");
                builder.setCancelable(false); // not necessary

                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        KeyPairActivity.this.keypair = null;
                        updateKeyInfo();
                        dialog.cancel();
                    }
                });

                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

                // create dialog box
                AlertDialog alert = builder.create();
                alert.show();
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

    @Override
    public void onGenerateIdentityTaskCompleted(String message, KeyPair keypair) {
        if (keypair != null) {
            // only set if successful
            this.keypair = keypair;
        }

        this.keyGenInProgress = false;
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        updateKeyInfo();
    }

    @Override
    public void onRegisterIdentityTaskCompleted(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
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
            deleteButton.setEnabled(false);
            fingerprint.setText("<no key loaded>");
            publicKey.setText("<no key loaded>");
        } else {
            SshTools.KeyPairData data = SshTools.keypairToBytes(keypair);

            deleteButton.setEnabled(true);
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
