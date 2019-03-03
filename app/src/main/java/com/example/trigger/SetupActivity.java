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
import android.widget.Toast;

import java.lang.reflect.Field;
import java.security.cert.Certificate;
import java.util.ArrayList;

import com.example.trigger.https.CertificatePreference;
import com.example.trigger.ssh.KeyPairPreference;
import com.jcraft.jsch.KeyPair;


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
        storeSetup();
    }

    public void onDeleteButtonClicked(View v) {
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

    // a recursive method to find a preference by key
    private Preference findAnyPreference(String key, PreferenceGroup group) {
        if (group == null) {
            group = getPreferenceScreen();
        }

        int count = group.getPreferenceCount();
        for (int i = 0; i < count; i += 1) {
            Preference pref = group.getPreference(i);
            if (pref.getKey().equals(key)) {
                return pref;
            } else if (pref instanceof PreferenceGroup) {
                pref = findAnyPreference(key, (PreferenceGroup) pref);
                if (pref != null) {
                    return pref;
                }
            }
        }

        return null;
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

    private void setText(String key, String text) {
        EditTextPreference etp = (EditTextPreference) findAnyPreference(key, null);
        if (etp != null) {
            etp.setText(text);
        } else {
            Log.e("SetupActivity.setText", "Cannot find key: " + key);
        }
    }

    private String getText(String key) {
        EditTextPreference etp = (EditTextPreference) findAnyPreference(key, null);
        if (etp != null) {
            return etp.getText();
        } else {
            Log.e("SetupActivity.getText", "Cannot find key: " + key);
            return "";
        }
    }

    private void setChecked(String key, boolean checked) {
        CheckBoxPreference cbp = (CheckBoxPreference) findAnyPreference(key, null);
        if (cbp != null) {
            cbp.setChecked(checked);
        } else {
            Log.e("SetupActivity.setChecked", "Cannot find key: " + key);
        }
    }

    private boolean getChecked(String key) {
        CheckBoxPreference cbp = (CheckBoxPreference) findAnyPreference(key, null);
        if (cbp != null) {
            return cbp.isChecked();
        } else {
            Log.e("SetupActivity.getChecked", "Cannot find key: " + key);
            return false;
        }
    }

    private KeyPair getKeyPair(String key) {
        KeyPairPreference kpp = (KeyPairPreference) findAnyPreference(key, null);
        if (kpp != null) {
            return kpp.getKeyPair();
        } else {
            Log.e("SetupActivity.getKeyPair", "Cannot find key: " + key);
            return null;
        }
    }

    private void setKeyPair(String key, KeyPair keypair) {
        KeyPairPreference kpp = (KeyPairPreference) findAnyPreference(key, null);
        if (kpp != null) {
            kpp.setKeyPair(keypair);
        } else {
            Log.e("SetupActivity.setKeyPair", "Cannot find key: " + key);
        }
    }

    private Certificate getCertificate(String key) {
        CertificatePreference cp = (CertificatePreference) findAnyPreference(key, null);
        if (cp != null) {
            return cp.getCertificate();
        } else {
            Log.e("SetupActivity.getCertificate", "Cannot find key: " + key);
            return null;
        }
    }

    private void setCertificate(String key, Certificate certificate) {
        CertificatePreference cp = (CertificatePreference) findAnyPreference(key, null);
        if (cp != null) {
            cp.setCertificate(certificate);
        } else {
            Log.e("SetupActivity.setCertificate", "Cannot find key: " + key);
        }
    }

    // Get an URL to fetch a https certificate from
    public String getAnyHttpsUrl() {
        String prefix = "https://";
        String url = "";

        if (setup instanceof HttpsDoorSetup) {
            HttpsDoorSetup s = (HttpsDoorSetup) setup;
            if (s.open_query.startsWith(prefix)) {
                url = s.open_query;
            } else if (s.close_query.startsWith(prefix)) {
                url = s.close_query;
            } else if (s.status_query.startsWith(prefix)) {
                url = s.status_query;
            }
        }

        // remove path
        if (url.startsWith(prefix)) {
            int i = url.indexOf('/', prefix.length());
            if (i > 0) {
                url = url.substring(0, i);
            }
        }

        return url;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Context context = this.getApplicationContext();

        // Set all field to default values - does not work?
        // PreferenceManager.setDefaultValues(context, R.xml.settings, false);
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.setup);
        setContentView(R.layout.activity_setup);

        // change door type
        ListPreference list_field = (ListPreference) findPreference("type");
        list_field.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String type = newValue.toString();

                if (type.equals(setup.getType())) {
                    // no door type change
                    return true;
                } else if (type.equals(HttpsDoorSetup.type)) {
                    setup = new HttpsDoorSetup(setup.getId(), getText("name"));
                    loadSetup();
                    return true;
                } else if (type.equals(SshDoorSetup.type)) {
                    setup = new SshDoorSetup(setup.getId(), getText("name"));
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
                final String name = newValue.toString();
                setMainGroupTitle(name);
                return true;
            }
        });

        builder = new AlertDialog.Builder(this);
        setupGroups = collectGroups();

        int id = getIntent().getIntExtra("setup_id", -1);

        if (Settings.idExists(id)) {
            setup = Settings.getSetup(id);
        } else {
            setup = new HttpsDoorSetup(Settings.getNewID(), "");
        }

        // init type selection
        list_field.setValue(setup.getType());
        loadSetup();
    }

    PreferenceGroup findPreferenceGroup(String key) {
        return (PreferenceGroup) getPreferenceScreen().findPreference(key);
    }

    void loadSetup() {
        showGroup(setup.getType());

        Field[] fields = this.setup.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                String name = field.getName();
                Class<?> type = field.getType();
                Object value = field.get(setup);

                if (name.equals("type") || name.equals("id")) {
                    // ignore for display in preference field
                } else if (type == String.class) {
                    setText(name, (String) value);
                } else if (type == Integer.class) {
                    setText(name, value.toString());
                } else if (type == int.class) {
                    setText(name, value.toString());
                } else if (type == Boolean.class) {
                    setChecked(name, (Boolean) value);
                } else if (type == boolean.class) {
                    setChecked(name, (boolean) value);
                } else if (type == KeyPair.class) {
                    setKeyPair(name, (KeyPair) value);
                } else if (type == Certificate.class) {
                    setCertificate(name, (Certificate) value);
                } else {
                    Log.e("SetupActivity", "Unhandled type " + type.getSimpleName() + " of field " + name);
                }
            } catch (Exception ex) {
                Log.e("SetupActivity", "loadSetup: " + ex.toString());
            }
        }

        // update title
        setMainGroupTitle(setup.getName());
    }

    // apply preference to setup object
    void storeSetup() {
        // duplicate entry
        if (!Settings.idExists(setup.getId()) && Settings.nameExists(setup.getName())) {
           showErrorMessage("Entry Exists", "Name already exists.");
           return;
        }

        Field[] fields = setup.getClass().getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            Class<?> type = field.getType();

            try {
                if (name.equals("id") || name.equals("type")) {
                    // ignore - id is not displayed and type is read only field
                } else if (type == String.class) {
                    field.set(setup, getText(name));
                } else if (type == Boolean.class) {
                    field.set(setup, getChecked(name));
                } else if (type == Integer.class) {
                    field.set(setup, new Integer(Integer.parseInt(getText(name))));
                } else if (type == int.class) {
                   field.set(setup, Integer.parseInt(getText(name)));
                } else if (type == KeyPair.class) {
                    field.set(setup, getKeyPair(name));
                } else if (type == Certificate.class) {
                    field.set(setup, getCertificate(name));
                } else {
                    Log.e("SetupActivity", "storeSetup: Unhandled type for " + name + ": " + type.toString());
                }
            } catch (Exception ex) {
                showErrorMessage("Error", "Input for '" + name + "' caused an error: " + ex.toString());
                return;
            }
        }

        if (setup.getName() == null || setup.getName().length() == 0) {
            showErrorMessage("Invalid Name", "Name is not set.");
        } else {
            Settings.saveSetup(this.setup);

            // report all done
            Toast.makeText(getApplicationContext(), "Done", Toast.LENGTH_SHORT).show();
        }
    }
}
