package com.example.trigger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.trigger.Utils.*;


public class Settings {
    private static ArrayList<Setup> sharedSetups;
    private static SharedPreferences sharedPreferences;
    private static String app_version; // stored in program
    private static String db_version; // stored in database

    private static String getDatabaseVersion(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
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
        Log.d("updateDB", "db_version: " + db_version);
        printAll(sharedPreferences);

        // update from 1.2.1 to 1.3.0
        if (db_version.equals("1.2.1")) {
            Log.i("Settings", "update database format from " + db_version + " to " + app_version);

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
                setup.ignore_cert = ignore;
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

        // updat from 1.3.0/1.3.1 to 1.4.0
        if (db_version.equals("1.3.0") || db_version.equals("1.3.1")) {
            for (int id = 0; id < 10; id += 1) {
                String prefix = String.format("item_%03d_", id);
                if (sharedPreferences.contains(prefix + "type")) {
                    String name = sharedPreferences.getString(prefix + "name", "");
                    String url = sharedPreferences.getString(prefix + "url", "");
                    String token = sharedPreferences.getString(prefix + "token", "");
                    String ssids = sharedPreferences.getString(prefix + "ssids", "");
                    Boolean ignore = sharedPreferences.getBoolean(prefix + "ignore", false);
                    Log.d("Settings", "Found old setting: " + name);
                    if (name.length() > 0) {
                        HttpsDoorSetup setup = new HttpsDoorSetup(id, name);
                        setup.open_query = url + "?action=open&token=" + token;
                        setup.close_query = url + "?action=close&token=" + token;
                        setup.status_query = url + "?action=status&token=" + token;
                        setup.ssids = ssids;
                        setup.ignore_cert = ignore;
                        saveSetup(setup);
                        Log.d("updateDB", "convert entry with url: " + url);
                    } else {
                        removeSetup(id);
                    }
                }
            }
            sharedPreferences.edit().putString("db_version", app_version).commit();
            db_version = app_version;
        }
    }

    static void init(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedSetups = new ArrayList<Setup>();

        app_version = getApplicationVersion(context);
        db_version = getDatabaseVersion(context);

        //update database layout if necessary
        upgradeDB();
    }

    static void saveSetup(Setup setup) {
        if (setup.getId() < 0) {
            return;
        }

        ArrayList<Pair> pairs = new ArrayList();
        setup.getAllSettings(pairs);

        // remove first, in case the type has changed
        removeSetup(setup.getId());

        // store new setup data
        String prefix = String.format("item_%03d_", setup.getId());
        SharedPreferences.Editor e = sharedPreferences.edit();

        e.putString(prefix + "type", setup.getType());
        for (Pair pair : pairs) {
            Log.d("save_setup", prefix + pair.key + ": " + pair.value);
            if (pair.value instanceof String) {
                e.putString(prefix + pair.key, (String) pair.value);
            } else if (pair.value instanceof Boolean) {
                e.putBoolean(prefix + pair.key, (Boolean) pair.value);
            } else {
                Log.e("Settings.save_setup", "Unknown type for " + pair.key);
            }
        }

        e.commit();
    }

    static ArrayList<Setup> getAllSetups() {
        Log.d("Settings.getAllSetups", "called");

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
            Log.d("Settings.getAllSetups", "get id " + id);
            Setup setup = getSetup(id);
            if (setup != null) {
                setups.add(setup);
            }
        }

        Log.d("Settings.getAllSetups", "setup.size(): " + setups.size());

        return setups;
    }

    static Setup getSetup(int id) {
        Setup setup = null;

        if (id < 0) {
            return null;
        }

        // get type
        String type = sharedPreferences.getString(
            String.format("item_%03d_type", id), ""
        );

        Log.d("Settings.getSetup", "id: " + id + ", type: " + type);

        // get empty setup object
        if (type.equals(HttpsDoorSetup.type)) {
            setup = new HttpsDoorSetup(id, "");
        } else if (type.equals(SshDoorSetup.type)) {
            setup = new SshDoorSetup(id, "");
        } else {
            Log.e("Settings.get_setup", "Found unknown setup type: " + type);
            return null;
        }

        Pattern key_pattern = Pattern.compile(
            String.format("^item_%03d_([\\w_]+)$", id)
        );
        ArrayList<Pair> pairs = new ArrayList();
        Map<String,?> keys = sharedPreferences.getAll();

        //Log.d("Settings.getSetup", "get fields");
        // collect all entries by id
        for (Map.Entry<String,?> entry : keys.entrySet()) {
            //Log.d("Settings.getSetup", "key: " + entry.getKey() + ", value: " + entry.getValue());
            Matcher m = key_pattern.matcher(entry.getKey());
            if (!m.find()) {
                continue;
            }

            String key = m.group(1);
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Boolean) {
                //Log.d("Settings.getSetup", "key/value: " + key + " " + value.toString());
                pairs.add(new Pair(key, value));
            } else {
                //Log.d("Settings.getSetup", "unknown value type for " + key + ": " + value.toString());
            }
        }

        Log.d("Settings.getSetup", "pairs.length: " + pairs.size());

        // set fields in setup object
        setup.setAllSettings(pairs);

        return setup;
    }

    static void removeSetup(int id) {
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

    static boolean idExists(int id) {
        Log.d("Settings", "idExists: " + String.format("item_%03d_type", id));
        return sharedPreferences.contains(String.format("item_%03d_type", id));
    }

    static int getNewID() {
        int n = 0;
        while (true) {
            if (!idExists(n)) {
                return n;
            }
            n += 1;
        }
    }

    static boolean nameExists(String name) {
        Map<String,?> keys = sharedPreferences.getAll();
        Pattern p = Pattern.compile("^item_(\\d{3})_name$");

        for (Map.Entry<String,?> entry : keys.entrySet()) {
            String key = entry.getKey();
            Matcher m = p.matcher(key);
            if (!m.find()) {
                continue;
            }

            if (name.equals(entry.getValue())) {
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
