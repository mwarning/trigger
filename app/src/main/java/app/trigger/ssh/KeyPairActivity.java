package app.trigger.ssh;

import app.trigger.Log;
import app.trigger.Utils;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import app.trigger.R;

public class KeyPairActivity extends AppCompatActivity implements
        RegisterIdentityTask.OnTaskCompleted,
        GenerateIdentityTask.OnTaskCompleted {
    private static final String TAG = "KeyPairActivity";
    private static final int IMPORT_PRIVATE_KEY_CODE = 0x01;
    private static final int EXPORT_PUBLIC_KEY_CODE = 0x02;
    private static final int EXPORT_PRIVATE_KEY_CODE = 0x03;
    private KeyPairPreference preference; // hack
    private AlertDialog.Builder builder;
    private Button createButton;
    private Button importPrivateKeyButton;
    private Button exportPublicKeyButton;
    private Button exportPrivateKeyButton;
    private Button cancelButton;
    private Button registerButton;
    private Button okButton;
    private Button deleteButton;
    private TextView fingerprint;
    private TextView publicKey;
    private EditText registerAddress;
    private Spinner keyTypeSpinner;

    private KeyPairTrigger keypair;
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
        importPrivateKeyButton = findViewById(R.id.ImportPrivateKeyButton);
        exportPublicKeyButton = findViewById(R.id.ExportPublicKeyButton);
        exportPrivateKeyButton = findViewById(R.id.ExportPrivateKeyButton);
        cancelButton = findViewById(R.id.CancelButton);
        registerButton = findViewById(R.id.RegisterButton);
        okButton = findViewById(R.id.OkButton);
        deleteButton = findViewById(R.id.DeleteButton);
        fingerprint = findViewById(R.id.Fingerprint);
        publicKey = findViewById(R.id.PublicKey);
        registerAddress = findViewById(R.id.RegisterAddress);
        keyTypeSpinner = findViewById(R.id.key_type_spinner);
        final KeyPairActivity self = this;

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_item,
            getResources().getStringArray(R.array.SshKeyTypes)
        );
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keyTypeSpinner.setAdapter(dataAdapter);
        keyTypeSpinner.setSelection(0);

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
                showErrorMessage("Busy", "Key generation already in progress. Please wait.");
            } else {
                keyGenInProgress = true;
                String type = "";
                switch (self.keyTypeSpinner.getSelectedItemPosition()) {
                    case 0:
                        type = "ED25519";
                        break;
                    case 1:
                        type = "ECDSA-384";
                        break;
                    case 2:
                        type = "ECDSA-521";
                        break;
                    case 3:
                        type = "RSA-2048";
                        break;
                    case 4:
                        type = "RSA-4096";
                        break;
                    default:
                        Log.e(TAG, "Invalid selected item position");
                        type = "";
                        break;
                }

                if (!type.isEmpty()) {
                    new GenerateIdentityTask(self).execute(type);
                }
            }
        });

        exportPublicKeyButton.setOnClickListener((View v) -> {
            if (keypair == null) {
                showErrorMessage("No Key", "No key loaded to export.");
            } else {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa.pub");
                intent.setType("*/*");
                startActivityForResult(intent, EXPORT_PUBLIC_KEY_CODE);
            }
        });

        exportPrivateKeyButton.setOnClickListener((View v) -> {
            if (keypair == null) {
                showErrorMessage("No Key", "No key loaded to export.");
            } else {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
                intent.setType("*/*");
                startActivityForResult(intent, EXPORT_PRIVATE_KEY_CODE);
            }
        });

        importPrivateKeyButton.setOnClickListener((View v) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
            intent.setType("*/*");
            startActivityForResult(intent, IMPORT_PRIVATE_KEY_CODE);
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
    }

    @Override
    public void onGenerateIdentityTaskCompleted(String message, KeyPairTrigger keypair) {
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

    private void exportPublicKey(Uri uri) {
        if (keypair == null) {
            showErrorMessage("No Key Pair", "No key loaded to export.");
            return;
        }

        try {
            Utils.writeFile(this, uri, keypair.getData().getBytes());

            Toast.makeText(getApplicationContext(), "Done. Wrote public key: " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    private void exportPrivateKey(Uri uri) {
        if (keypair == null) {
            showErrorMessage("No Key", "No key loaded to export.");
            return;
        }

        try {
            Utils.writeFile(this, uri, keypair.getData().getBytes());

            Toast.makeText(getApplicationContext(), "Done. Wrote private key: " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    private void importPrivateKey(Uri uri) {
        try {
            String privateKeyPEM = new String(Utils.readFile(this, uri));

            if (!SshTools.isPrivateKey(privateKeyPEM)) {
                throw new Exception("Not a valid key!");
            }

            keypair = new KeyPairTrigger(privateKeyPEM);

            updateKeyInfo();

            Toast.makeText(getApplicationContext(), "Done. Read " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show();
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
        case EXPORT_PUBLIC_KEY_CODE:
            exportPublicKey(data.getData());
            break;
        case EXPORT_PRIVATE_KEY_CODE:
            exportPrivateKey(data.getData());
            break;
        case IMPORT_PRIVATE_KEY_CODE:
            importPrivateKey(data.getData());
            break;
        }
    }

    private void updateKeyInfo() {
        if (keypair == null) {
            deleteButton.setEnabled(false);
            exportPublicKeyButton.setEnabled(false);
            exportPrivateKeyButton.setEnabled(false);
            fingerprint.setText("<no key loaded>");
            publicKey.setText("<no key loaded>");
        } else {
            deleteButton.setEnabled(true);
            exportPublicKeyButton.setEnabled(true);
            exportPrivateKeyButton.setEnabled(true);
            fingerprint.setText(keypair.getFingerPrint());
            publicKey.setText(keypair.getPublicKeyPEM());
        }
    }
}
