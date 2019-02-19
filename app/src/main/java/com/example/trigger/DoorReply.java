package com.example.trigger;


// reply from door
public class DoorReply {
    final ReplyCode code;
    final String message;

    DoorReply(ReplyCode code, String message) {
        this.code = code;
        this.message = message;
    }

    static DoorReply internal_error() {
        return new DoorReply(ReplyCode.LOCAL_ERROR, "Internal Error");
    }
}
