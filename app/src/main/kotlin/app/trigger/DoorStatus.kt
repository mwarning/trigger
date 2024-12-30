package app.trigger

// parsed door reply
class DoorStatus(val code: StateCode, val message: String) {
    enum class StateCode {
        OPEN, CLOSED, UNKNOWN, DISABLED
    }
}
