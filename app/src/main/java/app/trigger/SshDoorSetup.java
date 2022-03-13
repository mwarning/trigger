package app.trigger;

import android.graphics.Bitmap;

import app.trigger.DoorState.StateCode;
import app.trigger.ssh.KeyPairBean;


public class SshDoorSetup implements Setup {
    static final String type = "SshDoorSetup";
    int id;
    String name;
    public Boolean require_wifi;
    public KeyPairBean keypair;
    public String user;
    public String password;
    public String host;
    public int port;

    public String open_command;
    public String close_command;
    public String ring_command;
    public String state_command;

    // regex to evalute the door return message
    public String unlocked_pattern;
    public String locked_pattern;

    public Bitmap open_image;
    public Bitmap closed_image;
    public Bitmap unknown_image;
    public Bitmap disabled_image;

    public String register_url;
    public String ssids;

    public int timeout; // milliseconds

    public SshDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.require_wifi = true;
        this.keypair = null;
        this.user = "";
        this.password = "";
        this.host = "";
        this.port = 22;
        this.open_command = "";
        this.close_command = "";
        this.ring_command = "";
        this.state_command = "";
        this.locked_pattern = "LOCKED";
        this.unlocked_pattern = "UNLOCKED";
        this.open_image = null;
        this.closed_image = null;
        this.unknown_image = null;
        this.disabled_image = null;
        this.register_url = "";
        this.ssids = "";
        this.timeout = 5000;
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
    public boolean getWiFiRequired() {
        return require_wifi;
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
        if (!Utils.isEmpty(register_url)) {
            return register_url;
        }

        return host;
    }

    @Override
    public DoorState parseReply(DoorReply reply) {
        return Utils.genericDoorReplyParser(reply, this.unlocked_pattern, this.locked_pattern);
    }

    @Override
    public boolean canOpen() {
        return !Utils.isEmpty(open_command);
    }

    @Override
    public boolean canClose() {
        return !Utils.isEmpty(close_command);
    }

    @Override
    public boolean canRing() {
        return !Utils.isEmpty(ring_command);
    }
}
