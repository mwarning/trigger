package app.trigger;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Iterator;


public class BackupActivity extends AppCompatActivity {
    private static final int READ_REQUEST_CODE = 0x01;
    private static final int WRITE_REQUEST_CODE = 0x02;
    private AlertDialog.Builder builder;
    private Button exportButton;
    private Button importButton;

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

        importButton.setOnClickListener((View v) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, READ_REQUEST_CODE);
        });

        exportButton.setOnClickListener((View v) -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, "trigger-backup.json");
            intent.setType("application/json");
            startActivityForResult(intent, WRITE_REQUEST_CODE);
        });
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
        case READ_REQUEST_CODE:
            importSetups(data.getData());
            break;
        case WRITE_REQUEST_CODE:
            exportSetups(data.getData());
            break;
        }
    }

    private void exportSetups(Uri uri) {
        try {
            JSONObject obj = new JSONObject();

            int count = 0;
            for (Setup setup : Settings.getSetups()) {
                JSONObject json_obj = Settings.toJsonObject(setup);
                json_obj.remove("id");
                obj.put(setup.getName(), json_obj);
                count += 1;
            }

            Utils.writeFile(this, uri, obj.toString().getBytes());
            Toast.makeText(this, "Exported " + count + " entries.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            showErrorMessage("Error", e.toString());
        }
    }

    private void importSetups(Uri uri) {
        try {
            byte[] data = Utils.readFile(this, uri);

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
}
