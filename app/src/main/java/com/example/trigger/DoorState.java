package com.example.trigger;


// parsed door reply
public class DoorState {
    final StateCode code;
    final String message;

    DoorState(StateCode code, String message) {
        this.code = code;
        this.message = message;
    }
}
