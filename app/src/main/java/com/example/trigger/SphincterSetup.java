package com.example.trigger;


public class SphincterSetup implements Setup {
    int id;
    String name;
    String url;
    String token;
    Boolean ignore;

    public SphincterSetup(int id, String name, String url, String token, Boolean ignore) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.token = token;
        this.ignore = ignore;
    }

    public String toString() {
        return this.name;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getType() {
        return "sphincter";
    }
}
