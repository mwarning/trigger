package com.example.trigger;

import com.example.trigger.ssh.SshTools;
import com.jcraft.jsch.KeyPair;

import java.util.ArrayList;

import static com.example.trigger.Utils.*;


public class SshDoorSetup implements Setup {
    static final String type = "SshDoorSetup";
    int id;
    String name;
    KeyPair keypair;
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
        this.keypair = null;
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

    @Override
    public DoorState parseReply(DoorReply reply) {
        // TODO: This is probably not the expected way to parse the reply.
        // It probably needs to be made configurable (setting with regex?)
        if (reply.message.contains("UNLOCKED")) {
            // door unlocked
            return new DoorState(StateCode.OPEN, "");
        } else if (reply.message.contains("LOCKED")) {
            // door locked
            return new DoorState(StateCode.CLOSED, "");
        } else {
            return new DoorState(StateCode.UNKNOWN, reply.message);
        }
    }

    public KeyPair getKeyPair() {
        return keypair;
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
}
