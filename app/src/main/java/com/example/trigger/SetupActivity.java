package com.example.trigger;

import android.content.DialogInterface;
import android.os.Bundle;
import android.content.Context;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;

import static com.example.trigger.Utils.*;



public class SetupActivity extends PreferenceActivity {
    private ArrayList<PreferenceGroup> setupGroups;
    private AlertDialog.Builder builder;
    private Setup setup;

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    // collect all PreferenceGroups except first
    private ArrayList<PreferenceGroup> collectGroups() {
        ArrayList<PreferenceGroup> groups = new ArrayList();
        int count = getPreferenceScreen().getPreferenceCount();
        for (int i = count - 1; i > 0; i -= 1) {
            PreferenceGroup p = (PreferenceGroup) getPreferenceScreen().getPreference(i);
            if (p != null) {
                groups.add(p);
                getPreferenceScreen().removePreference(p);
            }
        }
        return groups;
    }

    private void showGroup(String key) {
        Log.d("SetupActivity.showGroup()", "show: " + key);

        // hide all groups
        for (int i = 0; i < setupGroups.size(); i += 1) {
            getPreferenceScreen().removePreference(setupGroups.get(i));
        }

        // show specific group
        for (int i = 0; i < setupGroups.size(); i += 1) {
            if (setupGroups.get(i).getKey().equals(key)) {
                PreferenceGroup group = setupGroups.get(i);
                getPreferenceScreen().addPreference(group);
                return;
            }
        }
        Log.e("SetupActivity.showGroup()", "PreferenceGroup not found: " + key);
    }

    public void onSaveButtonClicked(View v) {
        Log.d("SetupActivity.onSaveButtonClicked", "called");
        storeSetup();
    }

