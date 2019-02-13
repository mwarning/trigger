package com.example.trigger;

import java.util.ArrayList;

import static com.example.trigger.Utils.*;


public class HttpsDoorSetup implements Setup {
    static final String type = "HttpsDoorSetup";
    int id;
    String name;
    String open_query;
    String close_query;
    String status_query;
    String ssids;
    Boolean ignore_cert;
    Boolean enable_status;

    public HttpsDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.open_query = "";
        this.close_query = "";
        this.status_query = "";
        this.ssids = "";
        this.ignore_cert = false;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSSIDs() {
        return ssids;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getOpenQuery() {
        return open_query;
    }

    public String getCloseQuery() {
        return close_query;
    }

    public String getStatusQuery() {
        return status_query;
    }

    public Boolean ignoreCertErrors() {
        return ignore_cert;
    }

    @Override
    public void getAllSettings(ArrayList<Pair> ps) {
        ps.add(pair("name", name));
        ps.add(pair("open_query", open_query));
        ps.add(pair("close_query", close_query));
        ps.add(pair("status_query", status_query));
        ps.add(pair("ssids", ssids));
        ps.add(pair("ignore_cert", ignore_cert));
    }

    @Override
    public void setAllSettings(ArrayList<Pair> ps) {
        for (Pair p : ps) {
            switch (p.key) {
                case "name": name = (String) p.value; break;
                case "open_query": open_query = (String) p.value; break;
                case "close_query": close_query = (String) p.value; break;
                case "status_query": status_query = (String) p.value; break;
                case "ssids": ssids = (String) p.value; break;
                case "ignore_cert": ignore_cert = (Boolean) p.value; break;
            }
        }
    }
}
