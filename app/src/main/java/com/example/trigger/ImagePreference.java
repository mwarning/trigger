package com.example.trigger;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.AttributeSet;

import com.example.trigger.SetupActivity;
import com.jcraft.jsch.KeyPair;

import android.graphics.Bitmap;

/*
* Stores a string in preference. Indicated the presence of data with a switch.
* On click, the file chooser opens.
*/
public class ImagePreference extends SwitchPreference {
    private Bitmap image;
    private Context context;
    static ImagePreference self;

    public ImagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        final ImagePreference self = this;

        // tell the superclass that we handle the value on out own!
        setPersistent(false);

        if (this.image == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }

        this.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (self.image == null) {
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
                //String register_url = ((SetupActivity) self.context).getRegisterUrl();

                // store in public static member - hack!
                ImagePreference.self = ImagePreference.this;

                Intent intent = new Intent(self.context, ImageActivity.class);
                //intent.putExtra("register_url", register_url);
                self.context.startActivity(intent);

                return false;
            }
        });
    }

    public void setImage(Bitmap image) {
        this.image = image;

        if (this.image == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }
    }

    public Bitmap getImage() {
        return image;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (image == null) {
            super.onSetInitialValue(restorePersistedValue, false);
        } else {
            super.onSetInitialValue(restorePersistedValue, true);
        }
    }
}
