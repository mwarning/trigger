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


public class Settings {
    private static ArrayList<Setup> sharedSetups;
    private static SharedPreferences sharedPreferences;
    private static boolean init_done = false;
    private static String app_version;
    private static String db_version;

    private static String get_db_version(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("db_version", "1.2.0");
    }

    private static String get_app_version(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "";
    }

    static String get_version() {
        return app_version;
    }

    // update database format
    private static void update_db_format() {
        if (db_version == "1.2.0" && db_version != app_version) {
            Log.i("Settings", "update database format from '" + db_version + "'' to " + app_version);

            String name = sharedPreferences.getString("prefName", "");
            String url = sharedPreferences.getString("prefUrl", "");
            String token = sharedPreferences.getString("prefToken", "");
            Boolean ignore = sharedPreferences.getBoolean("prefIgnore", false);
            if (name.length() > 0 && url.length() > 0 && token.length() > 0) {
                sharedSetups.add(
                    new SphincterSetup(0, name, url, token, "", ignore)
                );
                store();
            }
            sharedPreferences.edit().putString("db_version", app_version).commit();
        }
    }

    static void load(Context context) {
        if (init_done) {
            Log.e("Settings", "load already done");
            return;
        } else {
            init_done = true;
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedSetups = new ArrayList<Setup>();

        app_version = get_app_version(context);
        db_version = get_db_version(context);

        // Update database layout if necessary
        update_db_format();

        Map<String,?> keys = sharedPreferences.getAll();
        Pattern p = Pattern.compile("^item_(\\d{3})_name$");

        for (Map.Entry<String,?> entry : keys.entrySet()) {
            String key = entry.getKey();
            Matcher m = p.matcher(key);
            if (!m.find()) {
                continue;
            }

            int id = Integer.parseInt(m.group(1));
            if (id < 0 && id_exists(id)) {
                continue;
            }

            String prefix = String.format("item_%03d_", id);
            String type = sharedPreferences.getString(prefix + "type", "");
            //Log.i("Settings.getItem", "prefix: " + prefix + ", type: " + type);

            if (type.equals("sphincter")) {
                String name = sharedPreferences.getString(prefix + "name", "");
                String url = sharedPreferences.getString(prefix + "url", "");
                String token = sharedPreferences.getString(prefix + "token", "");
                String ssids = sharedPreferences.getString(prefix + "ssids", "");
                Boolean ignore = sharedPreferences.getBoolean(prefix + "ignore", false);
                sharedSetups.add(
                    new SphincterSetup(id, name, url, token, ssids, ignore)
                );
            } else {
                Log.e("Settings.load", "Unknown setup type found: " + type);
            }
        }
    }

    static void store() {
        SharedPreferences.Editor e = sharedPreferences.edit();
        Map<String,?> keys = sharedPreferences.getAll();

        // remove setups that might have been removed
        for (Map.Entry<String,?> entry : keys.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("item_")) {
                e.remove(key);
            }
        }

        e.putString("db_version", db_version);

        for (Setup setup : sharedSetups) {
            if (setup.getId() < 0) {
                continue;
            }

            String prefix = String.format("item_%03d_", setup.getId());

            if (setup instanceof SphincterSetup) {
                SphincterSetup obj = (SphincterSetup) setup;
                Log.d("Settings", "safeItem: prefix: " + prefix + ", name: " + obj.name);

                // fill settings into preference view
                e.putString(prefix + "type", "sphincter");
                e.putString(prefix + "name", obj.name);
                e.putString(prefix + "url", obj.url);
                e.putString(prefix + "token", obj.token);
                e.putString(prefix + "ssids", obj.ssids);
                e.putBoolean(prefix + "ignore", obj.ignore);
            }
        }

        e.commit();
    }

    static ArrayList<Setup> all_setups() {
        return (ArrayList<Setup>) sharedSetups.clone();
    }

    static Setup find_setup(int id) {
        for (Setup setup : sharedSetups) {
            if (setup.getId() == id) {
                return setup;
            }
        }
        return null;
    }

    static void add_setup(Setup setup) {
        if (find_setup(setup.getId()) != null) {
            Log.d("Settings.add_setup", "id not found: " + setup.getId());
            return;
        }
        sharedSetups.add(setup);
    }


    static void remove_setup(int id) {
        for (int i = 0; i < sharedSetups.size(); i += 1) {
            if (sharedSetups.get(i).getId() == id) {
                sharedSetups.remove(i);
                return;
            }
        }
    }

    static boolean id_exists(int id) {
        for (Setup setup : sharedSetups) {
            if (setup.getId() == id) {
                return true;
            }
        }
        return false;
    }

    static int id_new() {
        int n = 0;
        while (true) {
            if (!id_exists(n)) {
                return n;
            }
            n += 1;
        }
    }

    static boolean name_exists(String name) {
        for (Setup setup : sharedSetups) {
            if (setup.getName() == name) {
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
