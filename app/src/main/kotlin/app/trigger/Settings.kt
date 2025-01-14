package app.trigger

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import app.trigger.https.HttpsTools
import app.trigger.ssh.KeyPairBean
import app.trigger.ssh.SshTools
import org.json.JSONObject
import java.security.cert.Certificate
import java.util.regex.Pattern

object Settings {
    private const val TAG = "Settings"
    private lateinit var sharedPreferences: SharedPreferences
    private var doors = ArrayList<Door>()

    private fun getDatabaseVersion(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // no version set means very old 1.2.1
        return prefs.getString("db_version", null) ?: "1.2.1"
    }

    // update database format
    private fun updateDatabase(context: Context) {
        var db_version = getDatabaseVersion(context)

        // update from 1.2.1 to 1.3.0
        if (db_version == "1.2.1") {
            Log.i("Settings", "Update database format from $db_version to 1.3.1")

            // Recover door from 1.2.0
            val name = sharedPreferences.getString("prefName", "")
            val url = sharedPreferences.getString("prefUrl", "")
            val token = sharedPreferences.getString("prefToken", "")
            val ignore = sharedPreferences.getBoolean("prefIgnore", false)
            if (name!!.isNotEmpty()) {
                val door = HttpsDoor(0, name)
                door.open_query = "$url?action=open&token=$token"
                door.close_query = "$url?action=close&token=$token"
                door.status_query = "$url?action=status&token=$token"
                if (ignore) {
                    door.ignore_hostname_mismatch = true
                }
                addDoor(door)
            }
            // remove old entries
            val e = sharedPreferences.edit()
            e.remove("prefName")
            e.remove("prefUrl")
            e.remove("prefToken")
            e.remove("prefIgnore")
            e.putString("db_version", BuildConfig.VERSION_NAME)
            e.commit()
            doors.clear()
            db_version = "1.3.1"
        }

        // update from 1.3.0/1.3.1 to 1.4.0
        if (db_version == "1.3.0" || db_version == "1.3.1") {
            Log.i("Settings", "Update database format from $db_version to 1.4.0")
            var id = 0
            while (id < 10) {
                val prefix = String.format("item_%03d_", id)
                if (sharedPreferences.contains(prefix + "type")) {
                    val name = sharedPreferences.getString(prefix + "name", "")
                    val url = sharedPreferences.getString(prefix + "url", "")
                    val token = sharedPreferences.getString(prefix + "token", "")
                    val ssids = sharedPreferences.getString(prefix + "ssids", "")
                    val ignore = sharedPreferences.getBoolean(prefix + "ignore", false)
                    if (name != null && name.isNotEmpty()) {
                        val door = HttpsDoor(id, name)
                        door.open_query = "$url?action=open&token=$token"
                        door.close_query = "$url?action=close&token=$token"
                        door.status_query = "$url?action=status&token=$token"
                        door.ssids = ssids!!
                        if (ignore) {
                            door.ignore_hostname_mismatch = true
                        }
                        addDoor(door)
                    } else {
                        removeSetup_pre_172(id)
                    }
                }
                id += 1
            }
            doors.clear()
            db_version = "1.4.0"
        }

        if (db_version == "1.4.0") {
            Log.i("Settings", "Update database format from $db_version to 1.6.0")
            var id = 0
            while (id < 10) {
                val prefix = String.format("item_%03d_", id)
                // change type of entry
                if (sharedPreferences.contains(prefix + "ignore_cert")) {
                    val ignore_cert = sharedPreferences.getBoolean(prefix + "ignore_cert", false)
                    sharedPreferences.edit().putString(prefix + "ignore_cert", ignore_cert.toString()).commit()
                }
                id += 1
            }
            doors.clear()
            sharedPreferences.edit().putString("db_version", "1.6.0").commit()
            db_version = "1.6.0"
        }

        if (db_version == "1.6.0") {
            Log.i("Settings", "Update database format from $db_version to 1.7.0")
            var id = 0
            while (id < 10) {
                val prefix = String.format("item_%03d_", id)
                // change type of entry
                if (sharedPreferences.contains(prefix + "ignore_cert")) {
                    val ignore_cert = sharedPreferences.getString(prefix + "ignore_cert", false.toString())
                    if (ignore_cert != null) {
                        val e = sharedPreferences.edit()
                        e.putString(prefix + "ignore_hostname_mismatch", true.toString())
                        e.remove(prefix + "ignore_cert")
                        e.commit()
                    }
                }
                id += 1
            }
            doors.clear()
            sharedPreferences.edit().putString("db_version", "1.7.0").commit()
            db_version = "1.7.0"
        }

        if (db_version == "1.7.0") {
            Log.i("Settings", "Update database format from $db_version to 1.7.1")
            // nothing to change
            doors.clear()
            sharedPreferences.edit().putString("db_version", "1.7.1").commit()
            db_version = "1.7.1"
        }

        if (db_version == "1.7.1") {
            Log.i("Settings", "Update database format from $db_version to 1.8.0")
            // convert settings from key based scheme to json
            var doors = getAllSetups_pre_172()
            for (door in doors) {
                removeSetup_pre_172(door.id)
                addDoor(door)
            }
            doors.clear()
            sharedPreferences.edit().putString("db_version", "1.8.0").commit()
            db_version = "1.8.0"
        }

        if (db_version == "1.8.0") {
            Log.i("Settings", "Update database format from $db_version to 1.9.0")
            // nothing to change
            doors.clear()
            sharedPreferences.edit().putString("db_version", "1.9.0").commit()
            db_version = "1.9.0"
        }

        if (db_version == "1.9.0") {
            Log.i("Settings", "Update database format from $db_version to 1.9.1")
            // nothing to change
            doors.clear()
            sharedPreferences.edit().putString("db_version", "1.9.1").commit()
            db_version = "1.9.1"
        }

        if (db_version == "1.9.1") {
            Log.i("Settings", "Update database format from $db_version to 1.9.2")
            doors.clear()
            // convert keypair format
            val e = sharedPreferences.edit()
            var id = 0
            while (id < 10) {
                try {
                    val obj = loadSetup(id)
                    if (obj != null && obj.has("keypair")) {
                        val keypair = SshTools.deserializeKeyPair_1_9_1(
                                obj.getString("keypair")
                        )
                        obj.put("keypair", SshTools.serializeKeyPair(keypair))
                        val key = String.format("item_%03d", id)
                        e.putString(key, obj.toString())
                    }
                } catch (ex: Exception) {
                    Log.e("updateDatabase", ex.toString())
                }
                id += 1
            }
            e.commit()
            sharedPreferences.edit().putString("db_version", "1.9.2").commit()
            db_version = "1.9.2"
        }

        // multiple consecutive versions with no database change
        if (db_version in setOf("1.9.2", "2.0.0", "2.0.1", "2.0.2", "2.0.3", "2.0.4", "2.0.5",
                        "2.0.6", "2.1.0", "2.1.1", "2.2.0", "2.2.1", "2.2.2", "2.2.3", "2.2.4",
                        "2.2.5", "3.0.0", "3.0.1", "3.1.0", "3.1.1", "3.1.2", "3.1.3", "3.2.0",
                        "3.2.1")) {
            val new_version = "3.2.2"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            // nothing to change
            doors.clear()
            sharedPreferences.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.2.2")) {
            val new_version = "3.3.0"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            doors.clear()
            // convert keypair format
            val e = sharedPreferences.edit()
            var id = 0
            while (id < 10) {
                try {
                    val obj = loadSetup(id)
                    if (obj != null && obj.has("keypair")) {
                        val keypair = SshTools.deserializeKeyPair_3_2_3(
                                obj.getString("keypair")
                        )
                        obj.put("keypair", SshTools.serializeKeyPair(keypair))
                        val key = String.format("item_%03d", id)
                        e.putString(key, obj.toString())
                    }
                } catch (ex: Exception) {
                    Log.e("updateDatabase", ex.toString())
                }
                id += 1
            }
            e.commit()
            sharedPreferences.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.3.0", "3.3.1", "3.3.2", "3.3.3", "3.3.4", "3.3.5")) {
            val new_version = "3.3.6"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            // nothing to change
            doors.clear()
            sharedPreferences.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.3.6")) {
            val new_version = "3.4.0"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            doors.clear()
            // convert MQTT certificate field name to server_certificate
            var id = 0
            while (id < 10) {
                try {
                    val obj = loadSetup(id)
                    if (obj != null && obj.has("type") && obj["type"] == "MQTTDoorSetup") {
                        if (obj.has("certificate")) {
                            obj.put("server_certificate", obj["certificate"])
                            obj.remove("certificate")
                            storeSetup(id, obj)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to rename MQTT certificate field: $e")
                }
                id += 1
            }
            sharedPreferences.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.4.0")) {
            val new_version = "3.4.1"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            doors.clear()
            // convert HTTPS certificate field name to server_certificate
            var id = 0
            while (id < 10) {
                try {
                    val obj = loadSetup(id)
                    if (obj != null && obj.has("type") && obj["type"] == "HttpsDoorSetup") {
                        if (obj.has("certificate")) {
                            obj.put("server_certificate", obj["certificate"])
                            obj.remove("certificate")
                            storeSetup(id, obj)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to rename HTTPS certificate field: $e")
                }
                id += 1
            }
            sharedPreferences.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.4.1", "3.4.2", "3.4.3", "3.4.4", "4.0.0", "4.0.1", "4.0.2")) {
            val new_version = "4.0.3"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            // nothing to change
            doors.clear()
            var id = 0
            while (id < 10) {
                try {
                    val obj = loadSetup(id)
                    // remove "method" and replace with "open_method",
                    // "close_method", "ring_method" and "status_method"
                    if (obj != null && obj.has("type") && obj["type"] == "HttpsDoorSetup") {
                        if (obj.has("method")) {
                            obj.put("open_method", obj["method"])
                            obj.put("close_method", obj["method"])
                            obj.put("ring_method", obj["method"])
                            obj.put("status_method", obj["method"])
                            obj.remove("method")
                            storeSetup(id, obj)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to rename HTTPS certificate field: $e")
                }
                id += 1
            }
            sharedPreferences.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version == "4.0.3") {
            val new_version = "4.0.4"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            // nothing to change
            doors.clear()
            sharedPreferences.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }
    }

    fun init(context: Context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        doors.clear()

        updateDatabase(context)
        loadSetups()
    }

    fun toJsonObject(door: Door): JSONObject? {
        return when (door) {
            is HttpsDoor -> door.toJSONObject()
            is SshDoor -> door.toJSONObject()
            is MqttDoor -> door.toJSONObject()
            is BluetoothDoor -> door.toJSONObject()
            is NukiDoor -> door.toJSONObject()
            else -> null
        }
    }

    fun fromJsonObject(obj: JSONObject): Door? {
        val type = obj.optString("type", "")

        return when (type) {
            HttpsDoor.TYPE -> HttpsDoor.fromJSONObject(obj)
            SshDoor.TYPE -> SshDoor.fromJSONObject(obj)
            MqttDoor.TYPE -> MqttDoor.fromJSONObject(obj)
            BluetoothDoor.TYPE -> BluetoothDoor.fromJSONObject(obj)
            NukiDoor.TYPE -> NukiDoor.fromJSONObject(obj)
            else -> null
        }
    }

    private fun storeSetup(id: Int, json: JSONObject) {
        val key = String.format("item_%03d", id)
        sharedPreferences.edit().putString(key, json.toString()).commit()
    }

    private fun loadSetup(id: Int): JSONObject? {
        if (id < 0) {
            return null
        }
        val key = String.format("item_%03d", id)
        val json = sharedPreferences.getString(key, null) ?: return null
        return JSONObject(json)
    }

    // add to list and store to preferences
    fun addDoor(door: Door): Boolean {
        if (door.id < 0) {
            return false
        }

        val json = toJsonObject(door) ?: return false

        removeDoor(door.id)

        // make sure a name is set and unique
        // name is unchanged if there is no conflict
        val name = getNewName(door.name)
        json.put("name", name)
        door.name = name

        val key = String.format("item_%03d", door.id)
        sharedPreferences.edit().putString(key, json.toString()).commit()

        // store to persistent memory
        doors.add(door)
        return true
    }

    // remove from list and database
    fun removeDoor(id: Int): Boolean {
        val it: MutableIterator<*> = doors.iterator()
        while (it.hasNext()) {
            val door = it.next() as Door
            if (door.id == id) {
                it.remove()

                // also remove item from storage
                val key = String.format("item_%03d", id)
                sharedPreferences.edit().remove(key).commit()
                break
            }
        }
        return true
    }

    private fun loadSetup_pre_172(id: Int): Door? {
        var door: Door?
        if (id < 0) {
            return null
        }
        run {

            // get type
            val type = sharedPreferences.getString(String.format("item_%03d_type", id), null
            ) ?: return null

            // get empty door object to fill
            door = if (type == HttpsDoor.TYPE) {
                HttpsDoor(id, "")
            } else if (type == SshDoor.TYPE) {
                SshDoor(id, "")
            } else if (type == BluetoothDoor.TYPE) {
                BluetoothDoor(id, "")
            } else if (type == NukiDoor.TYPE) {
                NukiDoor(id, "")
            } else if (type == MqttDoor.TYPE) {
                MqttDoor(id, "")
            } else {
                Log.e(TAG, "Found unknown door type: $type")
                return null
            }
        }
        val fields = door!!.javaClass.declaredFields
        for (field in fields) {
            val name = field.name
            val type = field.type
            val key = String.format("item_%03d_%s", id, name)
            try {
                val value = sharedPreferences.getString(key, "")
                if (name == "type" || name.endsWith("_tmp")) {
                    // ignore, object field is not meant to be stored
                } else if (type == String::class.java) {
                    field[door] = value
                } else if (type == Boolean::class.java) {
                    field[door] = java.lang.Boolean.parseBoolean(value)
                } else if (type == Boolean::class.javaPrimitiveType) {
                    field[door] = java.lang.Boolean.parseBoolean(value)
                } else if (type == Int::class.java) {
                    field[door] = value!!.toInt()
                } else if (type == Long::class.java) {
                    field[door] = value!!.toLong()
                } else if (type == Int::class.javaPrimitiveType) {
                    field[door] = value!!.toInt()
                } else if (type == Long::class.javaPrimitiveType) {
                    field[door] = value!!.toLong()
                } else if (type == KeyPairBean::class.java) {
                    field[door] = SshTools.deserializeKeyPair(value)
                } else if (type == Certificate::class.java) {
                    field[door] = HttpsTools.deserializeCertificate(value)
                } else {
                    Log.e(TAG, "loadSetup(): Unhandled type for $name: $type")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "loadSetup(): $ex")
            }
        }
        return door
    }

    private fun getAllSetups_pre_172(): ArrayList<Door> {
        val doors = ArrayList<Door>()
        val keys = sharedPreferences.all
        val p = Pattern.compile("^item_(\\d{3})_type$")
        for ((key) in keys) {
            val m = p.matcher(key)
            if (!m.find()) {
                continue
            }
            val id = m.group(1)?.toIntOrNull()
            if (id != null) {
                val door = loadSetup_pre_172(id)
                if (door != null) {
                    doors.add(door)
                }
            }
        }
        return doors
    }

    private fun removeSetup_pre_172(id: Int) {
        val prefix = String.format("item_%03d_", id)
        val e = sharedPreferences.edit()

        // remove existing door data
        val keys = sharedPreferences.all
        for ((key) in keys) {
            if (key.startsWith(prefix)) {
                e.remove(key)
            }
        }
        e.commit()
    }

    private fun loadSetups() {
        doors = ArrayList<Door>()
        val keys = sharedPreferences.all
        val p = Pattern.compile("^item_(\\d{3})$")
        for ((key) in keys) {
            val m = p.matcher(key)
            if (!m.find()) {
                continue
            }
            val id = m.group(1)?.toIntOrNull()
            if (id != null) try {
                val obj = loadSetup(id)
                if (obj != null) {
                    val door = fromJsonObject(obj)
                    if (door != null) {
                        doors.add(door)
                    }
                }
            } catch (e: Exception) {
                // ignore broken door
            }
        }
    }

    fun getDoors(): ArrayList<Door> {
        return doors
    }

    // get a unique name
    private fun getNewName(proposed: String): String {
        var name = proposed
        var counter = 1
        while (true) {
            var found = false
            for (door in doors) {
                if (door.name == name) {
                    found = true
                    break
                }
            }
            if (found) {
                name = "$proposed~$counter"
                counter += 1
            } else {
                break
            }
        }
        return name
    }

    fun getDoor(id: Int): Door? {
        for (door in doors) {
            if (door.id == id) {
                return door
            }
        }
        return null
    }

    // get a unique name
    fun getNewDoorName(proposed: String): String {
        var name = proposed
        var counter = 1
        while (true) {
            var found = false
            for (door in doors) {
                if (door.name == name) {
                    found = true
                    break
                }
            }

            if (found) {
                name = "$proposed~$counter"
                counter += 1
            } else {
                break
            }
        }
        return name
    }

    fun isDuplicateName(name: String, ignoreDoor: Door?): Boolean {
        for (door in doors) {
            if (ignoreDoor != null) {
                if (ignoreDoor.id == door.id) {
                    continue
                }
            }
            if (door.name == name) {
                return true
            }
        }
        return false
    }

    fun getNewDoorIdentifier() : Int {
        var id = 0
        while (true) {
            if (!idExists(id)) {
                return id
            }
            id += 1
        }
    }

    private fun idExists(id: Int): Boolean {
        for (door in doors) {
            if (door.id == id) {
                return true
            }
        }
        return false
    }
}
