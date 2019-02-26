package com.example.trigger;


// reply from door
public class DoorReply {
    final ReplyCode code;
    final String message;

    public enum ReplyCode {
        LOCAL_ERROR, // could establish a connection for some reason
        REMOTE_ERROR, // the door send some error
        SUCCESS, // the door send some message that has yet to be parsed
    }

    public DoorReply(ReplyCode code, String message) {
        this.code = code;
        this.message = message;
    }

    public static DoorReply internal_error() {
        return new DoorReply(ReplyCode.LOCAL_ERROR, "Internal Error");
    }
}
