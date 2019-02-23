package com.example.trigger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import com.example.trigger.ssh.SshTools;
import com.jcraft.jsch.KeyPair;

import java.lang.reflect.Field;
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
            Log.i("Settings", "update database format from " + db_version + " to 1.3.1");

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

        // update from 1.3.0/1.3.1 to 1.4.0
        if (db_version.equals("1.3.0") || db_version.equals("1.3.1")) {
            Log.i("Settings", "update database format from " + db_version + " to 1.4.0");

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
                        setup.ignore_cert = ignore;
                        saveSetup(setup);
                    } else {
                        removeSetup(id);
                    }
                }
            }
            db_version = "1.4.0";
        }

        if (db_version.equals("1.4.0")) {
            Log.i("Settings", "update database format from " + db_version + " to " + app_version);
            for (int id = 0; id < 10; id += 1) {
                String prefix = String.format("item_%03d_", id);
                // change type of entry
                if (sharedPreferences.contains(prefix + "ignore_cert")) {
                    Boolean ignore_cert = sharedPreferences.getBoolean(prefix + "ignore_cert", false);
                    sharedPreferences.edit().putString(prefix + "ignore_cert", ignore_cert.toString()).commit();
                }
            }
            sharedPreferences.edit().putString("db_version", app_version).commit();
            db_version = app_version;
        }
    }

    static void init(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        app_version = getApplicationVersion(context);
        db_version = getDatabaseVersion(context);

        //update database layout if necessary
        upgradeDB();
    }

    static void saveSetup(Setup setup) {
        if (setup.getId() < 0) {
            return;
        }

        // remove first, in case the type has changed
        removeSetup(setup.getId());

        // store new setup data
        String prefix = String.format("item_%03d_", setup.getId());
        SharedPreferences.Editor e = sharedPreferences.edit();

        Field[] fields = setup.getClass().getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            Class<?> type = field.getType();
            Object value = new String();

            try {
                value = field.get(setup);

                if (type == String.class) {
                    e.putString(prefix + name, value.toString());
                } else if (type == Boolean.class) {
                    e.putString(prefix + name, value.toString());
                } else if (type == boolean.class) {
                    e.putString(prefix + name, value.toString());
                } else if (type == Integer.class) {
                    e.putString(prefix + name, value.toString());
                } else if (type == int.class) {
                    e.putString(prefix + name, value.toString());
                } else if (type == KeyPair.class) {
                    e.putString(prefix + name, SshTools.serializeKeyPair((KeyPair) value));
                } else {
                    Log.e("Settings", "saveSetup: Unhandled type for " + name + ": " + type.toString());
                }
            } catch (Exception ex) {
                Log.e("Settings", "saveSetup: " + ex.toString());
            }
        }

        e.commit();
    }

    static Setup getSetup(int id) {
        Setup setup = null;

        if (id < 0) {
            return null;
        }

        {
            // get type
            String type = sharedPreferences.getString(
                String.format("item_%03d_type", id), ""
            );

            // get empty setup object to fill
            if (type.equals(HttpsDoorSetup.type)) {
                setup = new HttpsDoorSetup(id, "");
            } else if (type.equals(SshDoorSetup.type)) {
                setup = new SshDoorSetup(id, "");
            } else {
                Log.e("Settings.getSetup", "Found unknown setup type: " + type);
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
                } else {
                    Log.e("Settings", "getSetup: Unhandled type for " + name + ": " + type.toString());
                }
            } catch (Exception ex) {
                Log.e("Settings", "getSetup: " + ex.toString());
            }
        }

        return setup;
    }

    static ArrayList<Setup> getAllSetups() {
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
            Setup setup = getSetup(id);
            if (setup != null) {
                setups.add(setup);
            }
        }

        return setups;
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
