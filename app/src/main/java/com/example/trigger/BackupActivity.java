package com.example.trigger;

import com.example.trigger.Utils;
import com.github.isabsent.filepicker.SimpleFilePickerDialog;

import static com.github.isabsent.filepicker.SimpleFilePickerDialog.CompositeMode.FILE_OR_FOLDER_SINGLE_CHOICE;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.trigger.R;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;


public class BackupActivity extends AppCompatActivity implements
        SimpleFilePickerDialog.InteractionListenerString {
    private static final String SELECT_PATH_REQUEST = "SELECT_PATH_REQUEST";
    private static final int REQUEST_PERMISSION = 0x01;
    private AlertDialog.Builder builder;
    private Button exportButton;
    private Button importButton;
    private Button selectButton;
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
        pathEditText = findViewById(R.id.pathEditText);

        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.hasReadPermission(BackupActivity.this)) {
                    importSetups();
                } else {
                    Utils.requestReadPermission(BackupActivity.this, REQUEST_PERMISSION);
                }
            }
        });

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.hasReadPermission(BackupActivity.this) && Utils.hasWritePermission(BackupActivity.this)) {
                    exportSetups();
                } else {
                    Utils.requestWritePermission(BackupActivity.this, REQUEST_PERMISSION);
                    Utils.requestReadPermission(BackupActivity.this, REQUEST_PERMISSION);
                }
            }
        });

        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.hasReadPermission(BackupActivity.this)) {
                    final String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                    showListItemDialog("Select Path", rootPath, FILE_OR_FOLDER_SINGLE_CHOICE, SELECT_PATH_REQUEST);
                } else {
                    Utils.requestReadPermission(BackupActivity.this, REQUEST_PERMISSION);
                }
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

            Toast.makeText(this, "Exported setups: " + count, Toast.LENGTH_LONG).show();
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
                    String path = extras.getString(SimpleFilePickerDialog.SELECTED_SINGLE_PATH);
                    //setPath(path);
                    if ((new File(path)).isDirectory()) {
                        // append slash
                        if (!path.endsWith("/")) {
                            path += "/";
                        }
                        path += "backup.json";
                    }
                    pathEditText.setText(path);
                }
                break;
        }
        return false;
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
