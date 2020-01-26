package com.example.trigger;

import android.graphics.Bitmap;

import com.example.trigger.DoorState.StateCode;


public class BluetoothDoorSetup implements Setup {
    static final String type = "BluetoothDoorSetup";
    int id;
    String name;
    public String device_name;
    public String service_uuid;
    public String open_query;
    public String close_query;
    public String ring_query;
    public String status_query;
    public Bitmap open_image;
    public Bitmap closed_image;
    public Bitmap unknown_image;
    public Bitmap disabled_image;

    public BluetoothDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.device_name = "";
        this.service_uuid = "";
        this.open_query = "";
        this.close_query = "";
        this.ring_query = "";
        this.status_query = "";
        this.open_image = null;
        this.closed_image = null;
        this.unknown_image = null;
        this.disabled_image = null;
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
        return device_name;
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
        return "";
    }

    @Override
    public DoorState parseReply(DoorReply reply) {
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
        return Utils.isCommand(open_query);
    }

    @Override
    public boolean canClose() {
        return Utils.isCommand(close_query);
    }

    @Override
    public boolean canRing() {
        return Utils.isCommand(ring_query);
    }
}
