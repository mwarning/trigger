package com.example.trigger;

import com.jcraft.jsch.KeyPair;

import com.example.trigger.DoorState.StateCode;


public class SshDoorSetup implements Setup {
    static final String type = "SshDoorSetup";
    int id;
    String name;
    public KeyPair keypair;
    public String user;
    public String password;
    public String host;
    public int port;
    public String open_command;
    public String close_command;
    public String state_command;
    public String ssids;

    public SshDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.keypair = null;
        this.user = "";
        this.password = "";
        this.host = "";
        this.port = 22;
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

    @Override
    public String getSSIDs() {
        return ssids;
    }

    @Override
    public String getRegisterUrl() {
        return host + ":" + port;
    }

    @Override
    public DoorState parseReply(DoorReply reply) {
        // TODO: This is probably not the expected way to parse the reply.
        // It probably needs to be made configurable (setting with regex?)
        String msg = reply.message.trim();
        if (msg.contains("UNLOCKED")) {
            // door unlocked
            return new DoorState(StateCode.OPEN, msg);
        } else if (reply.message.contains("LOCKED")) {
            // door locked
            return new DoorState(StateCode.CLOSED, msg);
        } else {
            return new DoorState(StateCode.UNKNOWN, msg);
        }
    }
}
