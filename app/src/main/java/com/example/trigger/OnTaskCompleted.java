package com.example.trigger;

public interface OnTaskCompleted {
    void onTaskResult(int setup_id, DoorReply.ReplyCode code, String message);
}

