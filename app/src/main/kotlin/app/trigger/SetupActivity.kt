package app.trigger

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat.requestApplyInsets
import app.trigger.https.HttpsClientCertificateActivity
import app.trigger.https.HttpsClientKeyPairActivity
import app.trigger.https.HttpsServerCertificateActivity
import app.trigger.mqtt.MqttClientCertificateActivity
import app.trigger.mqtt.MqttClientKeyPairActivity
import app.trigger.mqtt.MqttServerCertificateActivity
import app.trigger.ssh.SshKeyPairActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val door_id = intent.getIntExtra("door_id", -1)

        currentDoor = if (door_id == -1) {
            HttpsDoor(Settings.getNewDoorIdentifier(), "")
        } else {
            val selectedDoor = Settings.getDoor(door_id)
            if (selectedDoor != null) {
                // we work on a clone until saved
                Settings.fromJsonObject(Settings.toJsonObject(selectedDoor)!!)!!
            } else {
                Log.d(TAG, "door not found $door_id")
                finish()
                return
            }
        }

        initViews()
    }

    private fun initViews() {
        val door = currentDoor ?: return

        Log.d(TAG, "initViews() ${door.type}")

        title = if (door.name.isEmpty()) {
            getString(R.string.title_door, getString(R.string.setting_no_value))
        } else {
            getString(R.string.title_door, door.name)
        }

        when (door.type) {
            HttpsDoor.TYPE -> {
                setContentView(R.layout.activity_setup_https)
            }
            SshDoor.TYPE -> {
                setContentView(R.layout.activity_setup_ssh)
            }
            BluetoothDoor.TYPE -> {
                setContentView(R.layout.activity_setup_bluetooth)
            }
            MqttDoor.TYPE -> {
                setContentView(R.layout.activity_setup_mqtt)
            }
            NukiDoor.TYPE -> {
                setContentView(R.layout.activity_setup_nuki)
            }
            else -> {
                Log.e(TAG, "Invalid door type: ${door.type}")
                finish()
                return
            }
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // updates insets when door type changes (otherwise the toolbar lands under the system status bar)
        requestApplyInsets(findViewById<View>(android.R.id.content).rootView)

        setupTextView(R.id.doorNameTextView, R.string.setting_door_name, door.name) { newValue ->
            if (newValue.isEmpty()) {
                Toast.makeText(this, R.string.error_invalid_name, Toast.LENGTH_SHORT).show()
            } else if (!Settings.isDuplicateName(newValue, door)) {
                if (door.name != newValue) {
                    door.name = newValue
                    initViews()
                }
            } else {
                Toast.makeText(this, R.string.error_duplicate_name, Toast.LENGTH_SHORT).show()
            }
        }

        setupSpinner(door.type,
            R.id.doorTypesSpinner,
            R.array.DoorTypeLabels,
            R.array.DoorTypeValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null && newValue != door.type) {
                        currentDoor = when (newValue) {
                            HttpsDoor.TYPE -> {
                                HttpsDoor(door.id, door.name)
                            }

                            SshDoor.TYPE -> {
                                SshDoor(door.id, door.name)
                            }

                            BluetoothDoor.TYPE -> {
                                BluetoothDoor(door.id, door.name)
                            }

                            MqttDoor.TYPE -> {
                                MqttDoor(door.id, door.name)
                            }

                            NukiDoor.TYPE -> {
                                NukiDoor(door.id, door.name)
                            }

                            else -> {
                                Log.e("SetupActivity", "Invalid door type: $newValue")
                                return
                            }
                        }
                        initViews()
                    }
                }
            })

        if (door is HttpsDoor) {
            initHttpsViews(door)
        }

        if (door is SshDoor) {
            initSshViews(door)
        }

        if (door is BluetoothDoor) {
            initBluetoothViews(door)
        }

        if (door is MqttDoor) {
            initMqttViews(door)
        }

        if (door is NukiDoor) {
            initNukiViews(door)
        }

        findViewById<SwitchMaterial>(R.id.openDoorImageSwitch).apply {
            isChecked = (door.getStateImage(DoorStatus.StateCode.OPEN) != null)
            setOnClickListener {
                val intent = Intent(applicationContext, ImageActivity::class.java)
                intent.putExtra("state_code", DoorStatus.StateCode.OPEN.name)
                startActivity(intent)
            }
        }

        findViewById<SwitchMaterial>(R.id.closedDoorImageSwitch).apply {
            isChecked = (door.getStateImage(DoorStatus.StateCode.CLOSED) != null)
            setOnClickListener {
                val intent = Intent(applicationContext, ImageActivity::class.java)
                intent.putExtra("state_code", DoorStatus.StateCode.CLOSED.name)
                startActivity(intent)
            }
        }

        findViewById<SwitchMaterial>(R.id.disabledDoorImageSwitch).apply {
            isChecked = (door.getStateImage(DoorStatus.StateCode.DISABLED) != null)
            setOnClickListener {
                val intent = Intent(applicationContext, ImageActivity::class.java)
                intent.putExtra("state_code", DoorStatus.StateCode.DISABLED.name)
                startActivity(intent)
            }
        }

        findViewById<SwitchMaterial>(R.id.unknownStatusImageSwitch).apply {
            isChecked = (door.getStateImage(DoorStatus.StateCode.UNKNOWN) != null)
            setOnClickListener {
                val intent = Intent(applicationContext, ImageActivity::class.java)
                intent.putExtra("state_code", DoorStatus.StateCode.UNKNOWN.name)
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.SaveButton).setOnClickListener {
            if (door.name.isEmpty()) {
                showMessage(R.string.error_invalid_name)
            } else {
                if (Settings.addDoor(door)) {
                    showMessage(R.string.done)
                    finish()
                } else {
                    showMessage(R.string.error)
                }
            }
        }

        findViewById<Button>(R.id.DeleteButton).apply {
            val current = currentDoor
            if (current != null && Settings.getDoor(current.id) != null) {
                visibility = View.VISIBLE
                setOnClickListener {
                    showDeleteDialog()
                }
            } else {
                // not saved yet
                visibility = View.GONE
            }
        }

        findViewById<Button>(R.id.AbortButton).setOnClickListener {
            finish()
        }
    }

    private fun isHttpURL(url: String): Boolean {
        return url.startsWith("https://") || url.startsWith("http://")
    }

    private fun initHttpsViews(door: HttpsDoor) {
        findViewById<CheckBox>(R.id.httpsRequireWLANCheckBox).apply {
            isChecked = door.require_wifi
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.require_wifi = isChecked
            }
        }

        setupTextView(R.id.httpsOpenUrlTextView, R.string.setting_https_open_url, door.open_query,
            { newValue ->
                if (newValue.isEmpty() || isHttpURL(newValue)) {
                    door.open_query = newValue
                    initViews()
                } else {
                    showMessage(R.string.error_invalid_url)
                }
            })

        setupSpinner(door.open_method,
            R.id.openHttpMethodSpinner,
            R.array.HttpMethodLabels,
            R.array.HttpMethodValues,
            object: SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        door.open_method = newValue
                    }
                }
            })

        setupTextView(R.id.httpsCloseUrlTextView, R.string.setting_https_close_url, door.close_query,
            { newValue ->
                if (newValue.isEmpty() || isHttpURL(newValue)) {
                    door.close_query = newValue
                    initViews()
                } else {
                    showMessage(R.string.error_invalid_url)
                }
            })

        setupSpinner(door.close_method,
            R.id.closeHttpMethodSpinner,
            R.array.HttpMethodLabels,
            R.array.HttpMethodValues,
            object: SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        door.close_method = newValue
                    }
                }
            })

        setupTextView(R.id.httpsRingUrlTextView, R.string.setting_https_ring_url, door.ring_query,
            { newValue ->
                if (newValue.isEmpty() || isHttpURL(newValue)) {
                    door.ring_query = newValue
                    initViews()
                } else {
                    showMessage(R.string.error_invalid_url)
                }
            })

        setupSpinner(door.ring_method,
            R.id.ringHttpMethodSpinner,
            R.array.HttpMethodLabels,
            R.array.HttpMethodValues,
            object: SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        door.ring_method = newValue
                    }
                }
            })

        setupTextView(R.id.httpsStatusUrlTextView, R.string.setting_https_status_url, door.status_query,
            { newValue ->
                if (newValue.isEmpty() || isHttpURL(newValue)) {
                    door.status_query = newValue
                    initViews()
                } else {
                    showMessage(R.string.error_invalid_url)
                }
            })

        setupSpinner(door.status_method,
            R.id.statusHttpMethodSpinner,
            R.array.HttpMethodLabels,
            R.array.HttpMethodValues,
            object: SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        door.status_method = newValue
                    }
                }
            })

        setupTextView(R.id.replyPatternLockedTextView, R.string.setting_reply_pattern_locked, door.locked_pattern,
            { newValue ->
                door.locked_pattern = newValue
                initViews()
            })

        setupTextView(R.id.replyPatternUnlockedTextView, R.string.setting_reply_pattern_unlocked, door.unlocked_pattern,
            { newValue ->
                door.unlocked_pattern = newValue
                initViews()
            })

        setupTextView(R.id.wlanSsidsTextView, R.string.setting_wlan_ssids, door.ssids,
            { newValue ->
                door.ssids = newValue
                initViews()
            })

        findViewById<CheckBox>(R.id.ignoreCertificateValidityCheckBox).apply {
            isChecked = door.ignore_certificate
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.ignore_certificate = isChecked
            }
        }

        findViewById<CheckBox>(R.id.ignoreCertificateHostnameCheckBox).apply {
            isChecked = door.ignore_hostname_mismatch
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.ignore_hostname_mismatch = isChecked
            }
        }

        findViewById<CheckBox>(R.id.ignoreCertificateDateCheckBox).apply {
            isChecked = door.ignore_expiration
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.ignore_expiration = isChecked
            }
        }

        findViewById<SwitchMaterial>(R.id.httpsClientCertificateSwitch).apply {
            isChecked = (door.client_certificate != null)
            setOnClickListener {
                val intent = Intent(applicationContext, HttpsClientCertificateActivity::class.java)
                startActivity(intent)
            }
        }

        findViewById<SwitchMaterial>(R.id.httpsServerCertificateSwitch).apply {
            isChecked = (door.server_certificate != null)
            setOnClickListener {
                val intent = Intent(applicationContext, HttpsServerCertificateActivity::class.java)
                startActivity(intent)
            }
        }

        findViewById<SwitchMaterial>(R.id.httpsClientPrivateKeySwitch).apply {
            isChecked = (door.client_keypair != null)
            setOnClickListener {
                val intent = Intent(applicationContext, HttpsClientKeyPairActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun initSshViews(door: SshDoor) {
        findViewById<CheckBox>(R.id.sshRequireWLANCheckBox).apply {
            isChecked = door.require_wifi
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.require_wifi = isChecked
            }
        }

        findViewById<SwitchMaterial>(R.id.sshKeyPairSwitch).apply {
            isChecked = (door.keypair != null)
            setOnClickListener {
                val intent = Intent(applicationContext, SshKeyPairActivity::class.java)
                startActivity(intent)
            }
        }

        setupTextView(R.id.sshServerAddressTextView, R.string.setting_ssh_server_address, door.host,
            { newValue ->
                door.host = newValue
                initViews()
            })

        setupTextView(R.id.sshServerPortTextView, R.string.setting_ssh_server_port, "${door.port}",
            { newValue ->
                val port = newValue.toIntOrNull()
                if (port == null || port < 1 || port >= 65536) {
                    showMessage(R.string.invalid_port)
                } else {
                    door.port = port
                    initViews()
                }
            })

        setupTextView(R.id.sshLoginNameTextView, R.string.setting_ssh_login_name, door.user,
            { newValue ->
                door.user = newValue
                initViews()
            })

        setupTextView(R.id.sshLoginPasswordTextView, R.string.setting_ssh_login_password, door.password,
            { newValue ->
                door.password = newValue
                initViews()
            })

        setupTextView(R.id.sshOpenCommandTextView, R.string.setting_ssh_open_command, door.open_command,
            { newValue ->
                door.open_command = newValue
                initViews()
            })

        setupTextView(R.id.sshCloseCommandTextView, R.string.setting_ssh_close_command, door.close_command,
            { newValue ->
                door.close_command = newValue
                initViews()
            })

        setupTextView(R.id.sshRingCommandTextView, R.string.setting_ssh_ring_command, door.ring_command,
            { newValue ->
                door.ring_command = newValue
                initViews()
            })

        setupTextView(R.id.sshStatusCommandTextView, R.string.setting_ssh_status_command, door.state_command,
            { newValue ->
                door.state_command = newValue
                initViews()
            })

        setupTextView(R.id.sshCommandTimeoutTextView, R.string.setting_ssh_command_timeout, "${door.timeout}",
            { newValue ->
                val timeout = newValue.toIntOrNull()
                if (timeout == null || timeout < 0 || timeout > 5000) {
                    showMessage(R.string.invalid_timeout)
                } else {
                    door.timeout = timeout
                    initViews()
                }
            })

        setupTextView(R.id.wlanSsidsTextView, R.string.setting_wlan_ssids, door.ssids,
            { newValue ->
                door.ssids = newValue
                initViews()
            })
    }

    private fun initBluetoothViews(door: BluetoothDoor) {
        setupTextView(R.id.bluetoothDeviceNameTextView, R.string.setting_bluetooth_device_name, door.device_name,
            { newValue ->
                door.device_name = newValue
                initViews()
            })

        setupTextView(R.id.bluetoothServiceUuidTextView, R.string.setting_bluetooth_service_uuid, door.service_uuid,
            { newValue ->
                door.service_uuid = newValue
                initViews()
            })

        setupTextView(R.id.replyPatternLockedTextView, R.string.setting_reply_pattern_locked, door.locked_pattern,
            { newValue ->
                door.locked_pattern = newValue
                initViews()
            })

        setupTextView(R.id.replyPatternUnlockedTextView, R.string.setting_reply_pattern_unlocked, door.unlocked_pattern,
            { newValue ->
                door.unlocked_pattern = newValue
                initViews()
            })

        setupTextView(R.id.bluetoothOpenCommandTextView, R.string.setting_bluetooth_open_command, door.open_query,
            { newValue ->
                door.open_query = newValue
                initViews()
            })

        setupTextView(R.id.bluetoothCloseCommandTextView, R.string.setting_bluetooth_close_command, door.close_query,
            { newValue ->
                door.close_query = newValue
                initViews()
            })

        setupTextView(R.id.bluetoothRingCommandTextView, R.string.setting_bluetooth_ring_command, door.ring_query,
            { newValue ->
                door.ring_query = newValue
                initViews()
            })

        setupTextView(R.id.bluetoothStatusCommandTextView, R.string.setting_bluetooth_status_command, door.status_query,
            { newValue ->
                door.status_query = newValue
                initViews()
            })
    }

    private fun initNukiViews(door: NukiDoor) {
        setupTextView(R.id.nukiLockNameTextView, R.string.setting_nuki_lock_name, door.device_name,
            { newValue ->
                door.device_name = newValue
                initViews()
            })

        setupTextView(R.id.nukiUserNameTextView, R.string.setting_nuki_user_name, door.user_name,
            { newValue ->
                door.user_name = newValue
                initViews()
            })

        findViewById<TextView>(R.id.nukiAppIdentifierTextView).text = "${door.app_id}"
        findViewById<TextView>(R.id.nukiLockIdentifierTextView).text = "${door.auth_id}"
        findViewById<TextView>(R.id.nukiSharedKeyTextView).text = door.shared_key
    }

    private fun initMqttViews(door: MqttDoor) {
        findViewById<CheckBox>(R.id.mqttRequireWLANCheckBox).apply {
            isChecked = door.require_wifi
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.require_wifi = isChecked
            }
        }

        setupTextView(R.id.mqttAddressTextView, R.string.setting_mqtt_address, door.server,
            { newValue ->
                door.server = newValue
                initViews()
            })

        setupTextView(R.id.mqttUsernameTextView, R.string.setting_mqtt_username, door.username,
            { newValue ->
                door.username = newValue
                initViews()
            })

        setupTextView(R.id.mqttPasswordTextView, R.string.setting_mqtt_password, door.password,
            { newValue ->
                door.password = newValue
                initViews()
            })


        setupTextView(R.id.mqttStatusTopicTextView, R.string.setting_mqtt_status_topic, door.status_topic,
            { newValue ->
                door.status_topic = newValue
                initViews()
            })

        setupTextView(R.id.mqttCommandTopicTextView, R.string.setting_mqtt_command_topic, door.command_topic,
            { newValue ->
                door.command_topic = newValue
                initViews()
            })

        setupTextView(R.id.mqttOpenCommandTextView, R.string.setting_mqtt_open_command, door.open_command,
            { newValue ->
                door.open_command = newValue
                initViews()
            })

        setupTextView(R.id.mqttCloseCommandTextView, R.string.setting_mqtt_close_command, door.close_command,
            { newValue ->
                door.close_command = newValue
                initViews()
            })

        setupTextView(R.id.mqttRingCommandTextView, R.string.setting_mqtt_ring_command, door.ring_command,
            { newValue ->
                door.ring_command = newValue
                initViews()
            })

       setupSpinner("${door.qos}",
            R.id.mqttQosSpinner,
            R.array.MqttQosLabels,
            R.array.MqttQosValues,
            object: SpinnerItemSelected {
                override fun call(newValue: String?) {
                    val qos = newValue?.let { newValue.toIntOrNull() }
                    if (qos != null) {
                        door.qos = qos
                    }
                }
            })

        findViewById<CheckBox>(R.id.mqttRetainedCheckBox).apply {
            isChecked = door.retained
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.retained = isChecked
            }
        }

        findViewById<SwitchMaterial>(R.id.mqttClientCertificateSwitch).apply {
            isChecked = (door.client_certificate != null)
            setOnClickListener {
                val intent = Intent(applicationContext, MqttClientCertificateActivity::class.java)
                startActivity(intent)
            }
        }

        findViewById<SwitchMaterial>(R.id.mqttServerCertificateSwitch).apply {
            isChecked = (door.server_certificate != null)
            setOnClickListener {
                val intent = Intent(applicationContext, MqttServerCertificateActivity::class.java)
                startActivity(intent)
            }
        }

        findViewById<SwitchMaterial>(R.id.mqttClientPrivateKeySwitch).apply {
            isChecked = (door.client_keypair != null)
            setOnClickListener {
                val intent = Intent(applicationContext, MqttClientKeyPairActivity::class.java)
                startActivity(intent)
            }
        }

        findViewById<CheckBox>(R.id.ignoreCertificateValidityCheckBox).apply {
            isChecked = door.ignore_certificate
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.ignore_certificate = isChecked
            }
        }

        findViewById<CheckBox>(R.id.ignoreCertificateHostnameCheckBox).apply {
            isChecked = door.ignore_hostname_mismatch
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.ignore_hostname_mismatch = isChecked
            }
        }

        findViewById<CheckBox>(R.id.ignoreCertificateDateCheckBox).apply {
            isChecked = door.ignore_expiration
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                door.ignore_expiration = isChecked
            }
        }

        setupTextView(R.id.wlanSsidsTextView, R.string.setting_wlan_ssids, door.ssids,
            { newValue ->
                door.ssids = newValue
                initViews()
            })

        setupTextView(R.id.replyPatternLockedTextView, R.string.setting_reply_pattern_locked, door.locked_pattern,
            { newValue ->
                door.locked_pattern = newValue
                initViews()
            })

        setupTextView(R.id.replyPatternUnlockedTextView, R.string.setting_reply_pattern_unlocked, door.unlocked_pattern,
            { newValue ->
                door.unlocked_pattern = newValue
                initViews()
            })
    }

    private fun setupTextView(textViewId: Int, titleId: Int, currentValue: String, onChange: (newValue: String) -> Unit) {
        val textView = findViewById<TextView>(textViewId)
        textView.text = currentValue.ifEmpty { getString(R.string.setting_no_value) }
        (textView.parent as LinearLayout).setOnClickListener { showStringDialog(titleId, currentValue, onChange) }
    }

    private fun showStringDialog(titleId: Int, value: String, onChange: (newValue: String) -> Unit) {
        Log.d(TAG, "showStringDialog()")

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_string)
        val titleText = dialog.findViewById<TextView>(R.id.ChangeStringTextView)
        val stringEditText = dialog.findViewById<EditText>(R.id.StringEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)

        titleText.setText(titleId)
        stringEditText.setText(value, TextView.BufferType.EDITABLE)

        okButton.setOnClickListener {
            val newValue = stringEditText.text.toString().trim { it <= ' ' }
            if (value != newValue) {
                onChange(newValue)
            }

            dialog.cancel()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_delete_door)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)

        okButton.setOnClickListener {
            val door = currentDoor
            if (door != null) {
                if (Settings.removeDoor(door.id)) {
                    showMessage(R.string.done)
                } else {
                    showMessage(R.string.error)
                }
            }

            dialog.cancel()
            finish()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        initViews()
    }

    private fun showMessage(textId: Int) {
        Toast.makeText(applicationContext, textId, Toast.LENGTH_SHORT).show()
    }

    private fun showMessage(textString: String) {
        Toast.makeText(applicationContext, textString, Toast.LENGTH_SHORT).show()
    }

    private interface SpinnerItemSelected {
        fun call(newValue: String?)
    }

    private fun setupSpinner(
        currentValue: String,
        spinnerId: Int,
        arrayId: Int,
        arrayValuesId: Int,
        callback: SpinnerItemSelected,
    ) {
        val arrayValues = resources.getStringArray(arrayValuesId)
        val spinner = findViewById<Spinner>(spinnerId)
        val spinnerAdapter = ArrayAdapter.createFromResource(this, arrayId, R.layout.spinner_item_settings)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_settings)

        spinner.adapter = spinnerAdapter
        spinner.setSelection(arrayValues.indexOf(currentValue))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var check = 0
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos >= arrayValues.size) {
                    showMessage("pos out of bounds: $arrayValues")
                    return
                }
                if (check++ > 0) {
                    callback.call(arrayValues[pos])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // ignore
            }
        }
    }

    companion object {
        private const val TAG = "SetupActivity"
        private const val REQUEST_FINE_LOCATION_PERMISSION = 0x01
        var currentDoor: Door? = null
    }
}
