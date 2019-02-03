package com.example.trigger;

import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.preference.PreferenceManager;
import android.util.Log;
import java.util.ArrayList;


public class EditActivity extends PreferenceActivity {
    private int setup_id;
    private String type;
    private SharedPreferences pref;
    private AlertDialog.Builder builder;

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private boolean validateName(String name) {
        if (name == null || name.length() == 0) {
            showErrorMessage("Invalid Name", "Name is not set.");
            return false;
        } else if (Settings.name_exists(pref, name)) {
           showErrorMessage("Invalid Name", "Name already exists.");
           return false;
        }
        return true;
    }

    public void onSaveButtonClicked(View v) {
        String name = pref.getString("prefName", "");
        Log.d("EditActivity", "onSafeButtonClicked: name: " + name);

        if (validateName(name)) {
            Log.d("EditActivity.onSafeButtonClicked", "valid name: " + name + ", type: " + type);
            if (type == "sphincter") {
                Log.d("EditActivity.onSafeButtonClicked", "save entry: " + name);
                Settings.safeItem(pref, new SphincterSetup(
                    setup_id,
                    name,
                    pref.getString("prefUrl", ""),
                    pref.getString("prefToken", ""),
                    pref.getBoolean("prefIgnore", false))
                );
            }
        }
    }

    public void onDeleteButtonClicked(View v) {
        Log.d("EditActivity", "onDeleteButtonClicked()");

        builder.setTitle("Confirm");
        builder.setMessage("Really remove item?");
        builder.setCancelable(false); // not necessary

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Log.d("onDeleteButtonClicked", "onClick Yes");
                Settings.deleteItem(pref, setup_id);
                // close this dialog and settings
                EditActivity.this.finish();
            }
        });

        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Log.d("onDeleteButtonClicked", "onClick No");
                // close this dialog
                dialog.cancel();
            }
        });

        // create dialog box
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d("EditActivity", "onCreate()");
        int id = getIntent().getIntExtra("setup_id", -1);
        builder = new AlertDialog.Builder(this);
        type = "sphincter";

        Context context = this.getApplicationContext();
        pref = PreferenceManager.getDefaultSharedPreferences(context);

        ArrayList<Integer> ids = Settings.get_all_ids(pref);
        if (ids.contains(id)) {
            setup_id = id;
            Log.d("onCreate", "old entry: " + setup_id);
            
            Setup item = Settings.getItem(pref, setup_id);
            if (item instanceof SphincterSetup) {
                SphincterSetup obj = (SphincterSetup) item;
                SharedPreferences.Editor e = pref.edit();
                e.putString("prefName", obj.name);
                e.putString("prefUrl", obj.url);
                e.putString("prefToken", obj.token);
                e.putBoolean("prefIgnore", obj.ignore);
                e.commit();
            } else {
                Log.d("onCreate", "No setup found for id " + setup_id);
            }
        } else {
            setup_id = Settings.newId(pref);
            Log.d("onCreate", "new entry: " + setup_id);
            // Set all field empty
            SharedPreferences.Editor e = pref.edit();
            e.putString("prefName", "");
            e.putString("prefUrl", "");
            e.putString("prefToken", "");
            e.putBoolean("prefIgnore", false);
            e.commit();
        }

        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        setContentView(R.layout.activity_settings);
    }

    @Override
    public void onResume() {
        Log.d("EditActivity", "onResume()");
        super.onResume();
    }

    @Override
    public void onDestroy() {
        Log.d("EditActivity", "onDestroy");
        super.onDestroy();
    }
}
