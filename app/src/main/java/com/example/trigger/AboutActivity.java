package com.example.trigger;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceActivity;
import android.os.Bundle;
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
