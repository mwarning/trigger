/*
* Copyright (C) 2025 The Trigger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package app.trigger

// reply from door
class DoorReply(val action: MainActivity.Action, val code: ReplyCode, val message: String) {
    enum class ReplyCode {
        LOCAL_ERROR,  // could establish a connection for some reason
        REMOTE_ERROR,  // the door send some error
        SUCCESS,  // the door send some message that has yet to be parsed
        DISABLED // Internet, WiFi or Bluetooth disabled or not supported
    }
}
