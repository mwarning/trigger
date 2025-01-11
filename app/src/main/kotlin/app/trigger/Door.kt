package app.trigger

import android.graphics.Bitmap
import app.trigger.DoorStatus.StateCode

abstract class Door {
    // internal id
    abstract var id: Int

    // Name of this setup for dropdown menu
    abstract var name: String

    // Door mechanism type name
    abstract val type: String

    var open_image: Bitmap? = null
    var closed_image: Bitmap? = null
    var unknown_image: Bitmap? = null
    var disabled_image: Bitmap? = null

    // Select setup entry from dropdown if it
    // matches any of these SSIDs (comma separated)
    abstract fun getWiFiSSIDs(): String

    // only applies for HTTPS, SSH and MQTT so far
    abstract fun getWiFiRequired(): Boolean

    // Get image dependent of the door state
    //fun getStateImage(state: StateCode?): Bitmap?
    fun getStateImage(state: StateCode?): Bitmap? {
        return when (state) {
            StateCode.OPEN -> open_image
            StateCode.CLOSED -> closed_image
            StateCode.DISABLED -> disabled_image
            StateCode.UNKNOWN -> unknown_image
            else -> null
        }
    }

    fun setStateImage(state: StateCode, bitmap: Bitmap?) {
        when (state) {
            StateCode.OPEN -> open_image = bitmap
            StateCode.CLOSED -> closed_image = bitmap
            StateCode.DISABLED -> disabled_image = bitmap
            StateCode.UNKNOWN -> unknown_image = bitmap
        }
    }

    // URL to fetch a https certificate from
    // or to send a ssh public key for registration
    open fun getRegisterUrl(): String = ""

    // Parse the text reply from
    // the door and determine state
    abstract fun parseReply(reply: DoorReply): DoorStatus

    // To show/hide button
    abstract fun canOpen(): Boolean
    abstract fun canClose(): Boolean
    abstract fun canRing(): Boolean
}
