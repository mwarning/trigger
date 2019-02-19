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
    private KeyPair keyPair;
    private Context context;
    static KeyPairPreference self;
/*
    KeyPairPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        onload();
    }

    KeyPairPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        onload();
    }
*/
    public KeyPairPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        // tell the superclass that we handle the value on out own!
        setPersistent(false);

        Log.d("KeyPairPreference", "onload");
        this.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Log.d("KeyPairPreference", "onPreferenceChange");
                //KeyPairPreference p = (KeyPairPreference) preference; 
                // Here you can enable/disable whatever you need to
                // open selection here and store result here, then turn switch
                // take code from SetupActivity...
                //p.startFileSelection();
                
                return false;
            }
        });

        this.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Log.d("KeyPairPreference", "onPreferenceClick");
                KeyPairPreference p = (KeyPairPreference) preference;
                // Here you can enable/disable whatever you need to
                // open selection here and store result here, then turn switch
                // take code from SetupActivity...
                p.setChecked(true);
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

    public void setKeyPair(KeyPair keyPair) {
        Log.d("KeyPairPreference", "setKeyPair");

        if (keyPair == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }

        this.keyPair = keyPair;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

/*
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return 
    }
*/

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        Log.d("KeyPairPreference", "onSetInitialValue");
        super.onSetInitialValue(restorePersistedValue, defaultValue);
        // If the value can be restored, do it. If not, use the default value.
        //setTime(restorePersistedValue ?
        //        getPersistedInt(mTime) : (int) defaultValue);
    }
}
