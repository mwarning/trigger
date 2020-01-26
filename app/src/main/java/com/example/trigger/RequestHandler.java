package com.example.trigger;

import android.os.AsyncTask;


// reply from door
public abstract class RequestHandler extends AsyncTask<Object, Void, DoorReply> {

    protected void stop() {
        // default implementation
    }
}
