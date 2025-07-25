/*
* Copyright (C) 2025 The Trigger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package app.trigger

// parsed door reply
class DoorStatus(val code: StateCode, val message: String) {
    enum class StateCode {
        OPEN, CLOSED, UNKNOWN, DISABLED
    }
}
