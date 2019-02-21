package com.example.trigger;

import com.jcraft.jsch.KeyPair;


public class SshDoorSetup implements Setup {
    static final String type = "SshDoorSetup";
    int id;
    String name;
    KeyPair keypair;
    String user;
    String password;
    String host;
    int port;
    String open_command;
    String close_command;
    String state_command;
    String ssids;

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
