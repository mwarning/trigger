package app.trigger.ssh;

import app.trigger.Log;
import app.trigger.Utils;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import app.trigger.R;

import com.codekidlabs.storagechooser.StorageChooser;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;


public class KeyPairActivity extends AppCompatActivity implements
        StorageChooser.OnSelectListener, StorageChooser.OnCancelListener,
        RegisterIdentityTask.OnTaskCompleted,
        GenerateIdentityTask.OnTaskCompleted {
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

        registerButton.setOnClickListener((View v) -> {
            String address = registerAddress.getText().toString();
            if (address == null || address.length() == 0) {
                showErrorMessage("Address Empty", "Address and port needed to send public key to destination.");
            } else if (keypair == null) {
                showErrorMessage("Key Pair Empty", "No public key available to register.");
            } else {
                RegisterIdentityTask task = new RegisterIdentityTask(self, address, keypair);
                task.start();
            }
        });

        createButton.setOnClickListener((View v) -> {
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
        });

        selectButton.setOnClickListener((View v) -> {
            if (Utils.hasReadPermission(self)) {
                StorageChooser chooser = new StorageChooser.Builder()
                    .withActivity(KeyPairActivity.this)
                    .withFragmentManager(getFragmentManager())
                    .allowCustomPath(true)
                    .setType(StorageChooser.DIRECTORY_CHOOSER)
                    .build();
                chooser.show();

                // get path that the user has chosen
                chooser.setOnSelectListener(KeyPairActivity.this);
                chooser.setOnCancelListener(KeyPairActivity.this);
            } else {
                Utils.requestReadPermission(self, REQUEST_PERMISSION);
                Utils.requestWritePermission(self, REQUEST_PERMISSION);
            }
        });

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportKeys();
            }
        });

        importButton.setOnClickListener((View v) -> {
            importKeys();
        });

        okButton.setOnClickListener((View v) -> {
            // persist your value here
            preference.setKeyPair(self.keypair);
            self.finish();
        });

        deleteButton.setOnClickListener((View v) -> {
            builder.setTitle("Confirm");
            builder.setMessage("Really remove key pair?");
            builder.setCancelable(false); // not necessary

            builder.setPositiveButton(R.string.yes, (DialogInterface dialog, int id) -> {
                self.keypair = null;
                updateKeyInfo();
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
            self.finish();
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
        this.runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

    private void exportKeys() {
        if (selected_path == null) {
            showErrorMessage("No Directory Selected", "No directory for export selected.");
        } else if (keypair == null) {
            showErrorMessage("No Key Pair", "No keys loaded to export.");
        } else if (!Utils.hasWritePermission(this)) {
            Utils.requestWritePermission(this, REQUEST_PERMISSION);
        } else try {
            SshTools.KeyPairData data = SshTools.keypairToBytes(keypair, null);

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

            if (Utils.arrayIndexOf(prvkey, "-----BEGIN OPENSSH PRIVATE KEY-----".getBytes()) != -1) {
                throw new Exception("OpenSSH key format detected - please convert to RSA format.");
            }

            JSch jsch = new JSch();
            keypair = KeyPair.load(jsch, prvkey, pubkey);

            updateKeyInfo();

            Toast.makeText(getApplicationContext(), "Done. Read 'id_rsa.pub' and 'id_rsa'.", Toast.LENGTH_SHORT).show();
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

    private void updateKeyInfo() {
        SshTools.KeyPairData data = null;
        if (keypair != null) {
            data = SshTools.keypairToBytes(keypair, null);
        }

        if (keypair == null || data == null || data.prvkey == null || data.pubkey == null) {
            deleteButton.setEnabled(false);
            fingerprint.setText("<no key loaded>");
            publicKey.setText("<no key loaded>");
        } else {
            deleteButton.setEnabled(true);
            fingerprint.setText(keypair.getFingerPrint());
            publicKey.setText(new String(data.pubkey));
        }
    }

    private void updatePathInfo() {
        if (selected_path == null) {
            pathSelection.setText("<no path selected>");
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
