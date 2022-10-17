package app.trigger

import android.graphics.Bitmap
import android.content.*
import android.preference.SwitchPreference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference.OnPreferenceClickListener
import android.util.AttributeSet

/*
* Stores a string in preference. Indicated the presence of data with a switch.
* On click, the file chooser opens.
*/
class ImagePreference(private val context_: Context, attrs: AttributeSet?) : SwitchPreference(context_, attrs) {
    var image: Bitmap? = null
        set(value) {
            field = value
            isChecked = (value != null)
        }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any) {
        super.onSetInitialValue(restorePersistedValue, (image != null))
    }

    companion object {
        var self: ImagePreference? = null
    }

    init {
        val self = this

        // tell the superclass that we handle the value on out own!
        isPersistent = false
        isChecked = (image != null)
        this.onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
            self.isChecked = (self.image != null)
            false
        }
        this.onPreferenceClickListener = OnPreferenceClickListener { //String register_url = ((SetupActivity) self.context).getRegisterUrl();

            // store in public static member - hack!
            Companion.self = this@ImagePreference
            val intent = Intent(self.context, ImageActivity::class.java)
            //intent.putExtra("register_url", register_url);
            self.context.startActivity(intent)
            false
        }
    }
}
