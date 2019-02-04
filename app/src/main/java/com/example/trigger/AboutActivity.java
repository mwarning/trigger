package com.example.trigger;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceActivity;
import android.os.Bundle;
import android.util.Log;


public class AboutActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d("AboutActivity", "onCreate()");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.about);

        Context context = this.getApplicationContext();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String version = Settings.get_version();
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
