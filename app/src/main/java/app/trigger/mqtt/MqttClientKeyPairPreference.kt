package app.trigger.mqtt

import android.content.Intent
import android.preference.SwitchPreference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference
import android.preference.Preference.OnPreferenceClickListener
import app.trigger.SetupActivity
import app.trigger.ssh.KeyPairBean
import android.content.Context
import android.util.AttributeSet

/*
* Preference to indicate the presence of data with a switch.
* On click, the MqttClientKeypairActivity opens.
*/
class MqttClientKeyPairPreference(private val context: Context, attrs: AttributeSet?) : SwitchPreference(context, attrs) {
    private var clientKey: KeyPairBean? = null

    fun getKeyPair(): KeyPairBean? {
        return clientKey
    }
    fun setKeyPair(key: KeyPairBean?) {
        clientKey = key
        isChecked = (clientKey != null)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any) {
        if (clientKey == null) {
            super.onSetInitialValue(restorePersistedValue, false)
        } else {
            super.onSetInitialValue(restorePersistedValue, true)
        }
    }

    companion object {
        private const val TAG = "MqttClientKeyPairPreference"
        var self: MqttClientKeyPairPreference? = null
    }

    init {
        val self = this

        // tell the superclass that we handle the value on out own!
        isPersistent = false
        isChecked = (clientKey != null)
        this.onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
            self.isChecked = (self.clientKey != null)
            false
        }
        this.onPreferenceClickListener = OnPreferenceClickListener { preference: Preference? ->
            val register_url = (self.context as SetupActivity).getRegisterUrl()

            // store in public static member - hack!
            Companion.self = this@MqttClientKeyPairPreference
            val intent = Intent(self.context, MqttClientKeyPairActivity::class.java)
            intent.putExtra("register_url", register_url)
            self.context.startActivity(intent)
            false
        }
    }
}