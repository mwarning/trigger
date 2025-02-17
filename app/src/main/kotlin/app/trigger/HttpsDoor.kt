package app.trigger

import app.trigger.https.HttpsTools
import app.trigger.ssh.KeyPairBean
import app.trigger.ssh.SshTools
import org.json.JSONObject
import java.security.cert.Certificate

class HttpsDoor(override var id: Int, override var name: String) : Door() {
    override val type = Companion.TYPE

    var require_wifi = false
    var open_query = ""
    var open_method = "GET"
    var close_query = ""
    var close_method = "GET"
    var ring_query = ""
    var ring_method = "GET"
    var status_query = ""
    var status_method = "GET"
    var ssids = ""

    // regex to evaluate the door return message
    var unlocked_pattern = ""
    var locked_pattern = ""

    var server_certificate: Certificate? = null
    var client_certificate: Certificate? = null
    var client_keypair: KeyPairBean? = null
    var ignore_certificate = false
    var ignore_hostname_mismatch = false
    var ignore_expiration = false

    override fun getWiFiRequired(): Boolean = require_wifi
    override fun getWiFiSSIDs(): String = ssids

    // extract from known urls
    override fun getRegisterUrl(): String {
        return stripUrls(open_query, ring_query, close_query, status_query)
    }

    override fun parseReply(reply: DoorReply): DoorStatus {
        return Utils.genericDoorReplyParser(reply, unlocked_pattern, locked_pattern)
    }

    override fun isActionSupported(action: MainActivity.Action): Boolean {
        return when (action) {
            MainActivity.Action.OPEN_DOOR -> open_query.isNotEmpty()
            MainActivity.Action.CLOSE_DOOR -> close_query.isNotEmpty()
            MainActivity.Action.RING_DOOR -> ring_query.isNotEmpty()
            MainActivity.Action.FETCH_STATE -> status_query.isNotEmpty()
        }
    }

    fun toJSONObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("type", type)
        obj.put("require_wifi", require_wifi)
        obj.put("open_query", open_query)
        obj.put("open_method", open_method)
        obj.put("close_query", close_query)
        obj.put("close_method", close_method)
        obj.put("ring_query", ring_query)
        obj.put("ring_method", ring_method)
        obj.put("status_query", status_query)
        obj.put("status_method", status_method)
        obj.put("ssids", ssids)
        obj.put("unlocked_pattern", unlocked_pattern)
        obj.put("locked_pattern", locked_pattern)

        obj.put("open_image", Utils.serializeBitmap(open_image))
        obj.put("closed_image", Utils.serializeBitmap(closed_image))
        obj.put("unknown_image", Utils.serializeBitmap(unknown_image))
        obj.put("disabled_image", Utils.serializeBitmap(disabled_image))

        obj.put("server_certificate", HttpsTools.serializeCertificate(server_certificate))
        obj.put("client_certificate", HttpsTools.serializeCertificate(client_certificate))
        obj.put("client_keypair", SshTools.serializeKeyPair(client_keypair))

        obj.put("ignore_certificate", ignore_certificate)
        obj.put("ignore_hostname_mismatch", ignore_hostname_mismatch)
        obj.put("ignore_expiration", ignore_expiration)

        return obj
    }

    companion object {
        const val TYPE = "HttpsDoorSetup"

        fun fromJSONObject(obj: JSONObject): HttpsDoor {
            val id = obj.getInt("id")
            val name = obj.getString("name")
            val setup = HttpsDoor(id, name)

            val defaultMethod = obj.optString("method", "GET")

            setup.require_wifi = obj.optBoolean("require_wifi", false)
            setup.open_query = obj.optString("open_query", "")
            setup.open_method = obj.optString("open_method", defaultMethod)
            setup.close_query = obj.optString("close_query", "")
            setup.close_method = obj.optString("close_method", defaultMethod)
            setup.ring_query = obj.optString("ring_query", "")
            setup.ring_method = obj.optString("ring_method", defaultMethod)
            setup.status_query = obj.optString("status_query", "")
            setup.status_method = obj.optString("status_method", defaultMethod)
            setup.ssids = obj.optString("ssids", "")
            setup.unlocked_pattern = obj.optString("unlocked_pattern", "")
            setup.locked_pattern = obj.optString("locked_pattern", "")
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

        private fun stripUrls(vararg urls: String): String {
            // remove path
            val prefix = "https://"
            for (url in urls) {
                if (url.startsWith(prefix)) {
                    val i = url.indexOf('/', prefix.length)
                    if (i > 0) {
                        return url.substring(0, i)
                    }
                }
                return url
            }
            return ""
        }
    }
}
