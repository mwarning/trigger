package app.trigger.ssh;

import app.trigger.Log;
import app.trigger.Utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import app.trigger.R;

public class SshKeyPairActivity extends AppCompatActivity implements
        RegisterIdentityTask.OnTaskCompleted,
        GenerateIdentityTask.OnTaskCompleted {
    private static final String TAG = "KeyPairActivity";
    private static final int IMPORT_PRIVATE_KEY_CODE = 0x01;
    private static final int EXPORT_PUBLIC_KEY_CODE = 0x02;
    private static final int EXPORT_PRIVATE_KEY_CODE = 0x03;
    private SshKeyPairPreference preference; // hack
    private AlertDialog.Builder builder;
    private ClipboardManager clipboard;
    private Button createButton;
    private Button importPrivateKeyButton;
    private Button exportPublicKeyButton;
    private Button exportPrivateKeyButton;
    private CheckBox useClipboardCheckBox;
    private CheckBox useFilesystemCheckBox;
    private Button cancelButton;
    private Button registerButton;
    private Button okButton;
    private Button deleteButton;
    private TextView publicKey;
    private EditText registerAddress;
    private Spinner keyTypeSpinner;

    private KeyPairBean keypair;
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

        this.preference = SshKeyPairPreference.self; // hack, TODO: pass serialized key in bundle

        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        builder = new AlertDialog.Builder(this);
        createButton = findViewById(R.id.CreateButton);
        importPrivateKeyButton = findViewById(R.id.ImportPrivateKeyButton);
        exportPublicKeyButton = findViewById(R.id.ExportPublicKeyButton);
        exportPrivateKeyButton = findViewById(R.id.ExportPrivateKeyButton);
        useClipboardCheckBox = findViewById(R.id.UseClipboardCheckBox);
        useFilesystemCheckBox = findViewById(R.id.UseFilesystemCheckBox);
        cancelButton = findViewById(R.id.CancelButton);
        registerButton = findViewById(R.id.RegisterButton);
        okButton = findViewById(R.id.OkButton);
        deleteButton = findViewById(R.id.DeleteButton);
        publicKey = findViewById(R.id.PublicKey);
        registerAddress = findViewById(R.id.RegisterAddress);
        keyTypeSpinner = findViewById(R.id.KeyTypeSpinner);
        final SshKeyPairActivity self = this;

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

        // toggle between both checkboxes
        useClipboardCheckBox.setOnClickListener((View v) -> {
            useFilesystemCheckBox.setChecked(!useClipboardCheckBox.isChecked());
        });

        // toggle between both checkboxes
        useFilesystemCheckBox.setOnClickListener((View v) -> {
            useClipboardCheckBox.setChecked(!useFilesystemCheckBox.isChecked());
        });

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
            } else if (useClipboardCheckBox.isChecked()) {
                String publicKey = keypair.getOpenSSHPublicKey();
                ClipData clip = ClipData.newPlainText(keypair.getDescription(), publicKey);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getApplicationContext(), "Done.", Toast.LENGTH_SHORT).show();
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
            } else if (useClipboardCheckBox.isChecked()) {
                String privateKey = keypair.getOpenSSHPrivateKey();
                ClipData clip = ClipData.newPlainText(keypair.getDescription(), privateKey);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getApplicationContext(), "Done.", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
                intent.setType("*/*");
                startActivityForResult(intent, EXPORT_PRIVATE_KEY_CODE);
            }
        });

        importPrivateKeyButton.setOnClickListener((View v) -> {
            if (useClipboardCheckBox.isChecked()) {
                if (clipboard.hasPrimaryClip()) {
                    String privateKey = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
                    KeyPairBean kp = SshTools.parsePrivateKeyPEM(privateKey);
                    if (kp != null) {
                        Toast.makeText(getApplicationContext(), "Done.", Toast.LENGTH_SHORT).show();
                        updateKeyInfo(kp);
                    } else {
                        Toast.makeText(getApplicationContext(), "Import Failed.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Clipboard is empty.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                //intent.putExtra(Intent.EXTRA_TITLE, "id_rsa");
                intent.setType("*/*");
                startActivityForResult(intent, IMPORT_PRIVATE_KEY_CODE);
            }
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
                updateKeyInfo(null);
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

        updateKeyInfo(this.preference.getKeyPair());
    }

    @Override
    public void onGenerateIdentityTaskCompleted(String message, KeyPairBean keypair) {
        this.keyGenInProgress = false;
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();

        // only set if successful
        if (keypair != null) {
            updateKeyInfo(keypair);
        }
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
            Utils.writeFile(this, uri, keypair.getOpenSSHPublicKey().getBytes());

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
            Utils.writeFile(this, uri, keypair.getOpenSSHPrivateKey().getBytes());

            Toast.makeText(getApplicationContext(), "Done. Wrote private key: " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.getMessage());
        }
    }

    private void importPrivateKey(Uri uri) {
        try {
            String privateKeyPEM = new String(Utils.readFile(this, uri));

            KeyPairBean kp = SshTools.parsePrivateKeyPEM(privateKeyPEM);
            if (kp == null) {
                throw new Exception("Not a valid key!");
            }

            updateKeyInfo(kp);

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

    private void updateKeyInfo(KeyPairBean kp) {
        keypair = kp;

        Resources res = getResources();
        TextView tv = findViewById(R.id.PublicKeyTextView);

        if (keypair == null) {
            deleteButton.setEnabled(false);
            exportPublicKeyButton.setEnabled(false);
            exportPrivateKeyButton.setEnabled(false);
            publicKey.setText("<no key loaded>");
            tv.setText(res.getString(R.string.public_key, ""));
        } else {
            deleteButton.setEnabled(true);
            exportPublicKeyButton.setEnabled(true);
            exportPrivateKeyButton.setEnabled(true);
            publicKey.setText(keypair.getOpenSSHPublicKey());
            tv.setText(keypair.getDescription());
            tv.setText(res.getString(R.string.public_key, keypair.getDescription()));
        }
    }
}
