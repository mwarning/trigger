package com.example.trigger;


class TaskResult {
    final String message;
    final boolean is_error;

    private TaskResult(String message, boolean is_error) {
        this.message = message;
        this.is_error = is_error;
    }

    static TaskResult empty() {
        return new TaskResult("", false);
    }

    static TaskResult error(String msg) {
        return new TaskResult(msg, true);
    }

    static TaskResult msg(String msg) {
        return new TaskResult(msg, false);
    }
}
