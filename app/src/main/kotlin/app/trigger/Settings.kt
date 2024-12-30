package app.trigger

import org.json.JSONObject
import android.content.*
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.*


object Settings {
    private const val TAG = "Settings"
    //private var sharedPreferences: SharedPreferences? = null
    var doors = ArrayList<Door>()
    //private var app_version : String? = null // stored in program
    private var db_version : String? = null // stored in database

    // read file from internal storage
    private fun readInternalFile(filePath: String): ByteArray {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw IOException("File does not exist: $filePath")
        }
        val fis = FileInputStream(file)
        var nRead: Int
        val dataArray = ByteArray(16384)
        val buffer = ByteArrayOutputStream()
        while (fis.read(dataArray, 0, dataArray.size).also { nRead = it } != -1) {
            buffer.write(dataArray, 0, nRead)
        }
        fis.close()
        return buffer.toByteArray()
    }

    // write file to external storage
    private fun writeInternalFile(filePath: String, dataArray: ByteArray) {
        val file = File(filePath)
        if (file.exists() && file.isFile) {
            if (!file.delete()) {
                throw IOException("Failed to delete existing file: $filePath")
            }
        }
        file.createNewFile()
        val fos = FileOutputStream(file)
        fos.write(dataArray)
        fos.close()
    }

    fun saveDatabase(context: Context): Boolean {
        try {
            val obj = JSONObject()
            obj.put("version", BuildConfig.VERSION_NAME)

            val array = JSONArray()
            for (door in doors) {
                val door_obj = toJsonObject(door)
                if (door_obj != null) {
                    array.put(door_obj)
                }
            }
            obj.put("doors", array)

            val databasePath = context.filesDir.toString() + "/database.bin"
            writeInternalFile(databasePath, obj.toString().toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    // load database
    fun init(context: Context) {
        val databasePath = context.filesDir.toString() + "/database.bin"
        if (File(databasePath).exists()) {
            // open existing database
            val stringData = readInternalFile(databasePath)

            val obj = JSONObject(
                String(stringData, Charset.forName("UTF-8"))
            )

            val db_version = obj.getString("version")

            if (db_version != BuildConfig.VERSION_NAME) {
                Log.w(TAG, "database version (${db_version}) != app version (${BuildConfig.VERSION_NAME})")
                // TODO
                //upgradeDB(db_version, db)
            }

            val array = obj.getJSONArray("doors")
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val door = fromJsonObject(item)
                if (door != null) {
                    doors.add(door)
                }
            }
        }
    }

    fun toJsonObject(door: Door): JSONObject? {
        return if (door is HttpsDoor) {
            door.toJSONObject()
        } else if (door is SshDoor) {
            door.toJSONObject()
        } else if (door is MqttDoor) {
            door.toJSONObject()
        } else if (door is BluetoothDoor) {
            door.toJSONObject()
        } else if (door is NukiDoor) {
            door.toJSONObject()
        } else {
            null
        }
    }

    fun fromJsonObject(obj: JSONObject): Door? {
        val type = obj.optString("type", "")

        return if (type == HttpsDoor.TYPE) {
            HttpsDoor.fromJSONObject(obj)
        } else if (type == SshDoor.TYPE) {
            SshDoor.fromJSONObject(obj)
        } else if (type == MqttDoor.TYPE) {
            MqttDoor.fromJSONObject(obj)
        } else if (type == BluetoothDoor.TYPE) {
            BluetoothDoor.fromJSONObject(obj)
        } else if (type == NukiDoor.TYPE) {
            NukiDoor.fromJSONObject(obj)
        } else {
            null
        }
    }

    fun removeDoor(id: Int) {
        doors.removeAll { setup -> setup.id == id }
    }

    fun getDoor(id: Int): Door? {
        for (door in doors) {
            if (door.id == id) {
                return door
            }
        }
        return null
    }

    fun addDoor(door: Door) {
        // replace
        for (i in doors.indices) {
            if (doors[i].id == door.id) {
                doors.set(i, door)
                return
            }
        }

        // add
        doors.add(door)
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
}
