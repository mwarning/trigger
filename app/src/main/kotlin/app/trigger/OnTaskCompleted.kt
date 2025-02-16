package app.trigger

import app.trigger.DoorReply.ReplyCode

interface OnTaskCompleted {
    fun onTaskResult(setupId: Int, action: MainActivity.Action, code: ReplyCode, message: String)
}
