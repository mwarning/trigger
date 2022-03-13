package app.trigger;

import android.graphics.Bitmap;
import app.trigger.DoorState.StateCode;
import app.trigger.ssh.KeyPairBean;

import java.security.cert.Certificate;


public class MqttDoorSetup implements Setup {
    static final String type = "MqttDoorSetup";
    int id;
    String name;
    public Boolean require_wifi;
    public String username;
    public String password;
    public String server;
    public String status_topic;
    public String command_topic;
    public Boolean retained;
    public int qos;

    public String open_command;
    public String close_command;
    public String ring_command;

    public String locked_pattern;
    public String unlocked_pattern;

    public Bitmap open_image;
    public Bitmap closed_image;
    public Bitmap unknown_image;
    public Bitmap disabled_image;

    public String ssids;
    public Certificate server_certificate;
    public Certificate client_certificate;
    public KeyPairBean client_keypair;

    public Boolean ignore_certificate;
    public Boolean ignore_hostname_mismatch;
    public Boolean ignore_expiration;

    public MqttDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.require_wifi = true;
        this.username = "";
        this.password = "";
        this.server = "";
        this.status_topic = "";
        this.command_topic = "";
        this.open_command = "";
        this.close_command = "";
        this.ring_command = "";
        this.locked_pattern = "LOCKED";
        this.unlocked_pattern = "UNLOCKED";
        this.retained = false;
        this.qos = 0;
        this.open_image = null;
        this.closed_image = null;
        this.unknown_image = null;
        this.disabled_image = null;
        this.ssids = "";
        this.ignore_certificate = false;
        this.ignore_hostname_mismatch = false;
        this.ignore_expiration = false;
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
        return null;
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
