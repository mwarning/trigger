package app.trigger

import android.graphics.Bitmap
import app.trigger.DoorState.StateCode
import app.trigger.DoorReply.ReplyCode
import android.text.Html
import org.json.JSONObject


class NukiDoorSetup(override var id: Int, override var name: String) : Setup {
    override val type = Companion.TYPE
    var device_name = ""
    var user_name = "user"
    var shared_key = ""
    var auth_id: Long = 0
    var app_id: Long = 2342
    var open_image: Bitmap? = null
    var closed_image: Bitmap? = null
    var unknown_image: Bitmap? = null
    var disabled_image: Bitmap? = null

    override fun getWiFiSSIDs(): String  = ""
    override fun getWiFiRequired(): Boolean = false

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
        val msg = Html.fromHtml(reply.message).toString().trim { it <= ' ' }
        return when (reply.code) {
            ReplyCode.LOCAL_ERROR, ReplyCode.REMOTE_ERROR -> DoorState(StateCode.UNKNOWN, msg)
            ReplyCode.SUCCESS -> if (reply.message.contains("unlocked")) {
                // door unlocked
                DoorState(StateCode.OPEN, msg)
            } else if (reply.message.contains("locked")) {
                // door locked
                DoorState(StateCode.CLOSED, msg)
            } else {
                DoorState(StateCode.UNKNOWN, msg)
            }
            ReplyCode.DISABLED -> DoorState(StateCode.DISABLED, msg)
        }
    }

    override fun canOpen(): Boolean {
        return true
    }

    override fun canClose(): Boolean {
        return true
    }

    override fun canRing(): Boolean {
        return false
    }

    fun toJSONObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("type", type)
        obj.put("device_name", device_name)
        obj.put("user_name", user_name)
        obj.put("shared_key", shared_key)
        obj.put("auth_id", auth_id)
        obj.put("app_id", app_id)

        obj.put("open_image", Utils.serializeBitmap(open_image))
        obj.put("closed_image", Utils.serializeBitmap(closed_image))
        obj.put("unknown_image", Utils.serializeBitmap(unknown_image))
        obj.put("disabled_image", Utils.serializeBitmap(disabled_image))

        return obj
    }

    companion object {
        const val TYPE = "NukiDoorSetup"

        fun fromJSONObject(obj: JSONObject): NukiDoorSetup {
            val id = obj.getInt("id")
            val name = obj.getString("name")
            val setup = NukiDoorSetup(id, name)

            setup.device_name = obj.optString("device_name", "")
            setup.user_name = obj.optString("user_name", "")
            setup.shared_key = obj.optString("shared_key", "")
            setup.auth_id = obj.optLong("auth_id", 0)
            setup.app_id = obj.optLong("app_id", 2342)

            setup.open_image = Utils.deserializeBitmap(obj.optString("open_image", ""))
            setup.closed_image = Utils.deserializeBitmap(obj.optString("closed_image", ""))
            setup.unknown_image = Utils.deserializeBitmap(obj.optString("unknown_image", ""))
            setup.disabled_image = Utils.deserializeBitmap(obj.optString("disabled_image", ""))

            return setup
        }
    }
}
