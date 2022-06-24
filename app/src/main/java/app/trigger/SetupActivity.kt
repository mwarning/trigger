package app.trigger

import android.preference.PreferenceActivity
import android.os.Bundle
import android.preference.Preference
import android.widget.Toast
import android.graphics.Bitmap
import app.trigger.ssh.KeyPairBean
import android.content.DialogInterface
import android.preference.Preference.OnPreferenceChangeListener
import android.os.Build
import android.preference.PreferenceGroup
import android.preference.PreferenceCategory
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.CheckBoxPreference
import app.trigger.ssh.SshKeyPairPreference
import app.trigger.mqtt.MqttClientKeyPairPreference
import app.trigger.https.CertificatePreference
import android.view.View
import androidx.appcompat.app.AlertDialog
import java.lang.Exception
import java.security.cert.Certificate
import java.util.ArrayList


class SetupActivity : PreferenceActivity() {
    private var setupGroups: ArrayList<PreferenceGroup>? = null
    private lateinit var builder: AlertDialog.Builder
    private lateinit var setup: Setup
    private fun showErrorMessage(title: String, message: String) {
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }

    // collect all PreferenceGroups except first
    private fun collectGroups(): ArrayList<PreferenceGroup> {
        val groups = ArrayList<PreferenceGroup>()
        val count = preferenceScreen.preferenceCount
        var i = count - 1
        while (i > 0) {
            val p = preferenceScreen.getPreference(i) as PreferenceGroup
            if (p != null) {
                groups.add(p)
                preferenceScreen.removePreference(p)
            }
            i -= 1
        }
        return groups
    }

    private fun showGroup(key: String?) {
        // hide all groups
        run {
            var i = 0
            while (i < setupGroups!!.size) {
                preferenceScreen.removePreference(setupGroups!![i])
                i += 1
            }
        }

        // show specific group
        var i = 0
        while (i < setupGroups!!.size) {
            if (setupGroups!![i].key == key) {
                val group = setupGroups!![i]
                preferenceScreen.addPreference(group)
                return
            }
            i += 1
        }
        Log.e(TAG, "showGroup(): PreferenceGroup not found: $key")
    }

    fun onSaveButtonClicked(v: View?) {
        storeSetup()
    }

    fun onDeleteButtonClicked(v: View?) {
        builder.setTitle(R.string.confirm)
        builder.setMessage(R.string.really_remove_item)
        builder.setCancelable(false) // not necessary
        builder.setPositiveButton(R.string.yes) { dialog: DialogInterface?, id: Int ->
            Settings.removeSetup(setup.id)
            // close this dialog and settings
            finish()
        }
        builder.setNegativeButton(R.string.no) { dialog: DialogInterface, id: Int ->
            // close this dialog
            dialog.cancel()
        }

        // create dialog box
        val alert = builder.create()
        alert.show()
    }

    // a recursive method to find a preference by key
    private fun findAnyPreference(key: String, group: PreferenceGroup?): Preference? {
        var group = group
        if (group == null) {
            group = preferenceScreen
        }
        val count = group!!.preferenceCount
        var i = 0
        while (i < count) {
            var pref = group.getPreference(i)
            if (pref!!.key == key) {
                return pref
            } else if (pref is PreferenceGroup) {
                pref = findAnyPreference(key, pref)
                if (pref != null) {
                    return pref
                }
            }
            i += 1
        }
        return null
    }

    private fun setMainGroupTitle(name: String?) {
        val pc = findPreference("main_category") as PreferenceCategory
        if (pc != null) {
            if (name!!.length > 0) {
                pc.title = name
            } else {
                pc.setTitle(R.string.new_entry)
            }
        } else {
            Log.e(TAG, "setMainGroupTitle(): Cannot find main_category")
        }
    }

    private fun getSummaryValue(key: String, value: String): String {
        if (value.isEmpty()) {
            return resources.getString(R.string.none)
        }
        return if (key == "password") {
            // only show password as star sequences
            String(CharArray(value.length)).replace("\u0000", "*")
        } else {
            value
        }
    }

    private fun setText(key: String, value: String) {
        val p = findAnyPreference(key, null)
        if (p is EditTextPreference) {
            val etp = p
            etp.text = value

            // show value as summary
            etp.onPreferenceChangeListener = OnPreferenceChangeListener { preference: Preference, newValue: Any ->
                preference.summary = getSummaryValue(key, newValue.toString())
                true
            }
            etp.summary = getSummaryValue(key, value)
        } else if (p is ListPreference) {
            val lp = p
            // show value as summary
            lp.onPreferenceChangeListener = OnPreferenceChangeListener { preference: Preference, newValue: Any ->
                preference.summary = getSummaryValue(key, newValue.toString())
                true
            }
            lp.summary = getSummaryValue(key, value)
            lp.value = value
        } else {
            Log.w(TAG, "setText(): Cannot find EditTextPreference/ListPreference in PreferenceGroup with key: $key")
        }
    }

