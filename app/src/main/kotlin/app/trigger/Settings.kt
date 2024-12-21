package app.trigger

import org.json.JSONObject
import android.content.*
import app.trigger.ssh.KeyPairBean
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import app.trigger.ssh.SshTools
import app.trigger.https.HttpsTools
import java.lang.Exception
import java.security.cert.Certificate
import java.util.*
import java.util.regex.Pattern


object Settings {
    private const val TAG = "Settings"
    private var sharedPreferences: SharedPreferences? = null
    var setups = ArrayList<Setup>()
    var app_version : String? = null // stored in program
    private var db_version : String? = null // stored in database

    private fun getDatabaseVersion(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // no version set means very old 1.2.1
        return prefs.getString("db_version", null) ?: "1.2.1"
    }

    private fun getApplicationVersion(context: Context): String {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return info.versionName!!
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return ""
    }

    // update database format
    private fun upgradeDB() {
        // update from 1.2.1 to 1.3.0
        if (db_version == "1.2.1") {
            Log.i("Settings", "Update database format from $db_version to 1.3.1")

            // Recover setup from 1.2.0
            val name = sharedPreferences!!.getString("prefName", "")
            val url = sharedPreferences!!.getString("prefUrl", "")
            val token = sharedPreferences!!.getString("prefToken", "")
            val ignore = sharedPreferences!!.getBoolean("prefIgnore", false)
            if (name!!.isNotEmpty()) {
                val setup = HttpsDoorSetup(0, name)
                setup.open_query = "$url?action=open&token=$token"
                setup.close_query = "$url?action=close&token=$token"
                setup.status_query = "$url?action=status&token=$token"
                if (ignore) {
                    setup.ignore_hostname_mismatch = true
                }
                addSetup(setup)
            }
            // remove old entries
            val e = sharedPreferences!!.edit()
            e.remove("prefName")
            e.remove("prefUrl")
            e.remove("prefToken")
            e.remove("prefIgnore")
            e.putString("db_version", app_version)
            e.commit()
            setups.clear()
            db_version = "1.3.1"
        }

        // update from 1.3.0/1.3.1 to 1.4.0
        if (db_version == "1.3.0" || db_version == "1.3.1") {
            Log.i("Settings", "Update database format from $db_version to 1.4.0")
            var id = 0
            while (id < 10) {
                val prefix = String.format("item_%03d_", id)
                if (sharedPreferences!!.contains(prefix + "type")) {
                    val name = sharedPreferences!!.getString(prefix + "name", "")
                    val url = sharedPreferences!!.getString(prefix + "url", "")
                    val token = sharedPreferences!!.getString(prefix + "token", "")
                    val ssids = sharedPreferences!!.getString(prefix + "ssids", "")
                    val ignore = sharedPreferences!!.getBoolean(prefix + "ignore", false)
                    if (!name.isNullOrEmpty()) {
                        val setup = HttpsDoorSetup(id, name)
                        setup.open_query = "$url?action=open&token=$token"
                        setup.close_query = "$url?action=close&token=$token"
                        setup.status_query = "$url?action=status&token=$token"
                        setup.ssids = ssids!!
                        if (ignore) {
                            setup.ignore_hostname_mismatch = true
                        }
                        addSetup(setup)
                    } else {
                        removeSetup_pre_172(id)
                    }
                }
                id += 1
            }
            setups.clear()
            db_version = "1.4.0"
        }

        if (db_version == "1.4.0") {
            Log.i("Settings", "Update database format from $db_version to 1.6.0")
            var id = 0
            while (id < 10) {
                val prefix = String.format("item_%03d_", id)
                // change type of entry
                if (sharedPreferences!!.contains(prefix + "ignore_cert")) {
                    val ignore_cert = sharedPreferences!!.getBoolean(prefix + "ignore_cert", false)
                    sharedPreferences!!.edit().putString(prefix + "ignore_cert", ignore_cert.toString()).commit()
                }
                id += 1
            }
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", "1.6.0").commit()
            db_version = "1.6.0"
        }

        if (db_version == "1.6.0") {
            Log.i("Settings", "Update database format from $db_version to 1.7.0")
            var id = 0
            while (id < 10) {
                val prefix = String.format("item_%03d_", id)
                // change type of entry
                if (sharedPreferences!!.contains(prefix + "ignore_cert")) {
                    val ignore_cert = sharedPreferences!!.getString(prefix + "ignore_cert", false.toString())
                    if (ignore_cert != null) {
                        val e = sharedPreferences!!.edit()
                        e.putString(prefix + "ignore_hostname_mismatch", true.toString())
                        e.remove(prefix + "ignore_cert")
                        e.commit()
                    }
                }
                id += 1
            }
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", "1.7.0").commit()
            db_version = "1.7.0"
        }

        if (db_version == "1.7.0") {
            Log.i("Settings", "Update database format from $db_version to 1.7.1")
            // nothing to change
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", "1.7.1").commit()
            db_version = "1.7.1"
        }

        if (db_version == "1.7.1") {
            Log.i("Settings", "Update database format from $db_version to 1.8.0")
            // convert settings from key based scheme to json
            var setups = getAllSetups_pre_172()
            for (setup in setups) {
                removeSetup_pre_172(setup.id)
                addSetup(setup)
            }
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", "1.8.0").commit()
            db_version = "1.8.0"
        }

        if (db_version == "1.8.0") {
            Log.i("Settings", "Update database format from $db_version to 1.9.0")
            // nothing to change
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", "1.9.0").commit()
            db_version = "1.9.0"
        }

        if (db_version == "1.9.0") {
            Log.i("Settings", "Update database format from $db_version to 1.9.1")
            // nothing to change
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", "1.9.1").commit()
            db_version = "1.9.1"
        }

        if (db_version == "1.9.1") {
            Log.i("Settings", "Update database format from $db_version to 1.9.2")
            setups.clear()
            // convert keypair format
            val e = sharedPreferences!!.edit()
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
                    Log.e("upgradeDB", ex.toString())
                }
                id += 1
            }
            e.commit()
            sharedPreferences!!.edit().putString("db_version", "1.9.2").commit()
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
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.2.2")) {
            val new_version = "3.3.0"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            setups.clear()
            // convert keypair format
            val e = sharedPreferences!!.edit()
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
                    Log.e("upgradeDB", ex.toString())
                }
                id += 1
            }
            e.commit()
            sharedPreferences!!.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.3.0", "3.3.1", "3.3.2", "3.3.3", "3.3.4", "3.3.5")) {
            val new_version = "3.3.6"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            // nothing to change
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.3.6")) {
            val new_version = "3.4.0"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            setups.clear()
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
            sharedPreferences!!.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.4.0")) {
            val new_version = "3.4.1"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            setups.clear()
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
            sharedPreferences!!.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.4.1", "3.4.2", "3.4.3")) {
            val new_version = "3.4.4"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            // nothing to change
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }

        if (db_version in setOf("3.4.4", "4.0.0")) {
            val new_version = "4.0.1"
            Log.i(TAG, "Update database format from $db_version to $new_version")
            // nothing to change
            setups.clear()
            sharedPreferences!!.edit().putString("db_version", new_version).commit()
            db_version = new_version
        }
    }

    fun init(context: Context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        app_version = getApplicationVersion(context)
        db_version = getDatabaseVersion(context)
        setups.clear()

        //update database layout if necessary
        upgradeDB()
        loadSetups()
    }

    fun toJsonObject(setup: Setup): JSONObject? {
        if (setup is HttpsDoorSetup) {
            return setup.toJSONObject()
        } else if (setup is SshDoorSetup) {
            return setup.toJSONObject()
        } else if (setup is MqttDoorSetup) {
            return setup.toJSONObject()
        } else if (setup is BluetoothDoorSetup) {
            return setup.toJSONObject()
        } else if (setup is NukiDoorSetup) {
            return setup.toJSONObject()
        } else {
            return null
        }
    }

    fun fromJsonObject(obj: JSONObject): Setup? {
        val type = obj.optString("type", "")

        if (type == HttpsDoorSetup.type) {
            return HttpsDoorSetup.fromJSONObject(obj)
        } else if (type == SshDoorSetup.type) {
            return SshDoorSetup.fromJSONObject(obj)
        } else if (type == MqttDoorSetup.type) {
            return MqttDoorSetup.fromJSONObject(obj)
        } else if (type == BluetoothDoorSetup.type) {
            return BluetoothDoorSetup.fromJSONObject(obj)
        } else if (type == NukiDoorSetup.type) {
            return NukiDoorSetup.fromJSONObject(obj)
        } else {
            return null
        }
    }

    fun getSetup(id: Int): Setup? {
        for (setup in setups) {
            if (setup.id == id) {
                return setup
            }
        }
        return null
    }

    private fun storeSetup(id: Int, json: JSONObject) {
        val key = String.format("item_%03d", id)
        sharedPreferences!!.edit().putString(key, json.toString()).commit()
    }

    private fun loadSetup(id: Int): JSONObject? {
        if (id < 0) {
            return null
        }
        val key = String.format("item_%03d", id)
        val json = sharedPreferences!!.getString(key, null) ?: return null
        return JSONObject(json)
    }

    // add to list and database
    fun addSetup(setup: Setup?) {
        if (setup == null || setup.id < 0) {
            return
        }

        val json = toJsonObject(setup) ?: return

        removeSetup(setup.id)

        // make sure a name is set and unique
        val name = getNewName(setup.name)
        json.put("name", name)
        setup.name = name

        val key = String.format("item_%03d", setup.id)
        sharedPreferences!!.edit().putString(key, json.toString()).commit()

        // store to persistent memory
        setups.add(setup)
    }

    // remove from list and database
    fun removeSetup(id: Int) {
        val it: MutableIterator<*> = setups.iterator()
        while (it.hasNext()) {
            val setup = it.next() as Setup
            if (setup.id == id) {
                it.remove()

                // also remove item from storage
                val key = String.format("item_%03d", id)
                sharedPreferences!!.edit().remove(key).commit()
                break
            }
        }
    }

    private fun loadSetup_pre_172(id: Int): Setup? {
        var setup: Setup? = null
        if (id < 0) {
            return null
        }
        run {

            // get type
            val type = sharedPreferences!!.getString(String.format("item_%03d_type", id), null
            ) ?: return null

            // get empty setup object to fill
            setup = if (type == HttpsDoorSetup.type) {
                HttpsDoorSetup(id, "")
            } else if (type == SshDoorSetup.type) {
                SshDoorSetup(id, "")
            } else if (type == BluetoothDoorSetup.type) {
                BluetoothDoorSetup(id, "")
            } else if (type == NukiDoorSetup.type) {
                NukiDoorSetup(id, "")
            } else if (type == MqttDoorSetup.type) {
                MqttDoorSetup(id, "")
            } else {
                Log.e(TAG, "Found unknown setup type: $type")
                return null
            }
        }
        val fields = setup!!.javaClass.declaredFields
        for (field in fields) {
            val name = field.name
            val type = field.type
            val key = String.format("item_%03d_%s", id, name)
            try {
                val value = sharedPreferences!!.getString(key, "")
                if (name == "type" || name.endsWith("_tmp")) {
                    // ignore, object field is not meant to be stored
                } else if (type == String::class.java) {
                    field[setup] = value
                } else if (type == Boolean::class.java) {
                    field[setup] = java.lang.Boolean.parseBoolean(value)
                } else if (type == Boolean::class.javaPrimitiveType) {
                    field[setup] = java.lang.Boolean.parseBoolean(value)
                } else if (type == Int::class.java) {
                    field[setup] = value!!.toInt()
                } else if (type == Long::class.java) {
                    field[setup] = value!!.toLong()
                } else if (type == Int::class.javaPrimitiveType) {
                    field[setup] = value!!.toInt()
                } else if (type == Long::class.javaPrimitiveType) {
                    field[setup] = value!!.toLong()
                } else if (type == KeyPairBean::class.java) {
                    field[setup] = SshTools.deserializeKeyPair(value)
                } else if (type == Certificate::class.java) {
                    field[setup] = HttpsTools.deserializeCertificate(value)
                } else {
                    Log.e(TAG, "loadSetup(): Unhandled type for $name: $type")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "loadSetup(): $ex")
            }
        }
        return setup
    }

    private fun getAllSetups_pre_172(): ArrayList<Setup> {
        val setups = ArrayList<Setup>()
        val keys = sharedPreferences!!.all
        val p = Pattern.compile("^item_(\\d{3})_type$")
        for ((key) in keys) {
            val m = p.matcher(key)
            if (!m.find()) {
                continue
            }
            val id = m.group(1).toInt()
            val setup = loadSetup_pre_172(id)
            if (setup != null) {
                setups.add(setup)
            }
        }
        return setups
    }

    private fun removeSetup_pre_172(id: Int) {
        val prefix = String.format("item_%03d_", id)
        val e = sharedPreferences!!.edit()

        // remove existing setup data
        val keys = sharedPreferences!!.all
        for ((key) in keys) {
            if (key.startsWith(prefix)) {
                e.remove(key)
            }
        }
        e.commit()
    }

    private fun loadSetups() {
        setups = ArrayList<Setup>()
        val keys = sharedPreferences!!.all
        val p = Pattern.compile("^item_(\\d{3})$")
        for ((key) in keys) {
            val m = p.matcher(key)
            if (!m.find()) {
                continue
            }
            val id = m.group(1).toInt()
            try {
                val obj = loadSetup(id)
                if (obj != null) {
                    val setup = fromJsonObject(obj)
                    if (setup != null) {
                        setups.add(setup)
                    }
                }
            } catch (e: Exception) {
                // ignore broken setup
            }
        }
    }

    // get a unique name
    fun getNewName(proposed: String): String {
        var name = proposed
        var counter = 1
        while (true) {
            var found = false
            for (setup in setups) {
                if (setup.name == name) {
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

    fun getNewID() : Int {
        var id = 0
        while (true) {
            if (!idExists(id)) {
                return id
            }
            id += 1
        }
    }

    fun idExists(id: Int): Boolean {
        for (setup in setups) {
            if (setup.id == id) {
                return true
            }
        }
        return false
    }

    fun countNames(name: String): Int {
        var count = 0
        for (setup in setups) {
            if (setup.name == name) {
                count += 1
            }
        }
        return count
    }

    // for debugging
    fun printAll(pref: SharedPreferences) {
        val keys = pref.all
        for ((key, value) in keys) {
            Log.d(TAG, "printAll(): " + key + ": " + value.toString())
        }
    }
}
