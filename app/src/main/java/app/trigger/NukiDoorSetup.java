package app.trigger;

import android.graphics.Bitmap;

import app.trigger.DoorState.StateCode;


public class NukiDoorSetup implements Setup {
    static final String type = "NukiDoorSetup";
    int id;
    String name;
    public String device_name;
    public String user_name;
    public String shared_key;
    public long auth_id;
    public long app_id;

    public Bitmap open_image;
    public Bitmap closed_image;
    public Bitmap unknown_image;
    public Bitmap disabled_image;

    public NukiDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.device_name = "";
        this.user_name = "";
        this.shared_key = "";
        this.auth_id = 0;
        this.app_id = 2342;
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
                if (reply.message.contains("unlocked")) {
                    // door unlocked
                    return new DoorState(StateCode.OPEN, msg);
                } else if (reply.message.contains("locked")) {
                    // door locked
                    return new DoorState(StateCode.CLOSED, msg);
                } else {
                    return new DoorState(StateCode.UNKNOWN, msg);
                }
            case DISABLED:
            default:
                return new DoorState(StateCode.DISABLED, msg);
        }
    }

    @Override
    public boolean canOpen() {
        return true;
    }

    @Override
    public boolean canClose() {
        return true;
    }

    @Override
    public boolean canRing() {
        return false;
    }
}
