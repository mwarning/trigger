package com.example.trigger;


public class SphincterSetup implements Setup {
    int id;
    String name;
    String url;
    String token;
    String ssids;
    Boolean ignore;

    public SphincterSetup(int id, String name, String url, String token, String ssids, Boolean ignore) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.token = token;
        this.ssids = ssids;
        this.ignore = ignore;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getSSIDs() {
        return this.ssids;
    }

    @Override
    public String getType() {
        return "sphincter";
    }
}
