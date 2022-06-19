package app.trigger

import app.trigger.DoorReply.ReplyCode


interface OnTaskCompleted {
    fun onTaskResult(setup_id: Int, code: ReplyCode, message: String)
}
