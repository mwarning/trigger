package com.example.trigger;

public enum ReplyCode {
    LOCAL_ERROR, // could establish a connection for some reason
    REMOTE_ERROR, // the door send some error
    SUCCESS, // the door send some message that has yet to be parsed
}
