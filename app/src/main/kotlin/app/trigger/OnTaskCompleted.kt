package app.trigger

import app.trigger.DoorReply.ReplyCode

interface OnTaskCompleted {
    fun onTaskResult(setupId: Int, code: ReplyCode, message: String)
}
