/*
* Copyright (C) 2025 The Trigger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package app.trigger

import app.trigger.DoorReply.ReplyCode

interface OnTaskCompleted {
    fun onTaskResult(setupId: Int, action: MainActivity.Action, code: ReplyCode, message: String)
}
