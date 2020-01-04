package com.example.trigger;

import android.graphics.Bitmap;
import android.graphics.drawable.Icon;

import com.example.trigger.DoorState.StateCode;


public interface Setup {
    // internal id
    public abstract int getId();

    // Name of this setup for dropdown menu
    public abstract String getName();

    // Door mechanism type name
    public abstract String getType();

    // Select setup entry from dropdown if it
    // matches any of these SSIDs (comma separated)
    public abstract String getSSIDs();

    // Get image dependent of the door state
    public abstract Bitmap getStateImage(StateCode state);

    // URL to fetch a https certificate from
    // or to send a ssh public key for registration
    public abstract String getRegisterUrl();

    // Parse the text reply from
    // the door and determine state
    public abstract DoorState parseReply(DoorReply reply);

    // To show/hide button
    public abstract boolean canOpen();
    public abstract boolean canClose();
    public abstract boolean canRing();
}
