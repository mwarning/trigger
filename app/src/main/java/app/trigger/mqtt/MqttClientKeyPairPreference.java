package app.trigger.mqtt;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.AttributeSet;

import app.trigger.SetupActivity;
import app.trigger.ssh.KeyPairBean;

/*
* Preference to indicate the presence of data with a switch.
* On click, the MqttClientKeypairActivity opens.
*/
public class MqttClientKeyPairPreference extends SwitchPreference {
    private KeyPairBean clientKey;
    private Context context;
    static MqttClientKeyPairPreference self;

    public MqttClientKeyPairPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        final MqttClientKeyPairPreference self = this;

        // tell the superclass that we handle the value on out own!
        setPersistent(false);

        if (this.clientKey == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }

        this.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (self.clientKey == null) {
                    self.setChecked(false);
                } else {
                    self.setChecked(true);
                }

                return false;
            }
        });

        this.setOnPreferenceClickListener((Preference preference) -> {
            String register_url = ((SetupActivity) self.context).getRegisterUrl();

            // store in public static member - hack!
            MqttClientKeyPairPreference.self = MqttClientKeyPairPreference.this;

            Intent intent = new Intent(self.context, MqttClientKeyPairActivity.class);
            intent.putExtra("register_url", register_url);
            self.context.startActivity(intent);

            return false;
        });
    }

    public void setKeyPair(KeyPairBean key) {
        this.clientKey = key;

        if (this.clientKey == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }
    }

    public KeyPairBean getKeyPair() {
        return clientKey;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (clientKey == null) {
            super.onSetInitialValue(restorePersistedValue, false);
        } else {
            super.onSetInitialValue(restorePersistedValue, true);
        }
    }
}
