package com.example.trigger;


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

    // URL to fetch a https certificate from
    // or to send a ssh public key for registration
    public abstract String getRegisterUrl();

    // Parse the text reply from
    // the door and determine state
    public abstract DoorState parseReply(DoorReply reply);
}
