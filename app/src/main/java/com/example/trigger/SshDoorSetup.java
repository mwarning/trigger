package com.example.trigger;

import java.util.ArrayList;

import static com.example.trigger.Utils.*;


public class SshDoorSetup implements Setup {
    static final String type = "SshDoorSetup";
    int id;
    String name;
    String keypath;
    String user;
    String host;
    String port;
    String open_command;
    String close_command;
    String state_command;
    String ssids;

    public SshDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.keypath = "";
        this.user = "";
        this.host = "";
        this.port = "22";
        this.open_command = "";
        this.close_command = "";
        this.state_command = "";
        this.ssids = "";
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
    public String getType() {
        return type;
    }

    public String getKeyPath() {
        return host;
    }

    public String getHost() {
        return host;
    }

    public String getUser() {
        return user;
    }

    public String getPort() {
        return port;
    }

    public String getOpenCommand() {
        return open_command;
    }

    public String getCloseCommand() {
        return close_command;
    }

    public String getStateCommand() {
        return state_command;
    }

    public String getSSIDs() {
        return ssids;
    }

    @Override
    public void getAllSettings(ArrayList<Pair> ps) {
        ps.add(pair("name", name));
        ps.add(pair("keypath", keypath));
        ps.add(pair("host", host));
        ps.add(pair("port", port));
        ps.add(pair("user", user));
        ps.add(pair("open_command", open_command));
        ps.add(pair("close_command", close_command));
        ps.add(pair("state_command", state_command));
        ps.add(pair("ssids", ssids));
    }

    @Override
    public void setAllSettings(ArrayList<Pair> ps) {
        for (Pair p : ps) {
            switch (p.key) {
                case "name": name = (String) p.value; break;
                case "keypath": keypath = (String) p.value; break;
                case "host": host = (String) p.value; break;
                case "port": port = (String) p.value; break;
                case "user": user = (String) p.value; break;
                case "open_command": open_command = (String) p.value; break;
                case "close_command": close_command = (String) p.value; break;
                case "state_command": state_command = (String) p.value; break;
                case "ssids": ssids = (String) p.value; break;
            }
        }
    }
}
