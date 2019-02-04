package com.example.trigger;

public interface Setup {
    public abstract String getName();
    public abstract String getType();
    public abstract String toString(); // for ArrayAdapter
    public abstract String getSSIDs();
    public abstract int getId();
}
