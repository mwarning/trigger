package com.example.trigger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import com.example.trigger.https.HttpsTools;
import com.example.trigger.ssh.SshTools;
import com.jcraft.jsch.KeyPair;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.security.cert.Certificate;
import java.util.Iterator;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Settings {
    private static SharedPreferences sharedPreferences;
    private static String app_version; // stored in program
    private static String db_version; // stored in database

    private static String getDatabaseVersion(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // no version set means old 1.2.1
        return prefs.getString("db_version", "1.2.1");
    }

    private static String getApplicationVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "";
    }

    static String getVersion() {
        return app_version;
    }

    // update database format
    private static void upgradeDB() {
        // update from 1.2.1 to 1.3.0
        if (db_version.equals("1.2.1")) {
            Log.i("Settings", "Update database format from " + db_version + " to 1.3.1");

            // Recover setup from 1.2.0
            String name = sharedPreferences.getString("prefName", "");
            String url = sharedPreferences.getString("prefUrl", "");
            String token = sharedPreferences.getString("prefToken", "");
            Boolean ignore = sharedPreferences.getBoolean("prefIgnore", false);
            if (name.length() > 0) {
                HttpsDoorSetup setup = new HttpsDoorSetup(0, name);
                setup.open_query = url + "?action=open&token=" + token;
                setup.close_query = url + "?action=close&token=" + token;
                setup.status_query = url + "?action=status&token=" + token;
                if (ignore) {
                    setup.ignore_hostname_mismatch = true;
                }
                saveSetup(setup);
            }
            // remove old entries
            SharedPreferences.Editor e = sharedPreferences.edit();
            e.remove("prefName");
            e.remove("prefUrl");
            e.remove("prefToken");
            e.remove("prefIgnore");
            e.putString("db_version", app_version);
            e.commit();
            db_version = "1.3.1";
        }

        // update from 1.3.0/1.3.1 to 1.4.0
        if (db_version.equals("1.3.0") || db_version.equals("1.3.1")) {
            Log.i("Settings", "Update database format from " + db_version + " to 1.4.0");

            for (int id = 0; id < 10; id += 1) {
                String prefix = String.format("item_%03d_", id);
                if (sharedPreferences.contains(prefix + "type")) {
                    String name = sharedPreferences.getString(prefix + "name", "");
                    String url = sharedPreferences.getString(prefix + "url", "");
                    String token = sharedPreferences.getString(prefix + "token", "");
                    String ssids = sharedPreferences.getString(prefix + "ssids", "");
                    Boolean ignore = sharedPreferences.getBoolean(prefix + "ignore", false);
                    if (name.length() > 0) {
                        HttpsDoorSetup setup = new HttpsDoorSetup(id, name);
                        setup.open_query = url + "?action=open&token=" + token;
                        setup.close_query = url + "?action=close&token=" + token;
                        setup.status_query = url + "?action=status&token=" + token;
                        setup.ssids = ssids;
                        if (ignore) {
                            setup.ignore_hostname_mismatch = true;
                        }
                        saveSetup(setup);
                    } else {
                        removeSetup_pre_172(id);
                    }
                }
            }
            db_version = "1.4.0";
        }

        if (db_version.equals("1.4.0")) {
            Log.i("Settings", "Update database format from " + db_version + " to 1.6.0");
            for (int id = 0; id < 10; id += 1) {
                String prefix = String.format("item_%03d_", id);
                // change type of entry
                if (sharedPreferences.contains(prefix + "ignore_cert")) {
                    Boolean ignore_cert = sharedPreferences.getBoolean(prefix + "ignore_cert", false);
                    sharedPreferences.edit().putString(prefix + "ignore_cert", ignore_cert.toString()).commit();
                }
            }
            sharedPreferences.edit().putString("db_version", "1.6.0").commit();
            db_version = "1.6.0";
        }

        if (db_version.equals("1.6.0")) {
            Log.i("Settings", "Update database format from " + db_version + " to 1.7.0");
            for (int id = 0; id < 10; id += 1) {
                String prefix = String.format("item_%03d_", id);
                // change type of entry
                if (sharedPreferences.contains(prefix + "ignore_cert")) {
                    String ignore_cert = sharedPreferences.getString(prefix + "ignore_cert", (new Boolean(false)).toString());
                    if (new Boolean(ignore_cert)) {
                        SharedPreferences.Editor e = sharedPreferences.edit();
                        e.putString(prefix + "ignore_hostname_mismatch", (new Boolean(true).toString()));
                        e.remove(prefix + "ignore_cert");
                        e.commit();
                    }
                }
            }

            sharedPreferences.edit().putString("db_version", "1.7.0").commit();
            db_version = "1.7.0";
        }

        if (db_version.equals("1.7.0")) {
            Log.i("Settings", "Update database format from " + db_version + " to 1.7.1");
            // nothing to change
            sharedPreferences.edit().putString("db_version", app_version).commit();
            db_version = "1.7.1";
        }

        if (db_version.equals("1.7.1")) {
            Log.i("Settings", "Update database format from " + db_version + " to 1.7.2");
            // convert settings from key based scheme to json
            ArrayList<Setup> setups = getAllSetups_pre_172();
            for (Setup setup : setups) {
                removeSetup_pre_172(setup.getId());
                saveSetup(setup);
            }

            sharedPreferences.edit().putString("db_version", app_version).commit();
            db_version = "1.7.2";
        }
    }

    static void init(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        app_version = getApplicationVersion(context);
        db_version = getDatabaseVersion(context);

        //update database layout if necessary
        upgradeDB();
    }

    static JSONObject toJsonObject(Setup setup) { 
        JSONObject obj = new JSONObject();

        Field[] fields = setup.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                String name = field.getName();
                Class<?> type = field.getType();
                Object value = field.get(setup);

                if (type == String.class) {
                    obj.put(name, (String) value);
                } else if (type == Boolean.class) {
                    obj.put(name, (Boolean) value);
                } else if (type == boolean.class) {
                    obj.put(name, (boolean) value);
                } else if (type == Integer.class) {
                    obj.put(name, (Integer) value);
                } else if (type == int.class) {
                    obj.put(name, (int) value);
                } else if (type == KeyPair.class) {
                    obj.put(name, SshTools.serializeKeyPair((KeyPair) value));
                } else if (type == Certificate.class) {
                    obj.put(name, HttpsTools.serializeCertificate((Certificate) value));
                } else {
                    Log.e("Settings", "toJsonObject: Unhandled type for " + name + ": " + type.toString());
                }
            } catch (Exception e) {
                Log.e("Settings", "toJsonObject: " + e.toString());
                return null;
            }
        }

        return obj;
    }

    /*
    * Initialize setup object fields from json object fields.
    */
    private static void setSetupField(Setup setup, JSONObject object, String name)
            throws JSONException, IllegalAccessException {
        Object value = object.get(name);
        Class<?> value_type = value.getClass();

        Field[] fields = setup.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().equals(name)) {
	            Class<?> type = field.getType();
	            if (type == String.class && value_type == String.class) {
	                field.set(setup, value);
	            } else if (type == Boolean.class && value_type == Boolean.class) {
	                field.set(setup, (Boolean) value);
	            } else if (type == boolean.class && value_type == boolean.class) {
	                field.set(setup, (boolean) value);
	            } else if (type == Integer.class && value_type == Integer.class) {
	                field.set(setup, (Integer) value);
	            } else if (type == int.class && (value_type == int.class || value_type == Integer.class)) {
	                field.set(setup, (int) value);
	            } else if (type == KeyPair.class && value_type == String.class) {
	                field.set(setup, SshTools.deserializeKeyPair((String) value));
	            } else if (type == Certificate.class && value_type == String.class) {
	                field.set(setup, HttpsTools.deserializeCertificate((String) value));
	            } else {
	                // show warning?
	                Log.e("Settings", "setField: Unhandled type for " + name + ": " + type.toString());
	            }
	            break;
	        }
        }
    }

    static Setup fromJsonObject(JSONObject obj)
            throws JSONException, IllegalAccessException {
        Setup setup = null;
        int id = obj.getInt("id");
        String type = obj.getString("type");

        // get empty setup object to fill
        if (type.equals(HttpsDoorSetup.type)) {
            setup = new HttpsDoorSetup(id, "");
        } else if (type.equals(SshDoorSetup.type)) {
            setup = new SshDoorSetup(id, "");
        } else if (type.equals(BluetoothDoorSetup.type)) {
            setup = new BluetoothDoorSetup(id, "");
        } else {
            Log.e("Settings.loadSetup", "Found unknown setup type: " + type);
            return null;
        }

        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.equals("type")) {
                setSetupField(setup, obj, key);
            }
        }

        return setup;
    }

    static Setup loadSetup(int id) {
        Setup setup = null;

        if (id < 0) {
            return null;
        }

        String key = String.format("item_%03d", id);
        String json = sharedPreferences.getString(key, null);

        if (json == null) {
            return null;
        }

        try {
            JSONObject obj = new JSONObject(json);
            return fromJsonObject(obj);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    static void saveSetup(Setup setup) {
        if (setup == null || setup.getId() < 0) {
            return;
        }

        JSONObject json = toJsonObject(setup);

        if (json == null) {
            return;
        }

        String key = String.format("item_%03d", setup.getId());

        sharedPreferences.edit().putString(key, json.toString()).commit();
    }

   static void removeSetup(int id) {
        String key = String.format("item_%03d", id);
        sharedPreferences.edit().remove(key).commit();
    }

    private static Setup loadSetup_pre_172(int id) {
        Setup setup = null;

        if (id < 0) {
            return null;
        }

        {
            // get type
            String type = sharedPreferences.getString(
                String.format("item_%03d_type", id), null
            );

            if (type == null) {
                return null;
            }

            // get empty setup object to fill
            if (type.equals(HttpsDoorSetup.type)) {
                setup = new HttpsDoorSetup(id, "");
            } else if (type.equals(SshDoorSetup.type)) {
                setup = new SshDoorSetup(id, "");
            } else if (type.equals(BluetoothDoorSetup.type)) {
                setup = new BluetoothDoorSetup(id, "");
            } else {
                Log.e("Settings.loadSetup", "Found unknown setup type: " + type);
                return null;
            }
        }

        Field[] fields = setup.getClass().getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            Class<?> type = field.getType();
            String key = String.format("item_%03d_%s", id, name);

            try {
                String value = sharedPreferences.getString(key, "");

                if (name.equals("type")) {
                    // ignore, object field is read only
                } else if (type == String.class) {
                    field.set(setup, value);
                } else if (type == Boolean.class) {
                    field.set(setup, new Boolean(Boolean.parseBoolean(value)));
                } else if (type == boolean.class) {
                    field.set(setup, Boolean.parseBoolean(value));
                } else if (type == Integer.class) {
                    field.set(setup, new Integer(Integer.parseInt(value)));
                } else if (type == int.class) {
                    field.set(setup, Integer.parseInt(value));
                } else if (type == KeyPair.class) {
                    field.set(setup, SshTools.deserializeKeyPair(value));
                } else if (type == Certificate.class) {
                    field.set(setup, HttpsTools.deserializeCertificate(value));
                } else {
                    Log.e("Settings", "loadSetup: Unhandled type for " + name + ": " + type.toString());
                }
            } catch (Exception ex) {
                Log.e("Settings", "loadSetup: " + ex.toString());
            }
        }

        return setup;
    }

    private static ArrayList<Setup> getAllSetups_pre_172() {
        ArrayList<Setup> setups = new ArrayList();
        Map<String,?> keys = sharedPreferences.getAll();
        Pattern p = Pattern.compile("^item_(\\d{3})_type$");

        for (Map.Entry<String,?> entry : keys.entrySet()) {
            String key = entry.getKey();
            Matcher m = p.matcher(key);
            if (!m.find()) {
                continue;
            }

            int id = Integer.parseInt(m.group(1));
            Setup setup = loadSetup_pre_172(id);
            if (setup != null) {
                setups.add(setup);
            }
        }

        return setups;
    }

    private static void removeSetup_pre_172(int id) {
        String prefix = String.format("item_%03d_", id);
        SharedPreferences.Editor e = sharedPreferences.edit();

        // remove existing setup data
        Map<String,?> keys = sharedPreferences.getAll();
        for (Map.Entry<String,?> entry : keys.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                e.remove(key);
            }
        }

        e.commit();
    }

    static ArrayList<Setup> getAllSetups() {
        ArrayList<Setup> setups = new ArrayList();
        Map<String,?> keys = sharedPreferences.getAll();
        Pattern p = Pattern.compile("^item_(\\d{3})$");

        for (Map.Entry<String,?> entry : keys.entrySet()) {
            String key = entry.getKey();
            Matcher m = p.matcher(key);
            if (!m.find()) {
                continue;
            }

            int id = Integer.parseInt(m.group(1));
            Setup setup = loadSetup(id);
            if (setup != null) {
                setups.add(setup);
            }
        }

        return setups;
    }

    static boolean idExists(int id) {
        return sharedPreferences.contains(String.format("item_%03d", id));
    }

    static int getNewID() {
        int id = 0;
        while (true) {
            if (!idExists(id)) {
                return id;
            }
            id += 1;
        }
    }

    static boolean nameExists(String name) {
        ArrayList<Setup> setups = getAllSetups();
        for (Setup setup : setups) {
            if (setup.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    // for debugging
    static void printAll(SharedPreferences pref) {
        Map<String,?> keys = pref.getAll();
        for (Map.Entry<String,?> entry : keys.entrySet()){
            Log.d("Settings.printAll", entry.getKey() + ": " +  entry.getValue().toString());
        }
    }
}
