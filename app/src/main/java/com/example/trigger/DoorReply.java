package com.example.trigger;


enum ReplyCode {
    LOCAL_ERROR, // could establish a connection for some reason
    REMOTE_ERROR, // the door send some error
    SUCCESS, // the door send some message that has yet to be parsed
}

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
