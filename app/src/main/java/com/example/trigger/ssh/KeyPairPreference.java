package com.example.trigger.ssh;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.util.Log;

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

        // tell the superclass that we handle the value on out own!
        setPersistent(false);

        if (this.keypair == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }

        Log.d("KeyPairPreference", "onload");
        this.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                KeyPairPreference p = (KeyPairPreference) preference;

                if (p.keypair == null) {
                    p.setChecked(false);
                } else {
                    p.setChecked(true);
                }
                
                return false;
            }
        });

        this.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                KeyPairPreference p = (KeyPairPreference) preference;

                // hack!
                KeyPairPreference.self = KeyPairPreference.this;
                Intent i = new Intent(p.context, KeyPairActivity.class);
                //i.putExtra("setup_id", setup_id);
                //SetupActivity needs to implement onActivityResult and set this preference ??
                //((SetupActivity)p.context).startActivityForResult(i, 42);

                p.context.startActivity(i);
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
        Log.d("KeyPairPreference", "onSetInitialValue");
        super.onSetInitialValue(restorePersistedValue, defaultValue);
    }
}