    public void onDeleteButtonClicked(View v) {
        Log.d("SetupActivity.onDeleteButtonClicked", "called");

        builder.setTitle("Confirm");
        builder.setMessage("Really remove item?");
        builder.setCancelable(false); // not necessary

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Settings.removeSetup(setup.getId());
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

    private void setMainGroupTitle(String name) {
        PreferenceCategory pc = (PreferenceCategory) findPreference("main_category");
        if (pc != null) {
            //String prefix = getResources().getString(R.string.setup_title);
            if (name.length() > 0) {
                pc.setTitle(name);
            } else {
                pc.setTitle(getResources().getString(R.string.new_entry));
            }
        } else {
            Log.e("SetupActivity.setTitle", "Cannot find main_category");
        }
    }

    private static void setText(PreferenceGroup group, String key, String text) {
        EditTextPreference etp = (EditTextPreference) group.findPreference(key);
        if (etp != null) {
            etp.setText(text);
        } else {
            Log.e("SetupActivity.setText", "Cannot find key: " + key);
        }
    }

    private static String getText(PreferenceGroup group, String key) {
        EditTextPreference etp = (EditTextPreference) group.findPreference(key);
        if (etp != null) {
            return etp.getText();
        } else {
            Log.e("SetupActivity.setText", "Cannot find key: " + key);
            return "";
        }
    }

    private static void setChecked(PreferenceGroup group, String key, boolean checked) {
        CheckBoxPreference cbp = (CheckBoxPreference) group.findPreference(key);
        if (cbp != null) {
            cbp.setChecked(checked);
        } else {
            Log.e("SetupActivity.setChecked", "Cannot find key: " + key);
        }
    }

    private static boolean getChecked(PreferenceGroup group, String key) {
        CheckBoxPreference cbp = (CheckBoxPreference) group.findPreference(key);
        if (cbp != null) {
            return cbp.isChecked();
        } else {
            Log.e("SetupActivity.setChecked", "Cannot find key: " + key);
            return false;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Context context = this.getApplicationContext();

        // Set all field to default values - does not work?
        //PreferenceManager.setDefaultValues(context, R.xml.settings, false);
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.setup);
        setContentView(R.layout.activity_setup);

        // change door type
        ListPreference list_field = (ListPreference) findPreference("type");
        list_field.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String type = newValue.toString();
                Log.d("SetupActivity", "onPreferenceChange: " + type);

                if (type.equals(setup.getType())) {
                    // no door type change
                    return true;
                } else if (type.equals(HttpsDoorSetup.type)) {
                    setup = new HttpsDoorSetup(setup.getId(), setup.getName());
                    loadSetup();
                    return true;
                } else if (type.equals(SshDoorSetup.type)) {
                    setup = new SshDoorSetup(setup.getId(), setup.getName());
                    loadSetup();
                    return true;
                } else {
                    Log.e("SetupActivity", "Unhandled type from selection: " + type);
                    return false;
                }
            }
        });

        // update main category title
        EditTextPreference name_field = (EditTextPreference) findPreference("name");
        name_field.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String name = newValue.toString();
                setMainGroupTitle(name);
                return true;
            }
        });

        builder = new AlertDialog.Builder(this);
        setupGroups = collectGroups();

        int id = getIntent().getIntExtra("setup_id", -1);

        Log.d("SetupActivity", "id: " + id);
        if (Settings.idExists(id)) {
            Log.d("SetupActivity", "idExists");
            setup = Settings.getSetup(id);
        } else {
            setup = new HttpsDoorSetup(Settings.getNewID(), "");
            Log.d("SetupActivity", "create new setup: " + setup.getId());
        }

        //Log.d("class name", "" + setup.getClass().getSimpleName());
        // init type selection
        list_field.setValue(setup.getType());
        loadSetup();
    }

    PreferenceGroup findPreferenceGroup(String key) {
        return (PreferenceGroup) getPreferenceScreen().findPreference(key);
    }

    // settings that are not to be displayed in the sub category
    private boolean ignoredSetting(String key) {
        return key.equals("type") || key.equals("id");
    }

    void loadSetup() {
        String type = setup.getType();
        showGroup(type);

        // get setup settings
        ArrayList<Pair> pairs = new ArrayList();
        this.setup.getAllSettings(pairs);

        // set settings in GUI
        PreferenceGroup group = findPreferenceGroup(type);
        for (Pair pair : pairs) {
            Log.d("SetupActivity.show", "entry: " + pair.key + ": " + pair.value.toString());
            if (pair.key.equals("name")) {
                setText(getPreferenceScreen(), "name", (String) pair.value);
            } else if (ignoredSetting(pair.key)) {
                // ignore - already displayed in main group
            } else if (pair.value instanceof String) {
                setText(group, pair.key, (String) pair.value);
            } else if (pair.value instanceof Boolean) {
                setChecked(group, pair.key, (Boolean) pair.value);
            } else {
                Log.e("SetupActivity", "Unknown value type for " + type + "." + pair.key);
            }
        }

        // update title
        setMainGroupTitle(setup.getName());
    }

    void storeSetup() {
        String name = getText(getPreferenceScreen(), "name");
        Log.d("SetupActivity", "storeSetup: name: " + name);

        if (name == null || name.length() == 0) {
            showErrorMessage("Invalid Name", "Name is not set.");
            return;
        }

        // duplicate entry
        if (!Settings.idExists(setup.getId()) && Settings.nameExists(name)) {
           showErrorMessage("Entry Exists", "Name already exists.");
           return;
        }

        // get settings
        ArrayList<Pair> pairs = new ArrayList();
        this.setup.getAllSettings(pairs);

        String type = this.setup.getType();
        PreferenceGroup group = findPreferenceGroup(type);
        for (Pair pair : pairs) {
            if (pair.key == "name") {
                pair.value = getText(getPreferenceScreen(), "name");
            } else if (pair.value instanceof String) {
                pair.value = getText(group, pair.key);
            } else if (pair.value instanceof Boolean) {
                pair.value = getChecked(group, pair.key);
            } else {
                Log.e("SetupActivity", "Unknown value type for " + type + "." + pair.key);
            }
        }

        this.setup.setAllSettings(pairs);
        Settings.saveSetup(this.setup);
    }
}
