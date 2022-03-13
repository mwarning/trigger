package app.trigger;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.lang.reflect.Field;
import java.security.cert.Certificate;
import java.util.ArrayList;

import app.trigger.https.CertificatePreference;
import app.trigger.mqtt.MqttClientKeyPairPreference;
import app.trigger.ssh.SshKeyPairPreference;
import app.trigger.ssh.KeyPairBean;


public class SetupActivity extends PreferenceActivity {
    private static final String TAG = "SetupActivity";
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
        ArrayList<PreferenceGroup> groups = new ArrayList<>();
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

        Log.e(TAG, "showGroup(): PreferenceGroup not found: " + key);
    }

    public void onSaveButtonClicked(View v) {
        storeSetup();
    }

    public void onDeleteButtonClicked(View v) {
        builder.setTitle(R.string.confirm);
        builder.setMessage(R.string.really_remove_item);
        builder.setCancelable(false); // not necessary

        builder.setPositiveButton(R.string.yes, (DialogInterface dialog, int id) -> {
            Settings.removeSetup(setup.getId());
            // close this dialog and settings
            SetupActivity.this.finish();
        });

        builder.setNegativeButton(R.string.no, (DialogInterface dialog, int id) -> {
            // close this dialog
            dialog.cancel();
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
            if (name.length() > 0) {
                pc.setTitle(name);
            } else {
                pc.setTitle(R.string.new_entry);
            }
        } else {
            Log.e(TAG, "setMainGroupTitle(): Cannot find main_category");
        }
    }

    private String getSummaryValue(String key, String value) {
        if (value.isEmpty()) {
            return getResources().getString(R.string.none);
        }

        if (key.equals("password")) {
            // only show password as star sequences
            return new String(new char[value.length()]).replace("\0", "*");
        } else {
            return value;
        }
    }

    private void setText(String key, String value) {
        Preference p = findAnyPreference(key, null);
        if (p instanceof EditTextPreference) {
            EditTextPreference etp = (EditTextPreference) p;
            etp.setText(value);

            // show value as summary
            etp.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                preference.setSummary(getSummaryValue(key, newValue.toString()));
                return true;
            });
            etp.setSummary(getSummaryValue(key, value));
        } else if (p instanceof ListPreference) {
            ListPreference lp = (ListPreference) p;
            lp.setValue(value);
            // set summary field to "%s" in xml
        } else {
            Log.w(TAG, "setText(): Cannot find EditTextPreference/ListPreference in PreferenceGroup with key: " + key);
        }
    }

    private String getText(String key) {
        Preference p = findAnyPreference(key, null);
        if (p instanceof EditTextPreference) {
            EditTextPreference etp = (EditTextPreference) p;
            return etp.getText();
        } else if (p instanceof ListPreference) {
            ListPreference lp = (ListPreference) p;
            return lp.getValue();
        } else {
            Log.w(TAG, "getText(): Cannot find EditTextPreference/ListPreference in PreferenceGroup with key: " + key);
            return "";
        }
    }

    private void setChecked(String key, boolean checked) {
        CheckBoxPreference cbp = (CheckBoxPreference) findAnyPreference(key, null);
        if (cbp != null) {
            cbp.setChecked(checked);
        } else {
            Log.e(TAG, "setChecked(): Cannot find CheckBoxPreference in PreferenceGroup with key: " + key);
        }
    }

    private boolean getChecked(String key) {
        CheckBoxPreference cbp = (CheckBoxPreference) findAnyPreference(key, null);
        if (cbp != null) {
            return cbp.isChecked();
        } else {
            Log.e(TAG, "getChecked(): Cannot find CheckBoxPreference in PreferenceGroup with key: " + key);
            return false;
        }
    }

    private Bitmap getBitmap(String key) {
        ImagePreference kpp = (ImagePreference) findAnyPreference(key, null);
        if (kpp != null) {
            return kpp.getImage();
        } else {
            Log.e(TAG, "getBitmap(): Cannot find ImagePreference in PreferenceGroup with key: " + key);
            return null;
        }
    }

    private void setBitmap(String key, Bitmap image) {
        ImagePreference kpp = (ImagePreference) findAnyPreference(key, null);
        if (kpp != null) {
            kpp.setImage(image);
        } else {
            Log.e(TAG, "setBitmap(): Cannot find ImagePreference in PreferenceGroup with key: " + key);
        }
    }

    private KeyPairBean getKeyPairBean(String key) {
        Preference preference = findAnyPreference(key, null);
        if (preference instanceof SshKeyPairPreference) {
            return ((SshKeyPairPreference)  preference).getKeyPair();
        } else if (preference instanceof MqttClientKeyPairPreference) {
            return ((MqttClientKeyPairPreference)  preference).getKeyPair();
        } else {
            Log.e(TAG, "getKeyPair(): Cannot find KeyPairPreference in PreferenceGroup with key: " + key);
            return null;
        }
    }

    private void setKeyPairBean(String key, KeyPairBean keypair) {
        Preference preference = findAnyPreference(key, null);
        if (preference instanceof SshKeyPairPreference) {
            ((SshKeyPairPreference)  preference).setKeyPair(keypair);
        } else if (preference instanceof MqttClientKeyPairPreference) {
            ((MqttClientKeyPairPreference)  preference).setKeyPair(keypair);
        } else {
            Log.e(TAG, "setKeyPair(): Cannot find KeyPairPreference in PreferenceGroup with key: " + key);
        }
    }

    private Certificate getCertificate(String key) {
        CertificatePreference cp = (CertificatePreference) findAnyPreference(key, null);
        if (cp != null) {
            return cp.getCertificate();
        } else {
            Log.e(TAG, "getCertificate(): Cannot find CertificatePreference in PreferenceGroup with key: " + key);
            return null;
        }
    }

    private void setCertificate(String key, Certificate certificate) {
        CertificatePreference cp = (CertificatePreference) findAnyPreference(key, null);
        if (cp != null) {
            cp.setCertificate(certificate);
        } else {
            Log.e(TAG, "setCertificate(): Cannot find CertificatePreference in PreferenceGroup with key: " + key);
        }
    }

    public String getRegisterUrl() {
        return setup.getRegisterUrl();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Set all field to default values - does not work?
        // PreferenceManager.setDefaultValues(context, R.xml.settings, false);
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.setup);
        setContentView(R.layout.activity_setup);

        // change door type
        ListPreference list_field = (ListPreference) findPreference("type");
        list_field.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
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
            } else if (type.equals(BluetoothDoorSetup.type)) {
                setup = new BluetoothDoorSetup(setup.getId(), getText("name"));
                loadSetup();
                return true;
            } else if (type.equals(NukiDoorSetup.type)) {
                setup = new NukiDoorSetup(setup.getId(), getText("name"));
                loadSetup();
                return true;
            } else if (type.equals(MqttDoorSetup.type)) {
                setup = new MqttDoorSetup(setup.getId(), getText("name"));
                loadSetup();
                return true;
            } else {
                Log.e(TAG, "Unhandled type from selection: " + type);
                return false;
            }
        });

        // update main category title
        EditTextPreference name_field = (EditTextPreference) findPreference("name");
        name_field.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
            final String name = newValue.toString();
            setMainGroupTitle(name);
            return true;
        });

        builder = new AlertDialog.Builder(this);
        setupGroups = collectGroups();

        int id = getIntent().getIntExtra("setup_id", -1);

        setup = Settings.getSetup(id);
        if (setup == null) {
            // default setup
            setup = new HttpsDoorSetup(Settings.getNewID(), "");
        }

        // init type selection
        list_field.setValue(setup.getType());

        loadSetup();
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
                } else if (type == Long.class) {
                    setText(name, value.toString());
                } else if (type == int.class) {
                    setText(name, value.toString());
                } else if (type == long.class) {
                    setText(name, value.toString());
                } else if (type == Boolean.class) {
                    setChecked(name, (Boolean) value);
                } else if (type == boolean.class) {
                    setChecked(name, (boolean) value);
                } else if (type == Bitmap.class) {
                    setBitmap(name, (Bitmap) value);
                } else if (type == KeyPairBean.class) {
                    setKeyPairBean(name, (KeyPairBean) value);
                } else if (type == Certificate.class) {
                    setCertificate(name, (Certificate) value);
                } else {
                    throw new Exception("Unhandled type " + type.getSimpleName() + " of field " + name);
                }
            } catch (Exception e) {
                Log.e(TAG, "loadSetup: " + e.toString());
                e.printStackTrace();
            }
        }

        // update title
        setMainGroupTitle(setup.getName());
    }

    // apply preference fields to setup object fields
    void storeSetup() {
        Field[] fields = setup.getClass().getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            Class<?> type = field.getType();

            try {
                if (name.equals("id") || name.equals("type")) {
                    // ignore - id is not displayed and type is read only field
                } else if (findAnyPreference(name, null) == null) {
                    // ignore
                    Log.w(TAG, "storeSetup(): Ignore setup field: " + name);
                } else if (type == String.class) {
                    field.set(setup, getText(name));
                } else if (type == Boolean.class) {
                    field.set(setup, getChecked(name));
                } else if (type == Integer.class) {
                    field.set(setup, Integer.parseInt(getText(name)));
                } else if (type == Long.class) {
                    field.set(setup, Long.parseLong(getText(name)));
                } else if (type == int.class) {
                   field.set(setup, Integer.parseInt(getText(name)));
                } else if (type == long.class) {
                   field.set(setup, Long.parseLong(getText(name)));
                } else if (type == Bitmap.class) {
                    field.set(setup, getBitmap(name));
                } else if (type == KeyPairBean.class) {
                    field.set(setup, getKeyPairBean(name));
                } else if (type == Certificate.class) {
                    field.set(setup, getCertificate(name));
                } else {
                    Log.e(TAG, "storeSetup: Unhandled type for " + name + ": " + type.toString());
                }
            } catch (Exception ex) {
                showErrorMessage("Error", "Input for '" + name + "' caused an error: " + ex.toString());
                return;
            }
        }

        int count = Settings.countNames(setup.getName());
        boolean exists = Settings.idExists(setup.getId());
        if ((exists && count > 1) || (!exists && count > 0)) {
            showErrorMessage("Entry Exists", "Name already exists.");
        } else if (setup.getName() == null || setup.getName().length() == 0) {
            showErrorMessage("Invalid Name", "Door name is not set.");
        } else {
            Settings.addSetup(this.setup);

            // report all done
            Toast.makeText(getApplicationContext(), "Done", Toast.LENGTH_SHORT).show();

            // needed for SSID matching
            if (this.setup.getWiFiSSIDs().length() > 0) {
                checkFineLocationPermission();
            }
        }
    }

    private static final int REQUEST_FINE_LOCATION_PERMISSION = 0x01;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_FINE_LOCATION_PERMISSION:
                if (Utils.allGranted(grantResults)) {
                    // permissions granted
                    Toast.makeText(getApplicationContext(), "Permissions granted - SSID matching should work now.", Toast.LENGTH_SHORT).show();
                } else {
                    showErrorMessage("Permissions Required", "Cannot match WiFi SSIDs.");
                }
                break;
        }
    }

    // SSID matching needs fine location permissions
    private void checkFineLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            if (!Utils.hasFineLocationPermission(this)) {
                Utils.requestFineLocationPermission(this, REQUEST_FINE_LOCATION_PERMISSION);
            }
        }
    }
}