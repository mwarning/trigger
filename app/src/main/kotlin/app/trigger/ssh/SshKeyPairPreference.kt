package app.trigger.ssh

import android.content.Intent
import android.preference.SwitchPreference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference.OnPreferenceClickListener
import app.trigger.SetupActivity
import android.content.Context
import android.util.AttributeSet

/*
 * Preference to indicate the presence of data with a switch.
 * On click, the SshKeyPairActivity opens.
 */
class SshKeyPairPreference(private val context: Context, attrs: AttributeSet?) : SwitchPreference(context, attrs) {
    private var keypair: KeyPairBean? = null
    var keyPair: KeyPairBean?
        get() = keypair
        set(keypair) {
            this.keypair = keypair
            isChecked = (this.keypair != null)
        }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any) {
        if (keypair == null) {
            super.onSetInitialValue(restorePersistedValue, false)
        } else {
            super.onSetInitialValue(restorePersistedValue, true)
        }
    }

    companion object {
        var self: SshKeyPairPreference? = null
    }

    init {
        val self = this

        // tell the superclass that we handle the value on out own!
        isPersistent = false
        isChecked = (keypair != null)
        this.onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
            self.isChecked = (self.keypair != null)
            false
        }
        this.onPreferenceClickListener = OnPreferenceClickListener {
            val register_url = (self.context as SetupActivity).getRegisterUrl()

            // store in public static member - hack!
            Companion.self = this@SshKeyPairPreference
            val intent = Intent(self.context, SshKeyPairActivity::class.java)
            intent.putExtra("register_url", register_url)
            self.context.startActivity(intent)
            false
        }
    }
}