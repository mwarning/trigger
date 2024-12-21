package app.trigger

import android.graphics.Bitmap
import app.trigger.DoorState.StateCode
import app.trigger.https.HttpsTools
import app.trigger.ssh.KeyPairBean
import app.trigger.ssh.SshTools
import org.json.JSONObject
import java.security.cert.Certificate


class MqttDoorSetup(override var id: Int, override var name: String) : Setup {
    override val type = Companion.TYPE
    var require_wifi = false
    var username = ""
    var password = ""
    var server = ""
    var status_topic = ""
    var command_topic = ""
    var retained = false
    var qos = 0
    var open_command = ""
    var close_command = ""
    var ring_command = ""
    var ssids = ""
    var locked_pattern = "LOCKED"
    var unlocked_pattern = "UNLOCKED"
    var open_image: Bitmap? = null
    var closed_image: Bitmap? = null
    var unknown_image: Bitmap? = null
    var disabled_image: Bitmap? = null

    var server_certificate: Certificate? = null
    var client_certificate: Certificate? = null
    var client_keypair: KeyPairBean? = null
    var ignore_certificate = false
    var ignore_hostname_mismatch = false
    var ignore_expiration = false

    override fun getWiFiRequired(): Boolean = require_wifi
    override fun getWiFiSSIDs(): String = ssids
    override fun getStateImage(state: StateCode?): Bitmap? {
        return when (state) {
            StateCode.OPEN -> open_image
            StateCode.CLOSED -> closed_image
            StateCode.DISABLED -> disabled_image
            StateCode.UNKNOWN -> unknown_image
            else -> null
        }
    }

    override fun parseReply(reply: DoorReply): DoorState {
        return Utils.genericDoorReplyParser(reply, unlocked_pattern, locked_pattern)
    }

    override fun canOpen(): Boolean {
        return !Utils.isEmpty(open_command)
    }

    override fun canClose(): Boolean {
        return !Utils.isEmpty(close_command)
    }

    override fun canRing(): Boolean {
        return !Utils.isEmpty(ring_command)
    }

    fun toJSONObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("type", type)

        obj.put("require_wifi", require_wifi)
        obj.put("username", username)
        obj.put("password", password)
        obj.put("server", server)
        obj.put("status_topic", status_topic)
        obj.put("command_topic", command_topic)
        obj.put("retained", retained)
        obj.put("qos", qos)

        obj.put("open_command", open_command)
        obj.put("close_command", close_command)
        obj.put("ring_command", ring_command)
        obj.put("ssids", ssids)

        obj.put("unlocked_pattern", unlocked_pattern)
        obj.put("locked_pattern", locked_pattern)
        obj.put("open_image", Utils.serializeBitmap(open_image))
        obj.put("closed_image", Utils.serializeBitmap(closed_image))
        obj.put("unknown_image", Utils.serializeBitmap(unknown_image))
        obj.put("disabled_image", Utils.serializeBitmap(disabled_image))

        return obj
    }

    companion object {
        const val TYPE = "MqttDoorSetup"

        fun fromJSONObject(obj: JSONObject): MqttDoorSetup {
            val id = obj.getInt("id")
            val name = obj.getString("name")
            val setup = MqttDoorSetup(id, name)

            setup.require_wifi = obj.optBoolean("require_wifi", false)
            setup.username = obj.optString("username", "")
            setup.password = obj.optString("password", "")
            setup.server = obj.optString("server", "")
            setup.status_topic = obj.optString("status_topic", "")
            setup.command_topic = obj.optString("command_topic", "")
            setup.retained = obj.optBoolean("retained", false)
            setup.qos = obj.optInt("qos", 0)
            setup.open_command = obj.optString("open_command", "")
            setup.close_command = obj.optString("close_command", "")
            setup.ring_command = obj.optString("ring_command", "")
            setup.ssids = obj.optString("ssids", "")

            setup.unlocked_pattern = obj.optString("unlocked_pattern", "UNLOCKED")
            setup.locked_pattern = obj.optString("locked_pattern", "LOCKED")

            setup.open_image = Utils.deserializeBitmap(obj.optString("open_image", ""))
            setup.closed_image = Utils.deserializeBitmap(obj.optString("closed_image", ""))
            setup.unknown_image = Utils.deserializeBitmap(obj.optString("unknown_image", ""))
            setup.disabled_image = Utils.deserializeBitmap(obj.optString("disabled_image", ""))

            setup.server_certificate = HttpsTools.deserializeCertificate(obj.optString("server_certificate", ""))
            setup.client_certificate = HttpsTools.deserializeCertificate(obj.optString("client_certificate", ""))
            setup.client_keypair = SshTools.deserializeKeyPair(obj.optString("client_keypair", ""))

            setup.ignore_certificate = obj.optBoolean("ignore_certificate", false)
            setup.ignore_hostname_mismatch = obj.optBoolean("ignore_hostname_mismatch", false)
            setup.ignore_expiration = obj.optBoolean("ignore_expiration", false)

            return setup
        }
    }
}
