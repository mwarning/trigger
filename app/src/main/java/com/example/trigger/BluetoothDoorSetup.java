package com.example.trigger;


import com.example.trigger.DoorState;
import com.example.trigger.DoorState.StateCode;


public class BluetoothDoorSetup implements Setup {
    static final String type = "BluetoothDoorSetup";
    int id;
    String name;
    public String server_address;
    public String open_query;
    public String close_query;
    public String status_query;

    public BluetoothDoorSetup(int id, String name) {
        this.id = id;
        this.name = name;
        this.server_address = "";
        this.open_query = "";
        this.close_query = "";
        this.status_query = "";
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
        return server_address;
    }

    @Override
    public String getRegisterUrl() {
        return "";
    }

    @Override
    public DoorState parseReply(DoorReply reply) {
        String msg = reply.message.trim();

        switch (reply.code) {
            case LOCAL_ERROR:
            case REMOTE_ERROR:
                return new DoorState(StateCode.UNKNOWN, msg);
            case SUCCESS:
                if (msg.equals("UNLOCKED")) {
                    // door unlocked
                    return new DoorState(StateCode.OPEN, msg);
                } else if (msg.equals("LOCKED")) {
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
}
