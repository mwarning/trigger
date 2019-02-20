package com.example.trigger;


public interface Setup {
    public abstract int getId();
    public abstract String getName();
    public abstract String getType();
    public abstract String getSSIDs();
    public abstract DoorState parseReply(DoorReply reply);
}
