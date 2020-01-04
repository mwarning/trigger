package com.example.trigger;

import android.graphics.Bitmap;

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
    public String ring_command;
    public String state_command;
    public Bitmap open_image;
    public Bitmap closed_image;
    public Bitmap unknown_image;
    public Bitmap disabled_image;
    public String register_url;
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
        this.ring_command = "";
        this.state_command = "";
        this.open_image = null;
        this.closed_image = null;
        this.unknown_image = null;
        this.disabled_image = null;
        this.register_url = "";
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
    public Bitmap getStateImage(StateCode state) {
        switch (state) {
            case OPEN:
                return open_image;
            case CLOSED:
                return closed_image;
            case DISABLED:
                return disabled_image;
            case UNKNOWN:
                return unknown_image;
            default:
                return null;
        }
    }

    @Override
    public String getRegisterUrl() {
        return this.register_url;
    }

    @Override
    public DoorState parseReply(DoorReply reply) {
        String msg = android.text.Html.fromHtml(reply.message).toString().trim();

        if (reply.message.contains("UNLOCKED")) {
            // door unlocked
            return new DoorState(StateCode.OPEN, msg);
        } else if (reply.message.contains("LOCKED")) {
            // door locked
            return new DoorState(StateCode.CLOSED, msg);
        } else {
            return new DoorState(StateCode.UNKNOWN, msg);
        }
    }

    @Override
    public boolean canOpen() {
        return Utils.isCommand(open_command);
    }

    @Override
    public boolean canClose() {
        return Utils.isCommand(close_command);
    }

    @Override
    public boolean canRing() {
        return Utils.isCommand(ring_command);
    }
}