    private fun getText(key: String): String {
        val p = findAnyPreference(key, null)
        return if (p is EditTextPreference) {
            p.text
        } else if (p is ListPreference) {
            p.value
        } else {
            Log.w(TAG, "getText(): Cannot find EditTextPreference/ListPreference in PreferenceGroup with key: $key")
            ""
        }
    }

    private fun setChecked(key: String, checked: Boolean) {
        val cbp = findAnyPreference(key, null) as CheckBoxPreference?
        if (cbp != null) {
            cbp.isChecked = checked
        } else {
            Log.e(TAG, "setChecked(): Cannot find CheckBoxPreference in PreferenceGroup with key: $key")
        }
    }

    private fun getChecked(key: String): Boolean {
        val cbp = findAnyPreference(key, null) as CheckBoxPreference?
        return if (cbp != null) {
            cbp.isChecked
        } else {
            Log.e(TAG, "getChecked(): Cannot find CheckBoxPreference in PreferenceGroup with key: $key")
            false
        }
    }

    private fun getBitmap(key: String): Bitmap? {
        val kpp = findAnyPreference(key, null) as ImagePreference?
        return if (kpp != null) {
            kpp.image
        } else {
            Log.e(TAG, "getBitmap(): Cannot find ImagePreference in PreferenceGroup with key: $key")
            null
        }
    }

    private fun setBitmap(key: String, image: Bitmap?) {
        val kpp = findAnyPreference(key, null) as ImagePreference?
        if (kpp != null) {
            kpp.image = image
        } else {
            Log.e(TAG, "setBitmap(): Cannot find ImagePreference in PreferenceGroup with key: $key")
        }
    }

    private fun getKeyPairBean(key: String): KeyPairBean? {
        val preference = findAnyPreference(key, null)
        return if (preference is SshKeyPairPreference) {
            preference.keyPair
        } else if (preference is MqttClientKeyPairPreference) {
            preference.getKeyPair()
        } else {
            Log.e(TAG, "getKeyPair(): Cannot find KeyPairPreference in PreferenceGroup with key: $key")
            null
        }
    }

    private fun setKeyPairBean(key: String, keypair: KeyPairBean?) {
        val preference = findAnyPreference(key, null)
        if (preference is SshKeyPairPreference) {
            preference.keyPair = keypair
        } else if (preference is MqttClientKeyPairPreference) {
            preference.setKeyPair(keypair)
        } else {
            Log.e(TAG, "setKeyPair(): Cannot find KeyPairPreference in PreferenceGroup with key: $key")
        }
    }

    private fun getCertificate(key: String): Certificate? {
        val cp = findAnyPreference(key, null) as CertificatePreference?
        return if (cp != null) {
            cp.certificate
        } else {
            Log.e(TAG, "getCertificate(): Cannot find CertificatePreference in PreferenceGroup with key: $key")
            null
        }
    }

    private fun setCertificate(key: String, certificate: Certificate?) {
        val cp = findAnyPreference(key, null) as CertificatePreference?
        if (cp != null) {
            cp.certificate = certificate
        } else {
            Log.e(TAG, "setCertificate(): Cannot find CertificatePreference in PreferenceGroup with key: $key")
        }
    }

    fun getRegisterUrl(): String {
        return setup.getRegisterUrl()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        // Set all field to default values - does not work?
        // PreferenceManager.setDefaultValues(context, R.xml.settings, false);
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.setup)
        setContentView(R.layout.activity_setup)

