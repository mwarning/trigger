package app.trigger;

import android.graphics.Bitmap;
import android.graphics.drawable.Icon;

import app.trigger.DoorState.StateCode;

import java.security.cert.Certificate;


public class HttpsDoorSetup implements Setup {
    static final String type = "HttpsDoorSetup";
    int id;
    String name;
    public Boolean require_wifi;
    public String method;
    public String open_query;
    public String close_query;
    public String ring_query;
    public String status_query;

    // regex to evalute the door return message
    public String unlocked_pattern;
    public String locked_pattern;

    public Bitmap open_image;
    public Bitmap closed_image;
    public Bitmap unknown_image;
    public Bitmap disabled_image;

    public String ssids;
    public Certificate certificate;
    public Boolean ignore_certificate;
    public Boolean ignore_hostname_mismatch;
    public Boolean ignore_expiration;

    public HttpsDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.require_wifi = true;
        this.method = "";
        this.open_query = "";
        this.close_query = "";
        this.ring_query = "";
        this.status_query = "";
        this.locked_pattern = "LOCKED";
        this.unlocked_pattern = "UNLOCKED";
        this.open_image = null;
        this.closed_image = null;
        this.unknown_image = null;
        this.disabled_image = null;
        this.ssids = "";
        this.certificate = null;
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

    private static String stripUrls(String... urls) {
        // remove path
        String prefix = "https://";
        for (String url : urls) {
            if (url.startsWith(prefix)) {
                int i = url.indexOf('/', prefix.length());
                if (i > 0) {
                    url = url.substring(0, i);
                }
            }
            return url;
        }

        return "";
    }

    @Override
    public String getRegisterUrl() {
        // extract from known urls
        return stripUrls(open_query, ring_query, close_query, status_query);
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
