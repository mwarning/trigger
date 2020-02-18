package com.example.trigger;

import android.graphics.Bitmap;

import com.example.trigger.DoorState.StateCode;

import java.security.cert.Certificate;


public class MqttDoorSetup implements Setup {
    static final String type = "MqttDoorSetup";
    int id;
    String name;
    public String username;
    public String password;
    public String server;
    public String status_topic;
    public String command_topic;
    public String open_command;
    public String close_command;
    public String ring_command;
    public Boolean retained;
    public int qos;

    public Bitmap open_image;
    public Bitmap closed_image;
    public Bitmap unknown_image;
    public Bitmap disabled_image;

    public String ssids;
    public Certificate certificate;

    public MqttDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.username = "";
        this.password = "";
        this.server = "";
        this.status_topic = "";
        this.command_topic = "";
        this.open_command = "";
        this.close_command = "";
        this.ring_command = "";
        this.retained = false;
        this.qos = 0;
        this.open_image = null;
        this.closed_image = null;
        this.unknown_image = null;
        this.disabled_image = null;
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
        return null;
    }

    @Override
    public DoorState parseReply(DoorReply reply) {
        // strip HTML from response
        String msg = android.text.Html.fromHtml(reply.message).toString().trim();

        switch (reply.code) {
            case LOCAL_ERROR:
            case REMOTE_ERROR:
                return new DoorState(StateCode.UNKNOWN, msg);
            case SUCCESS:
                if (reply.message.contains("UNLOCKED")) {
                    // door unlocked
                    return new DoorState(StateCode.OPEN, msg);
                } else if (reply.message.contains("LOCKED")) {
                    // door locked
                    return new DoorState(StateCode.CLOSED, msg);
                } else {
                    return new DoorState(StateCode.UNKNOWN, msg);
                }
            default:
                // should not happen
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
