package app.trigger

import app.trigger.ssh.KeyPairBean
import app.trigger.ssh.SshTools
import org.json.JSONObject


class SshDoor(override var id: Int, override var name: String) : Door() {
    override val type = Companion.TYPE

    var require_wifi = false
    var keypair: KeyPairBean? = null
    var user = ""
    var password = ""
    var host = ""
    var port = 22
    var open_command = ""
    var close_command = ""
    var ring_command = ""
    var state_command = ""

    // regex to evaluate the door return message
    var unlocked_pattern = ""
    var locked_pattern = ""

    var register_url = ""
    var ssids = ""
    var timeout = 5000 // milliseconds
    var passphrase_tmp = ""

    override fun getWiFiRequired(): Boolean = require_wifi
    override fun getWiFiSSIDs(): String = ssids

    override fun getRegisterUrl(): String {
        return register_url.ifEmpty { host }
    }

    override fun parseReply(reply: DoorReply): DoorStatus {
        return Utils.genericDoorReplyParser(reply, unlocked_pattern, locked_pattern)
    }

    override fun canOpen(): Boolean {
        return open_command.isNotEmpty()
    }

    override fun canClose(): Boolean {
        return close_command.isNotEmpty()
    }

    override fun canRing(): Boolean {
        return ring_command.isNotEmpty()
    }

    override fun canFetchState(): Boolean {
        return state_command.isNotEmpty()
    }

    fun needsPassphrase(): Boolean {
        return keypair != null && keypair!!.encrypted
    }

    fun toJSONObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("type", type)

        obj.put("require_wifi", require_wifi)
        obj.put("keypair", SshTools.serializeKeyPair(keypair))
        obj.put("user", user)
        obj.put("password", password)
        obj.put("host", host)
        obj.put("port", port)
        obj.put("open_command", open_command)
        obj.put("close_command", close_command)
        obj.put("ring_command", ring_command)
        obj.put("state_command", state_command)

        obj.put("unlocked_pattern", unlocked_pattern)
        obj.put("locked_pattern", locked_pattern)
        obj.put("open_image", Utils.serializeBitmap(open_image))
        obj.put("closed_image", Utils.serializeBitmap(closed_image))
        obj.put("unknown_image", Utils.serializeBitmap(unknown_image))
        obj.put("disabled_image", Utils.serializeBitmap(disabled_image))

        obj.put("register_url", register_url)
        obj.put("ssids", ssids)
        obj.put("timeout", timeout)

        return obj
    }

    companion object {
        const val TYPE = "SshDoorSetup"

        fun fromJSONObject(obj: JSONObject): SshDoor {
            val id = obj.getInt("id")
            val name = obj.getString("name")
            val setup = SshDoor(id, name)

            setup.require_wifi = obj.optBoolean("require_wifi", false)
            setup.keypair = SshTools.deserializeKeyPair(obj.optString("keypair", ""))
            setup.user = obj.optString("user", "")
            setup.password = obj.optString("password", "")
            setup.host = obj.optString("host", "")
            setup.port = obj.optInt("port", 22)
            setup.open_command = obj.optString("open_command", "")
            setup.close_command = obj.optString("close_command", "")
            setup.ring_command = obj.optString("ring_command", "")
            setup.state_command = obj.optString("state_command", "")

            setup.unlocked_pattern = obj.optString("unlocked_pattern", "")
            setup.locked_pattern = obj.optString("locked_pattern", "")

            setup.open_image = Utils.deserializeBitmap(obj.optString("open_image", ""))
            setup.closed_image = Utils.deserializeBitmap(obj.optString("closed_image", ""))
            setup.unknown_image = Utils.deserializeBitmap(obj.optString("unknown_image", ""))
            setup.disabled_image = Utils.deserializeBitmap(obj.optString("disabled_image", ""))

            setup.register_url = obj.optString("register_url", "")
            setup.ssids = obj.optString("ssids", "")
            setup.timeout = obj.optInt("timeout", 5000)

            return setup
        }
    }
}
