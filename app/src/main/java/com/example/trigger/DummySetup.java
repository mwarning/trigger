package com.example.trigger;


public class DummySetup implements Setup {
    String name;

    public DummySetup(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public int getId() {
        return -1;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public java.lang.String getSSIDs() {
        return "";
    }

    @Override
    public String getType() {
        return "dummy";
    }
}