        // change door type
        val list_field = findPreference("type") as ListPreference
        list_field.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            val type = newValue.toString()
            if (type == setup.type) {
                // no door type change
                true
            } else if (type == HttpsDoorSetup.type) {
                setup = HttpsDoorSetup(setup.id, getText("name"))
                loadSetup()
                true
            } else if (type == SshDoorSetup.type) {
                setup = SshDoorSetup(setup.id, getText("name"))
                loadSetup()
                true
            } else if (type == BluetoothDoorSetup.type) {
                setup = BluetoothDoorSetup(setup.id, getText("name"))
                loadSetup()
                true
            } else if (type == NukiDoorSetup.type) {
                setup = NukiDoorSetup(setup.id, getText("name"))
                loadSetup()
                true
            } else if (type == MqttDoorSetup.type) {
                setup = MqttDoorSetup(setup.id, getText("name"))
                loadSetup()
                true
            } else {
                Log.e(TAG, "Unhandled type from selection: $type")
                false
            }
        }

        // update main category title
        val name_field = findPreference("name") as EditTextPreference
        name_field.onPreferenceChangeListener = OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
            val name = newValue.toString()
            setMainGroupTitle(name)
            true
        }
        builder = AlertDialog.Builder(this)
        setupGroups = collectGroups()
        val id = intent.getIntExtra("setup_id", -1)
        val new_setup = Settings.getSetup(id)
        if (new_setup != null) {
            setup = new_setup
        } else  {
            // default setup
            setup = HttpsDoorSetup(Settings.getNewID(), "")
        }

        // init type selection
        list_field.value = setup.type
        loadSetup()
    }

    private fun loadSetup() {
        showGroup(setup.type)
        val fields = setup.javaClass.declaredFields
        for (field in fields) {
            try {
                val accessible = field.isAccessible()
                field.setAccessible(true)
                val name = field.name
                val type = field.type
                val value = field[setup]
                if (name == "type" || name == "id" || name == "Companion") {
                    // ignore for display in preference field
                } else if (type == String::class.java) {
                    setText(name, value as String)
                } else if (type == Int::class.java) {
                    setText(name, value.toString())
                } else if (type == Long::class.java) {
                    setText(name, value.toString())
                } else if (type == Int::class.javaPrimitiveType) {
                    setText(name, value.toString())
                } else if (type == Long::class.javaPrimitiveType) {
                    setText(name, value.toString())
                } else if (type == Boolean::class.java) {
                    setChecked(name, value as Boolean)
                } else if (type == Boolean::class.javaPrimitiveType) {
                    setChecked(name, value as Boolean)
                } else if (type == Bitmap::class.java) {
                    setBitmap(name, value as Bitmap?)
                } else if (type == KeyPairBean::class.java) {
                    setKeyPairBean(name, value as KeyPairBean?)
                } else if (type == Certificate::class.java) {
                    setCertificate(name, value as Certificate?)
                } else {
                    throw Exception("Unhandled type ${type} of field $name")
                }
                field.setAccessible(accessible)
            } catch (e: Exception) {
                Log.e(TAG, "loadSetup: $e")
                e.printStackTrace()
            }
        }

        // update title
        setMainGroupTitle(setup.name)
    }

    // apply preference fields to setup object fields
    fun storeSetup() {
        val fields = setup.javaClass.declaredFields
        for (field in fields) {
            val name = field.name
            val type = field.type
            val accessible = field.isAccessible()
            try {
                field.setAccessible(true)
                if (name == "id" || name == "type" || name == "Companion") {
                    // ignore - id is not displayed and type is read only field
                } else if (findAnyPreference(name, null) == null) {
                    // ignore
                    Log.w(TAG, "storeSetup(): Ignore setup field: $name")
                } else if (type == String::class.java) {
                    field[setup] = getText(name)
                } else if (type == Boolean::class.java) {
                    field[setup] = getChecked(name)
                } else if (type == Int::class.java) {
                    field[setup] = getText(name).toInt()
                } else if (type == Long::class.java) {
                    field[setup] = getText(name).toLong()
                } else if (type == Int::class.javaPrimitiveType) {
                    field[setup] = getText(name).toInt()
                } else if (type == Long::class.javaPrimitiveType) {
                    field[setup] = getText(name).toLong()
                } else if (type == Bitmap::class.java) {
                    field[setup] = getBitmap(name)
                } else if (type == KeyPairBean::class.java) {
                    field[setup] = getKeyPairBean(name)
                } else if (type == Certificate::class.java) {
                    field[setup] = getCertificate(name)
                } else {
                    Log.e(TAG, "storeSetup: Unhandled type for $name: $type")
                }
            } catch (ex: Exception) {
                showErrorMessage("Error", "Input for '$name' caused an error: $ex")
                return
            } finally {
                field.setAccessible(accessible)
            }
        }

        val count = Settings.countNames(setup.name)
        val exists = Settings.idExists(setup.id)
        if (exists && count > 1 || !exists && count > 0) {
            showErrorMessage("Entry Exists", "Name already exists.")
        } else if (setup.name == null || setup.name.isEmpty()) {
            showErrorMessage("Invalid Name", "Door name is not set.")
        } else {
            Settings.addSetup(setup)

            // report all done
            Toast.makeText(applicationContext, "Done", Toast.LENGTH_SHORT).show()

            // needed for SSID matching
            if (setup.getWiFiSSIDs().isNotEmpty()) {
                checkFineLocationPermission()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_FINE_LOCATION_PERMISSION -> if (Utils.allGranted(grantResults)) {
                // permissions granted
                Toast.makeText(applicationContext, "Permissions granted - SSID matching should work now.", Toast.LENGTH_SHORT).show()
            } else {
                showErrorMessage("Permissions Required", "Cannot match WiFi SSIDs.")
            }
        }
    }

    // SSID matching needs fine location permissions
    private fun checkFineLocationPermission() {
        if (Build.VERSION.SDK_INT >= 26) {
            if (!Utils.hasFineLocationPermission(this)) {
                Utils.requestFineLocationPermission(this, REQUEST_FINE_LOCATION_PERMISSION)
            }
        }
    }

    companion object {
        private const val TAG = "SetupActivity"
        private const val REQUEST_FINE_LOCATION_PERMISSION = 0x01
    }
}
