package app.trigger.ssh;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.AttributeSet;

import app.trigger.SetupActivity;

/*
 * Preference to indicate the presence of data with a switch.
 * On click, the SshKeyPairActivity opens.
 */
public class SshKeyPairPreference extends SwitchPreference {
    private KeyPairBean keypair;
    private Context context;
    static SshKeyPairPreference self;

    public SshKeyPairPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        final SshKeyPairPreference self = this;

        // tell the superclass that we handle the value on out own!
        setPersistent(false);

        if (this.keypair == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }

        this.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (self.keypair == null) {
                    self.setChecked(false);
                } else {
                    self.setChecked(true);
                }
                
                return false;
            }
        });

        this.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                String register_url = ((SetupActivity) self.context).getRegisterUrl();

                // store in public static member - hack!
                SshKeyPairPreference.self = SshKeyPairPreference.this;

                Intent intent = new Intent(self.context, SshKeyPairActivity.class);
                intent.putExtra("register_url", register_url);
                self.context.startActivity(intent);

                return false;
            }
        });
    }

    public void setKeyPair(KeyPairBean keypair) {
        this.keypair = keypair;

        if (this.keypair == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }
    }

    public KeyPairBean getKeyPair() {
        return keypair;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (keypair == null) {
            super.onSetInitialValue(restorePersistedValue, false);
        } else {
            super.onSetInitialValue(restorePersistedValue, true);
        }
    }
}
