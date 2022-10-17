package app.trigger.https

import android.content.Intent
import android.preference.SwitchPreference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference
import android.preference.Preference.OnPreferenceClickListener
import app.trigger.SetupActivity
import android.content.Context
import android.util.AttributeSet
import java.security.cert.Certificate

/*
* Stores a string in preference. Indicated the presence of data with a switch.
* On click, the file chooser opens.
*/
class CertificatePreference(private val context_: Context, attrs: AttributeSet?) : SwitchPreference(context_, attrs) {
    public var certificate: Certificate? = null
        set(value) {
            field = value
            isChecked = (certificate != null)
        }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any) {
        if (certificate == null) {
            super.onSetInitialValue(restorePersistedValue, false)
        } else {
            super.onSetInitialValue(restorePersistedValue, true)
        }
    }

    companion object {
        var self: CertificatePreference? = null
    }

    init {
        val self = this

        // tell the superclass that we handle the value on out own!
        isPersistent = false
        isChecked = (certificate != null)
        this.onPreferenceChangeListener = OnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
            self.isChecked = (self.certificate != null)
            false
        }
        this.onPreferenceClickListener = OnPreferenceClickListener { preference: Preference? ->
            val register_url = (self.context as SetupActivity).getRegisterUrl()

            // store in public static member - hack!
            Companion.self = self
            val intent = Intent(self.context, CertificateActivity::class.java)
            intent.putExtra("register_url", register_url)
            intent.putExtra("certificate_preference_title", self.title.toString())
            self.context.startActivity(intent)
            false
        }
    }
}