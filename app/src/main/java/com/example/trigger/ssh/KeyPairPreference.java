package com.example.trigger.ssh;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.AttributeSet;

import com.example.trigger.SetupActivity;
import com.jcraft.jsch.KeyPair;

/*
* Stores a string in preference. Indicated the presence of data with a switch.
* On click, the file chooser opens.
*/
public class KeyPairPreference extends SwitchPreference {
    private KeyPair keypair;
    private Context context;
    static KeyPairPreference self;

    public KeyPairPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        final KeyPairPreference self = this;

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
                KeyPairPreference.self = KeyPairPreference.this;

                Intent intent = new Intent(self.context, KeyPairActivity.class);
                intent.putExtra("register_url", register_url);
                self.context.startActivity(intent);

                return false;
            }
        });
    }

    public void setKeyPair(KeyPair keypair) {
        this.keypair = keypair;

        if (this.keypair == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }
    }

    public KeyPair getKeyPair() {
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
