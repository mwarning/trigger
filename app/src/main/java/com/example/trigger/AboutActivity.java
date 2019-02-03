package com.example.trigger;

import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.EditTextPreference;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;


public class AboutActivity extends PreferenceActivity {
    private String getVersion() {
        try {
            Context context = this.getApplicationContext();
            PackageInfo info = context.getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d("AboutActivity", "onCreate()");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.about);

        Context context = this.getApplicationContext();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String version = getVersion();
        if (version.length() > 0) {
            SharedPreferences.Editor e = pref.edit();
            e.putString("prefVersion", version);
            e.commit();
        }

        Preference editTextPref = findPreference("prefVersion");
        if (editTextPref != null) {
            editTextPref.setSummary(version);
        } else {
            Log.e("AboutActivity", "prefVersion is null");
        }
    }
}
