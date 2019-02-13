package com.example.trigger;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceActivity;
import android.os.Bundle;


public class AboutActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.about);

        Context context = this.getApplicationContext();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String version = Settings.getVersion();

        Preference versionPref = findPreference("prefVersion");
        versionPref.setSummary(version);
    }
}
