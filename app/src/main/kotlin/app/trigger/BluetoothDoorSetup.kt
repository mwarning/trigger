package app.trigger

import android.graphics.Bitmap
import app.trigger.DoorState.StateCode
import org.json.JSONObject


class BluetoothDoorSetup(override var id: Int, override var name: String) : Setup {
    override val type = Companion.TYPE
    var device_name = ""
    var service_uuid = ""
    var open_query = ""
    var close_query = ""
    var ring_query = ""
    var locked_pattern = "LOCKED"
    var unlocked_pattern = "UNLOCKED"
    var status_query = ""
    var open_image: Bitmap? = null
    var closed_image: Bitmap? = null
    var unknown_image: Bitmap? = null
    var disabled_image: Bitmap? = null

    override fun getWiFiSSIDs(): String =  ""
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
        return Utils.genericDoorReplyParser(reply, unlocked_pattern, locked_pattern)
    }

    override fun canOpen(): Boolean {
        return !Utils.isEmpty(open_query)
    }

    override fun canClose(): Boolean {
        return !Utils.isEmpty(close_query)
    }

    override fun canRing(): Boolean {
        return !Utils.isEmpty(ring_query)
    }

   fun toJSONObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("type", type)

        obj.put("device_name", device_name)
        obj.put("service_uuid", service_uuid)
        obj.put("open_query", open_query)
        obj.put("close_query", close_query)
        obj.put("ring_query", ring_query)
        obj.put("locked_pattern", locked_pattern)
        obj.put("unlocked_pattern", unlocked_pattern)
        obj.put("status_query", status_query)

        obj.put("open_image", Utils.serializeBitmap(open_image))
        obj.put("closed_image", Utils.serializeBitmap(closed_image))
        obj.put("unknown_image", Utils.serializeBitmap(unknown_image))
        obj.put("disabled_image", Utils.serializeBitmap(disabled_image))

        return obj
    }

    companion object {
        const val TYPE = "BluetoothDoorSetup"

        fun fromJSONObject(obj: JSONObject): BluetoothDoorSetup {
            val id = obj.getInt("id")
            val name = obj.getString("name")
            val setup = BluetoothDoorSetup(id, name)

            setup.device_name = obj.optString("device_name", "")
            setup.service_uuid = obj.optString("service_uuid", "")
            setup.open_query = obj.optString("open_query", "")
            setup.close_query = obj.optString("close_query", "")
            setup.ring_query = obj.optString("ring_query", "")
            setup.locked_pattern = obj.optString("locked_pattern", "LOCKED")
            setup.unlocked_pattern = obj.optString("unlocked_pattern", "UNLOCKED")
            setup.status_query = obj.optString("status_query", "")

            setup.open_image = Utils.deserializeBitmap(obj.optString("open_image", ""))
            setup.closed_image = Utils.deserializeBitmap(obj.optString("closed_image", ""))
            setup.unknown_image = Utils.deserializeBitmap(obj.optString("unknown_image", ""))
            setup.disabled_image = Utils.deserializeBitmap(obj.optString("disabled_image", ""))

            return setup
        }
    }
}
