package app.trigger

import android.graphics.Bitmap
import app.trigger.DoorState.StateCode


interface Setup {
    // internal id
    var id: Int

    // Name of this setup for dropdown menu
    var name: String

    // Door mechanism type name
    val type: String

    // Select setup entry from dropdown if it
    // matches any of these SSIDs (comma separated)
    fun getWiFiSSIDs(): String

    // only applies for HTTPS, SSH and MQTT so far
    fun getWiFiRequired(): Boolean

    // Get image dependent of the door state
    fun getStateImage(state: StateCode?): Bitmap?

    // URL to fetch a https certificate from
    // or to send a ssh public key for registration
    fun getRegisterUrl(): String = ""

    // Parse the text reply from
    // the door and determine state
    fun parseReply(reply: DoorReply): DoorState

    // To show/hide button
    fun canOpen(): Boolean
    fun canClose(): Boolean
    fun canRing(): Boolean
}
