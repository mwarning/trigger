package app.trigger

import android.preference.PreferenceActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.preference.Preference

class AboutActivity : PreferenceActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.about)
        val context = this.applicationContext
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val versionPref = findPreference("prefVersion")

        versionPref.summary = if (BuildConfig.DEBUG) {
            BuildConfig.VERSION_NAME + " (debug)"
        } else {
            BuildConfig.VERSION_NAME
        }
    }
}
