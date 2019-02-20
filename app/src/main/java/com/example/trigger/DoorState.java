package com.example.trigger;


enum StateCode {
    OPEN,
    CLOSED,
    UNKNOWN,
    DISABLED,
}

// parsed door reply
public class DoorState {
    final StateCode code;
    final String message;

    DoorState(StateCode code, String message) {
        this.code = code;
        this.message = message;
    }
}
