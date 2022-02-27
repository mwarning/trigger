package app.trigger.mqtt;

import app.trigger.R;
import app.trigger.Utils;
import app.trigger.ssh.KeyPairBean;
import app.trigger.ssh.SshTools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;


public class MqttClientKeyPairActivity extends AppCompatActivity {
    private static final int IMPORT_PRIVATE_KEY_CODE = 0x01;
    private static final int EXPORT_PRIVATE_KEY_CODE = 0x03;
    private MqttClientKeyPairPreference preference; // hack
    private AlertDialog.Builder builder;
    private ClipboardManager clipboard;
    private Button importPrivateKeyButton;
    private Button exportPrivateKeyButton;
    private CheckBox useClipboardCheckBox;
    private CheckBox useFilesystemCheckBox;
    private Button cancelButton;
    private Button okButton;
    private Button deleteButton;
    private TextView privateKey;
    private KeyPairBean keypair; // only contains private key here

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mqtt_client_keypair);

        this.preference = MqttClientKeyPairPreference.self; // hack, TODO: pass serialized key in bundle
        this.keypair = this.preference.getKeyPair();
        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        builder = new AlertDialog.Builder(this);
        importPrivateKeyButton = findViewById(R.id.ImportPrivateKeyButton);
        exportPrivateKeyButton = findViewById(R.id.ExportPrivateKeyButton);
        useClipboardCheckBox = findViewById(R.id.UseClipboardCheckBox);
        useFilesystemCheckBox = findViewById(R.id.UseFilesystemCheckBox);
        cancelButton = findViewById(R.id.CancelButton);
        okButton = findViewById(R.id.OkButton);
        deleteButton = findViewById(R.id.DeleteButton);
        privateKey = findViewById(R.id.PrivateKey);

        // toggle between both checkboxes
        useClipboardCheckBox.setOnClickListener((View v) -> {
            useFilesystemCheckBox.setChecked(!useClipboardCheckBox.isChecked());
        });

        // toggle between both checkboxes
        useFilesystemCheckBox.setOnClickListener((View v) -> {
            useClipboardCheckBox.setChecked(!useFilesystemCheckBox.isChecked());
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
            // update the SwitchPreference
            preference.setKeyPair(MqttClientKeyPairActivity.this.keypair);
            MqttClientKeyPairActivity.this.finish();
        });

        deleteButton.setOnClickListener((View v) -> {
            builder.setTitle("Confirm");
            builder.setMessage("Really remove client private key?");
            builder.setCancelable(false); // not necessary

            builder.setPositiveButton(R.string.yes, (DialogInterface dialog, int id) -> {
                MqttClientKeyPairActivity.this.keypair = null;
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
            MqttClientKeyPairActivity.this.finish();
        });

        updateKeyInfo(this.keypair);
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
        TextView tv = findViewById(R.id.PrivateKeyTextView);

        if (keypair == null) {
            deleteButton.setEnabled(false);
            exportPrivateKeyButton.setEnabled(false);
            privateKey.setText("<no key loaded>");
            tv.setText(res.getString(R.string.private_key, ""));
        } else {
            deleteButton.setEnabled(true);
            exportPrivateKeyButton.setEnabled(true);
            privateKey.setText(keypair.getOpenSSHPublicKey());
            tv.setText(keypair.getDescription());
            tv.setText(res.getString(R.string.private_key, keypair.getDescription()));
        }
    }
}
