package com.example.trigger;

import android.graphics.Bitmap;
import android.graphics.drawable.Icon;

import com.example.trigger.DoorState.StateCode;

import java.security.cert.Certificate;


public class HttpsDoorSetup implements Setup {
    static final String type = "HttpsDoorSetup";
    int id;
    String name;
    public String method;
    public String open_query;
    public String close_query;
    public String ring_query;
    public String status_query;

    public Bitmap open_image;
    public Bitmap closed_image;
    public Bitmap unknown_image;
    public Bitmap disabled_image;

    public String ssids;
    public Certificate certificate;
    public Boolean ignore_hostname_mismatch;

    public HttpsDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.method = "";
        this.open_query = "";
        this.close_query = "";
        this.ring_query = "";
        this.status_query = "";
        this.open_image = null;
        this.closed_image = null;
        this.unknown_image = null;
        this.disabled_image = null;
        this.ssids = "";
        this.certificate = null;
        this.ignore_hostname_mismatch = false;
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
