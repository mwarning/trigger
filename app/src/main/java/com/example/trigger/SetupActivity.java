package com.example.trigger;

import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceCategory;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.preference.PreferenceManager;
import android.util.Log;


public class SetupActivity extends PreferenceActivity {
    private AlertDialog.Builder builder;
    private int setup_id;
    private String type;

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    public void onSaveButtonClicked(View v) {
        String name = getText("prefName");

        if (name == null || name.length() == 0) {
            showErrorMessage("Invalid Name", "Name is not set.");
            return;
        }

        // duplicate entry
        if (Settings.id_exists(setup_id) && Settings.name_exists(name)) {
           showErrorMessage("Entry Exists", "Name already exists.");
           return;
        }

        if (type == "sphincter") {
            // remove any existing entry (does not exist for new entries)
            Settings.remove_setup(setup_id);
            Settings.add_setup(new SphincterSetup(
                setup_id,
                name,
                getText("prefUrl"),
                getText("prefToken"),
                getText("prefSSIDs"),
                getChecked("prefIgnore"))
            );
            Settings.store();
        } else {
            showErrorMessage("Unknown Setup Type", "Cannot store setup.");
        }
    }

    public void onDeleteButtonClicked(View v) {
        builder.setTitle("Confirm");
        builder.setMessage("Really remove item?");
        builder.setCancelable(false); // not necessary

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Settings.remove_setup(setup_id);
                Settings.store();
                // close this dialog and settings
                SetupActivity.this.finish();
            }
        });

        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // close this dialog
                dialog.cancel();
            }
        });

        // create dialog box
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void setTitle(String name) {
        String prefix = getResources().getString(R.string.pref_settings);
        PreferenceCategory pc = (PreferenceCategory) findPreference("prefCategory");
        if (pc != null) {
            pc.setTitle(prefix + ": " + name);
        } else {
            Log.e("SetupActivity.setTitle", "Cannot find prefCategory");
        }
    }

    private void setText(String key, String text) {
        EditTextPreference etp = (EditTextPreference) findPreference(key);
        if (etp != null) {
            etp.setText(text);
        } else {
            Log.e("SetupActivity.setText", "Cannot find " + key);
        }
    }

    private String getText(String key) {
        EditTextPreference etp = (EditTextPreference) findPreference(key);
        if (etp != null) {
            return etp.getText();
        } else {
            Log.e("SetupActivity.setText", "Cannot find " + key);
            return "";
        }
    }

    private void setChecked(String key, boolean checked) {
        CheckBoxPreference cbp = (CheckBoxPreference) findPreference(key);
        if (cbp != null) {
            cbp.setChecked(checked);
        } else {
            Log.e("SetupActivity.setChecked", "Cannot find " + key);
        }
    }

    private boolean getChecked(String key) {
        CheckBoxPreference cbp = (CheckBoxPreference) findPreference(key);
        if (cbp != null) {
            return cbp.isChecked();
        } else {
            Log.e("SetupActivity.setChecked", "Cannot find " + key);
            return false;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Context context = this.getApplicationContext();

        // Set all field to default values - does not work?
        //PreferenceManager.setDefaultValues(context, R.xml.settings, false);

        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        setContentView(R.layout.activity_settings);

        int id = getIntent().getIntExtra("setup_id", -1);
        builder = new AlertDialog.Builder(this);
        type = "sphincter";

        if (Settings.id_exists(id)) {
            setup_id = id;
            
            Setup item = Settings.find_setup(setup_id);
            if (item instanceof SphincterSetup) {
                SphincterSetup obj = (SphincterSetup) item;
                setTitle(obj.name);
                setText("prefName", obj.name);
                setText("prefUrl", obj.url);
                setText("prefToken", obj.token);
                setText("prefSSIDs", obj.ssids);
                setChecked("prefIgnore", obj.ignore);
            } else {
                Log.e("onCreate", "No setup found for id " + setup_id);
                // close activity
                finish();
            }
        } else {
            setup_id = Settings.id_new();
            setTitle(getResources().getString(R.string.new_entry));
            setText("prefName", "");
            setText("prefUrl", "");
            setText("prefToken", "");
            setText("prefSSIDs", "");
            setChecked("prefIgnore", false);
        }
    }
}
