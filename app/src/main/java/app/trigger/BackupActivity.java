package app.trigger;

import com.codekidlabs.storagechooser.StorageChooser;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;


public class BackupActivity extends AppCompatActivity implements
        StorageChooser.OnSelectListener, StorageChooser.OnCancelListener {
    private static final int REQUEST_PERMISSION = 0x01;
    private AlertDialog.Builder builder;
    private Button exportButton;
    private Button importButton;
    private ImageButton selectButton;
    private TextView pathEditText;

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        builder = new AlertDialog.Builder(this);
        importButton = findViewById(R.id.ImportButton);
        exportButton = findViewById(R.id.ExportButton);
        selectButton = findViewById(R.id.SelectButton);
        pathEditText = findViewById(R.id.PathEditText);

        importButton.setOnClickListener((View v) -> {
            if (!Utils.hasReadPermission(BackupActivity.this)) {
                Utils.requestReadPermission(BackupActivity.this, REQUEST_PERMISSION);
                return;
            }
            importSetups();
        });

        exportButton.setOnClickListener((View v) -> {
            if (!Utils.hasReadPermission(BackupActivity.this)) {
                Utils.requestReadPermission(BackupActivity.this, REQUEST_PERMISSION);
                return;
            }

            if (!Utils.hasWritePermission(BackupActivity.this)) {
                Utils.requestWritePermission(BackupActivity.this, REQUEST_PERMISSION);
                return;
            }

            exportSetups();
        });

        selectButton.setOnClickListener((View v) -> {
            if (Utils.hasReadPermission(BackupActivity.this)) {
                StorageChooser chooser = new StorageChooser.Builder()
                    .withActivity(this)
                    .withFragmentManager(getFragmentManager())
                    .allowCustomPath(true)
                    .setType(StorageChooser.DIRECTORY_CHOOSER)
                    .build();
                chooser.show();

                // get path that the user has chosen
                chooser.setOnSelectListener(this);
                chooser.setOnCancelListener(this);
            } else {
                Utils.requestReadPermission(BackupActivity.this, REQUEST_PERMISSION);
            }
        });
    }

    private void exportSetups() {
        String path = pathEditText.getText().toString();

        if (path.isEmpty()) {
            showErrorMessage("Empty Path", "No path selected.");
            return;
        }

        if ((new File(path)).isDirectory() || path.endsWith("/")) {
            showErrorMessage("Invalid Path", "Not a file name.");
            return;
        }

        if ((new File(path)).exists()) {
            showErrorMessage("File Exists", "Cannot overwrite existing file.");
            return;
        }

        try {
            JSONObject obj = new JSONObject();

            int count = 0;
            for (Setup setup : Settings.getSetups()) {
                JSONObject json_obj = Settings.toJsonObject(setup);
                json_obj.remove("id");
                obj.put(setup.getName(), json_obj);
                count += 1;
            }

            Utils.writeExternalFile(path, obj.toString().getBytes());

            Toast.makeText(this, "Exported " + count + " entries.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.toString());
        }
    }

    private void importSetups() {
        String path = pathEditText.getText().toString();

        if (path.isEmpty()) {
            showErrorMessage("Empty Path", "No path selected.");
            return;
        }

        if ((new File(path)).isDirectory() || path.endsWith("/")) {
            showErrorMessage("Invalid Path", "Not a file name.");
            return;
        }

        try {
            byte[] data = Utils.readExternalFile(path);
            JSONObject json_data = new JSONObject(
                new String(data, 0, data.length, "UTF-8")
            );

            int count = 0;
            Iterator<String> keys = json_data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject obj = json_data.getJSONObject(key);
                obj.put("id", Settings.getNewID());
                Setup setup = Settings.fromJsonObject(obj);
                Settings.addSetup(setup);
                count += 1;
            }
            Toast.makeText(this, "Imported setups: " + count, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.toString());
        }
    }

    // for StorageChooser
    @Override
    public void onSelect(String path) {
        if ((new File(path)).isDirectory()) {
            // append slash
            if (!path.endsWith("/")) {
                path += "/";
            }
            path += "trigger-backup.json";
        }
        pathEditText.setText(path);
    }

    // for StorageChooser
    @Override
    public void onCancel() {
        // nothing to do
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
