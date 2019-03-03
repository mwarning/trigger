package com.example.trigger.https;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.util.Log;

import java.security.cert.Certificate;

import com.example.trigger.SetupActivity;


/*
* Stores a string in preference. Indicated the presence of data with a switch.
* On click, the file chooser opens.
*/
public class CertificatePreference extends SwitchPreference {
    private Certificate certificate;
    private Context context;
    static CertificatePreference self;

    public CertificatePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        // tell the superclass that we handle the value on out own!
        setPersistent(false);

        if (this.certificate == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }

        this.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                CertificatePreference p = (CertificatePreference) preference;

                if (p.certificate == null) {
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
                CertificatePreference p = CertificatePreference.this;
                String fetch_url = ((SetupActivity) p.context).getRegisterUrl();

                // store in public static member - hack!
                CertificatePreference.self = p;

                Intent i = new Intent(p.context, CertificateActivity.class);
                i.putExtra("fetch_url", fetch_url);

                p.context.startActivity(i);
                return false;
            }
        });
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;

        if (this.certificate == null) {
            setChecked(false);
        } else {
            setChecked(true);
        }
    }

    public Certificate getCertificate() {
        return certificate;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (certificate == null) {
            super.onSetInitialValue(restorePersistedValue, false);
        } else {
            super.onSetInitialValue(restorePersistedValue, true);
        }
    }
}
