package com.example.trigger;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.EditTextPreference;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Map;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Settings {
    static int newId(SharedPreferences pref) {
        ArrayList<Integer> ids = get_all_ids(pref);

        int n = 0;
        while (true) {
            if (ids.indexOf(n) == -1) {
                return n;
            }
            n += 1;
        }
    }

    static void printAll(SharedPreferences pref) {
    	Map<String,?> keys = pref.getAll();
        for(Map.Entry<String,?> entry : keys.entrySet()){
            Log.d("Settings.printAll", entry.getKey() + ": " +  entry.getValue().toString());
        }
    }

    static void safeItem(SharedPreferences pref, Setup item) {
        String prefix = String.format("item_%03d_", item.getId());

        if (item.getId() < 0) {
            Log.e("Settings.safeItem", "Invalid id: " + item.getId());
            return;
        }

        if (item instanceof SphincterSetup) {
            SphincterSetup obj = (SphincterSetup) item;
        	Log.d("Settings", "safeItem: prefix: " + prefix + ", name: " + obj.name);

            SharedPreferences.Editor e = pref.edit();
            e.putString(prefix + "type", "sphincter");
            e.putString(prefix + "name", obj.name);
            e.putString(prefix + "url", obj.url);
            e.putString(prefix + "token", obj.token);
            e.putBoolean(prefix + "ignore", obj.ignore);
            e.commit();
        }
    }

    static void deleteItem(SharedPreferences pref, int id) {
        if (id < 0) {
            Log.e("Settings.deleteItem", "Invalid id: " + id);
            return;
        }

        Map<String,?> keys = pref.getAll();
        String prefix = String.format("item_%03d_", id);
        
        SharedPreferences.Editor e = pref.edit();
        for (Map.Entry<String,?> entry : keys.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                e.remove(key);
            }
        }
        e.commit();
    }

    static ArrayList<Integer> get_all_ids(SharedPreferences pref) {
        ArrayList<Integer> ids = new ArrayList<>();
        Map<String,?> keys = pref.getAll();

        Pattern p = Pattern.compile("^item_(\\d{3})_name$");

        for (Map.Entry<String,?> entry : keys.entrySet()) {
            String key = entry.getKey();
            Matcher m = p.matcher(key);
            if (m.find()) {
                int id = Integer.parseInt(m.group(1));
                if (id >= 0 && ids.indexOf(id) < 0) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    static boolean name_exists(SharedPreferences pref, String name) {
        ArrayList<Setup> items = getAllItems(pref);
        for (Setup item : items) {
            if (item.getName() == name) {
               return true;
            }
        }
        return false;
    }

    static Setup getItem(SharedPreferences pref, int id) {
        String prefix = String.format("item_%03d_", id);
        String type = pref.getString(prefix + "type", "");
        //Log.i("Settings.getItem", "prefix: " + prefix + ", type: " + type);

        if (type.equals("sphincter")) {
            String name = pref.getString(prefix + "name", "");
            String url = pref.getString(prefix + "url", "");
            String token = pref.getString(prefix + "token", "");
            Boolean ignore = pref.getBoolean(prefix + "ignore", false);
            return new SphincterSetup(id, name, url, token, ignore);
        }

        return null;
    }

    static ArrayList<Setup> getAllItems(SharedPreferences pref) {
        ArrayList<Setup> items = new ArrayList<>();
        ArrayList<Integer> ids = get_all_ids(pref);

        for (int id : ids) {
            Setup item = getItem(pref, id);
            if (item != null) {
                items.add(item);
            }
        }

        return items;
    }

    static void showItem(Setup item, SharedPreferences pref) {
        if (item instanceof SphincterSetup) {
            SphincterSetup obj = (SphincterSetup) item;
            SharedPreferences.Editor e = pref.edit();
            e.putString("prefUrl", obj.url);
            e.putString("prefToken", obj.token);
            e.putBoolean("prefIgnore", obj.ignore);
            e.commit();
        }
    }
}
