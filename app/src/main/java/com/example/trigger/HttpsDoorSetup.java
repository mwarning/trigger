package com.example.trigger;


public class HttpsDoorSetup implements Setup {
    static final String type = "HttpsDoorSetup";
    int id;
    String name;
    String open_query;
    String close_query;
    String status_query;
    String ssids;
    Boolean ignore_cert;

    public HttpsDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.open_query = "";
        this.close_query = "";
        this.status_query = "";
        this.ssids = "";
        this.ignore_cert = false;
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
    public String getSSIDs() {
        return ssids;
    }

    @Override
    public String getType() {
        return type;
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
                if (msg.equals("UNLOCKED")) {
                    // door unlocked
                    return new DoorState(StateCode.OPEN, "");
                } else if (msg.equals("LOCKED")) {
                    // door locked
                    return new DoorState(StateCode.CLOSED, "");
                } else {
                    return new DoorState(StateCode.UNKNOWN, msg);
                }
            default:
                // should not happen
                return new DoorState(StateCode.UNKNOWN, msg);
        }
    }

    public String getOpenQuery() {
        return open_query;
    }

    public String getCloseQuery() {
        return close_query;
    }

    public String getStatusQuery() {
        return status_query;
    }

    public Boolean ignoreCertErrors() {
        return ignore_cert;
    }
}
