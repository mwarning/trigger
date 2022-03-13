package app.trigger;

import android.graphics.Bitmap;

import app.trigger.DoorState.StateCode;


public class BluetoothDoorSetup implements Setup {
    static final String type = "BluetoothDoorSetup";
    int id;
    String name;
    public String device_name;
    public String service_uuid;
    public String open_query;
    public String close_query;
    public String ring_query;
    public String locked_pattern;
    public String unlocked_pattern;
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
        this.locked_pattern = "LOCKED";
        this.unlocked_pattern = "UNLOCKED";
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
    public String getWiFiSSIDs() {
        return device_name;
    }

    @Override
    public boolean getWiFiRequired() {
        return false;
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
        return Utils.genericDoorReplyParser(reply, this.unlocked_pattern, this.locked_pattern);
    }

    @Override
    public boolean canOpen() {
        return !Utils.isEmpty(open_query);
    }

    @Override
    public boolean canClose() {
        return !Utils.isEmpty(close_query);
    }

    @Override
    public boolean canRing() {
        return !Utils.isEmpty(ring_query);
    }
}
