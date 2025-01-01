package app.trigger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.app.Dialog
import android.graphics.Bitmap
import app.trigger.DoorStatus.StateCode
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import app.trigger.DoorReply.ReplyCode
import android.graphics.BitmapFactory
import android.view.animation.Animation
import android.view.accessibility.AccessibilityEvent
import android.widget.AdapterView.OnItemSelectedListener
import android.view.animation.AnimationUtils
import org.conscrypt.OpenSSLProvider
import android.net.wifi.WifiManager
import android.net.NetworkInfo
import android.net.NetworkInfo.DetailedState
import android.os.Build
import android.os.Looper
import app.trigger.ssh.SshRequestHandler
import app.trigger.https.HttpsRequestHandler
import app.trigger.bluetooth.BluetoothRequestHandler
import app.trigger.nuki.NukiRequestHandler
import app.trigger.mqtt.MqttRequestHandler
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.lang.Exception
import java.security.Security
import java.util.*


class MainActivity : AppCompatActivity(), OnTaskCompleted {
    private var hasDoorSelected = false
    private lateinit var stateImage: ImageView
    private lateinit var lockButton: ImageButton
    private lateinit var ringButton: ImageButton
    private lateinit var unlockButton: ImageButton
    private lateinit var spinner: Spinner
    private lateinit var pressed: Animation
    private lateinit var state_open_default_image: Bitmap
    private lateinit var state_closed_default_image: Bitmap
    private lateinit var state_disabled_default_image: Bitmap
    private lateinit var state_unknown_default_image: Bitmap
    private lateinit var builder: AlertDialog.Builder
    private var ignore_wifi_check_for_door_id = -1

    enum class Action {
        OPEN_DOOR,
        CLOSE_DOOR,
        RING_DOOR,  // ring the door bell
        FETCH_STATE // fetch the door state
    }

    // helper class for spinner
    private class SpinnerItem(val id: Int, val name: String, val ssids: String) {
        override fun toString(): String {
            return name
        }
    }

    private fun getPreferredSpinnerIndex(items: ArrayList<SpinnerItem>, match_ssid: Boolean): Int {
        var i: Int

        // select by ssid
        if (match_ssid && WifiTools.isConnected()) {
            val ssid = WifiTools.getCurrentSSID()
            i = 0
            for (item in items) {
                if (WifiTools.matchSSID(item.ssids, ssid)) {
                    return i
                }
                i += 1
            }
        }

        // keep previous selection
        val current = getSelectedDoor()
        if (current != null) {
            i = 0
            for (item in items) {
                if (current.id == item.id) {
                    return i
                }
                i += 1
            }
        }

        // select first item
        return if (items.size > 0) {
            0
        } else {
            AccessibilityEvent.INVALID_POSITION
        }
    }

    private fun getSelectedDoor(): Door? {
        val item = spinner.selectedItem as SpinnerItem?
        return if (item != null) {
            Settings.getDoor(item.id)
        } else {
            null
        }
    }

    private fun getSelectedDoorId(): Int  {
        val item = spinner.selectedItem as SpinnerItem?
        return item?.id ?: -1
    }

    private fun updateSpinner(match_ssid: Boolean) {
        val items = ArrayList<SpinnerItem>()
        for (door in Settings.getDoors()) {
            items.add(SpinnerItem(door.id, door.name, door.getWiFiSSIDs()))
        }

        // sort items by name
        items.sortWith { s1: SpinnerItem, s2: SpinnerItem -> s1.name.compareTo(s2.name) }
        val selection = getPreferredSpinnerIndex(items, match_ssid)
        val adapter = ArrayAdapter(this, R.layout.main_spinner, items)
        spinner.adapter = adapter
        spinner.setSelection(selection)
        spinner.onItemSelectedListener = object : OnItemSelectedListener {
            var check = 0 // prevent triggering on creation
            override fun onItemSelected(parent: AdapterView<*>?, view: View, pos: Int, id: Long) {
                if (check++ > 0) {
                    updateButtons()
                    callRequestHandler(Action.FETCH_STATE)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // nothing to do
            }
        }

        // something is selected
        hasDoorSelected = (spinner.selectedItemPosition != AccessibilityEvent.INVALID_POSITION)

        updateButtons()
    }

    // hide lock/unlock/ring buttons if there is no action behind them
    private fun updateButtons() {
        val door = getSelectedDoor()

        if (door == null || door.canClose()) {
            lockButton.visibility = View.VISIBLE
        } else {
            lockButton.visibility = View.GONE
        }

        if (door == null || door.canOpen()) {
            unlockButton.visibility = View.VISIBLE
        } else {
            unlockButton.visibility = View.GONE
        }

        if (door == null || door.canRing()) {
            ringButton.visibility = View.VISIBLE
        } else {
            ringButton.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WifiTools.init(applicationContext)
        BluetoothTools.init(applicationContext)
        Settings.init(applicationContext)

        state_open_default_image = BitmapFactory.decodeResource(resources, R.drawable.state_open)
        state_closed_default_image = BitmapFactory.decodeResource(resources, R.drawable.state_closed)
        state_disabled_default_image = BitmapFactory.decodeResource(resources, R.drawable.state_disabled)
        state_unknown_default_image = BitmapFactory.decodeResource(resources, R.drawable.state_unknown)
        spinner = findViewById(R.id.selection_spinner)
        stateImage = findViewById(R.id.stateImage)
        lockButton = findViewById(R.id.Lock)
        ringButton = findViewById(R.id.Ring)
        unlockButton = findViewById(R.id.Unlock)

        builder = AlertDialog.Builder(this)

        pressed = AnimationUtils.loadAnimation(this, R.anim.pressed)
        updateSpinner(true)

        findViewById<ImageView>(R.id.stateImage).setOnClickListener {
            // update door state
            callRequestHandler(Action.FETCH_STATE)
        }

        Log.d(TAG, "Security.insertProviderAt(new OpenSSLProvider()")
        Security.insertProviderAt(OpenSSLProvider(), 1)
    }

    override fun onResume() {
        updateSpinner(false)
        invalidateOptionsMenu()
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        registerReceiver(broadcastReceiver, intentFilter)
        super.onResume()
    }

    override fun onPause() {
        unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    private fun showMessage(textId: Int) {
        Toast.makeText(applicationContext, textId, Toast.LENGTH_SHORT).show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    // listen for connectivity changes
    private var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isInitialStickyBroadcast) {
                // do nothing if the receiver was just initialized
                return
            }

            if (intent.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                        ?: return
                val state = networkInfo.detailedState
                if (state == DetailedState.CONNECTED || state == DetailedState.DISCONNECTED) {
                    // WifiTools.isConnected() will take a while until it returns true
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed({ callRequestHandler(Action.FETCH_STATE) }, 1000)
                }
            }

            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF) {
                    callRequestHandler(Action.FETCH_STATE)
                }
            }
        }
    }

    fun doUnlock(view: View?) {
        unlockButton.startAnimation(pressed)
        callRequestHandler(Action.OPEN_DOOR)
    }

    fun doLock(view: View?) {
        lockButton.startAnimation(pressed)
        callRequestHandler(Action.CLOSE_DOOR)
    }

    fun doRing(view: View?) {
        ringButton.startAnimation(pressed)
        callRequestHandler(Action.RING_DOOR)
    }

    private fun changeUI(state: StateCode?) {
        var image: Bitmap? = null

        when (state) {
            StateCode.OPEN -> {
                image = state_open_default_image
                lockButton.isEnabled = true
                unlockButton.isEnabled = true
            }
            StateCode.CLOSED -> {
                image = state_closed_default_image
                lockButton.isEnabled = true
                unlockButton.isEnabled = true
            }
            StateCode.DISABLED -> {
                image = state_disabled_default_image
                lockButton.isEnabled = false
                unlockButton.isEnabled = false
            }
            StateCode.UNKNOWN -> {
                image = state_unknown_default_image
                // Enabled, in case the API does not support state queries
                lockButton.isEnabled = true
                unlockButton.isEnabled = true
            }
            else -> {
                Log.e(TAG, "Invalid Door Status ${state}")
            }
        }

        // use custom image
        val door = getSelectedDoor()
        if (door != null) {
            val custom = door.getStateImage(state)
            if (custom != null) {
                image = custom
            }
        }

        // set background image
        stateImage.setImageBitmap(image)

        // update action bar menu
        invalidateOptionsMenu()
    }

    // can be called multiple times by the same task
    override fun onTaskResult(door_id: Int, code: ReplyCode, message: String) {
        runOnUiThread {
            val door = getSelectedDoor()
            if (door == null || door.id != door_id) {
                // probably some late result that does not matter anymore
                return@runOnUiThread
            }
            val state = door.parseReply(DoorReply(code, message))

            // change state image
            changeUI(state.code)

            // display message
            if (state.message.isNotEmpty()) {
                showMessage(state.message)
            }
        }
    }

    private fun checkConnectedWifi(door: Door, action: Action): Boolean {
        if (door.id == ignore_wifi_check_for_door_id) {
            return true
        } else {
            ignore_wifi_check_for_door_id = -1
        }

        if (!door.getWiFiRequired()) {
            return true
        }

        if (WifiTools.isConnected()) {
            val ssids = door.getWiFiSSIDs()
            val current_ssid = WifiTools.getCurrentSSID()
            if (ssids.isNotEmpty() && !WifiTools.matchSSID(ssids, current_ssid)) {
                builder.setTitle("Wrong WiFi")
                builder.setMessage("Connected to wrong network ('$current_ssid') - ignore?")
                builder.setPositiveButton("Yes") { dialog: DialogInterface, id: Int ->
                    ignore_wifi_check_for_door_id = door.id
                    // trigger again
                    callRequestHandler(action)
                    dialog.cancel()
                }
                builder.setNegativeButton("No") { dialog: DialogInterface, id: Int -> dialog.cancel() }
                builder.show()
                return false
            }
        } else {
            builder.setTitle("WiFi Disabled")
            builder.setMessage("WiFi disabled - ignore?")
            builder.setPositiveButton("Yes") { dialog: DialogInterface, id: Int ->
                ignore_wifi_check_for_door_id = door.id
                // trigger again
                callRequestHandler(action)
                dialog.cancel()
            }
            builder.setNegativeButton("No") { dialog: DialogInterface, id: Int -> dialog.cancel() }
            builder.show()
            return false
        }
        return true
    }

    private fun checkSshPassphrase(door: Door, action: Action): Boolean {
        if (door is SshDoor) {
            // check if passphrase is not needed
            if (!door.needsPassphrase() || !Utils.isEmpty(door.passphrase_tmp)) {
                return true
            }

            // try other passphrases
            for (s in Settings.getDoors()) {
                if (s is SshDoor) {
                    if (s.needsPassphrase() && s.passphrase_tmp.isNotEmpty()) {
                        if (SshRequestHandler.testPassphrase(door.keypair, s.passphrase_tmp)) {
                            showMessage("Reuse passphrase from ${door.name}")
                            door.passphrase_tmp = s.passphrase_tmp
                            return true
                        }
                    }
                }
            }

            // ask for passphrase
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_ssh_passphrase)

            val passphraseEditText = dialog.findViewById<EditText>(R.id.PassphraseEditText)
            val abortButton = dialog.findViewById<Button>(R.id.AbortButton)
            val okButton = dialog.findViewById<Button>(R.id.OkButton)
            okButton.setOnClickListener { v: View? ->
                val passphrase = passphraseEditText.text.toString()
                if (SshRequestHandler.testPassphrase(door.keypair, passphrase)) {
                    door.passphrase_tmp = passphrase
                    showMessage(R.string.passphrase_accepted)
                    callRequestHandler(action)
                } else {
                    showMessage(R.string.passphrase_invalid)
                }
                dialog.cancel()
            }
            abortButton.setOnClickListener { v: View? -> dialog.cancel() }
            dialog.show()
            return false
        }
        return true
    }

    private fun checkBluetoothScanPermissions(door: Door, action: Action): Boolean {
        if (door is BluetoothDoor || door is NukiDoor) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!Utils.hasBluetoothConnectPermission(this)) {
                    Utils.requestBluetoothConnectPermission(this, BLUETOOTH_CONNECT_REQUEST_CODE)
                    return false
                }
            }
        }

        return true
    }

    private fun callRequestHandler(action: Action) {
        val door = getSelectedDoor()
        if (door == null) {
            changeUI(StateCode.DISABLED)
            return
        }

        if (!checkConnectedWifi(door, action)) {
            changeUI(StateCode.DISABLED)
            return
        }

        if (!checkSshPassphrase(door, action)) {
            changeUI(StateCode.DISABLED)
            return
        }

        if (!checkBluetoothScanPermissions(door, action)) {
            changeUI(StateCode.DISABLED)
            return
        }

        if (door is HttpsDoor) {
            val handler = HttpsRequestHandler(this, door, action)
            handler.start()
        } else if (door is SshDoor) {
            val handler = SshRequestHandler(this, door, action)
            handler.start()
        } else if (door is BluetoothDoor) {
            val handler = BluetoothRequestHandler(this, door, action)
            handler.start()
        } else if (door is NukiDoor) {
            val handler = NukiRequestHandler(this, door, action)
            handler.start()
        } else if (door is MqttDoor) {
            val handler = MqttRequestHandler(this, door, action)
            handler.start()
        } else {
            // hm, invalid door
            changeUI(StateCode.DISABLED)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_CONNECT_REQUEST_CODE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showMessage("Permission granted - Please try again.")
            } else {
                showMessage("Bluetooth scan permissions required for using Bluetooth.")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        callRequestHandler(Action.FETCH_STATE)
    }

    // Show/Hide menu items
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        val editMenuItem = menu.findItem(R.id.action_edit)
        val showQrMenuItem = menu.findItem(R.id.action_show_qr)
        val cloneMenuItem = menu.findItem(R.id.action_clone)

        if (hasDoorSelected) {
            editMenuItem.isEnabled = true
            editMenuItem.icon!!.alpha = 255
            showQrMenuItem.isEnabled = true
            showQrMenuItem.icon!!.alpha = 255
            cloneMenuItem.isEnabled = true
            cloneMenuItem.icon!!.alpha = 255
        } else {
            editMenuItem.isEnabled = false
            editMenuItem.icon!!.alpha = 130
            showQrMenuItem.isEnabled = false
            showQrMenuItem.icon!!.alpha = 130
            cloneMenuItem.isEnabled = false
            cloneMenuItem.icon!!.alpha = 130
        }
        return true
    }

    /*
    private void connectNextWifi() {
        // collect app configured SSIDs
        ArrayList<Door> doors = Settings.getAllDoors();
        ArrayList<String> ssids = new ArrayList();

        for (door : doors) {
            ssids.addAll(splitCommaSeparated(door.getWiFiSSIDs()));
        }

        wifi.connectBestOf(ssids);
        updateSpinner();
    }
*/
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        if (id == R.id.action_edit) {
            val door_id = getSelectedDoorId()
            val i = Intent(this, SetupActivity::class.java)
            i.putExtra("door_id", door_id)
            startActivity(i)
            return true
        }
        if (id == R.id.action_new) {
            val i = Intent(this, SetupActivity::class.java)
            i.putExtra("door_id", -1)
            startActivity(i)
            return true
        }
        if (id == R.id.action_about) {
            val i = Intent(this, AboutActivity::class.java)
            startActivity(i)
            return true
        }
        if (id == R.id.action_scan_qr) {
            val door_id = getSelectedDoorId()
            val i = Intent(this, QRScanActivity::class.java)
            i.putExtra("door_id", door_id)
            startActivity(i)
            return true
        }
        if (id == R.id.action_show_qr) {
            val door_id = getSelectedDoorId()
            val i = Intent(this, QRShowActivity::class.java)
            i.putExtra("door_id", door_id)
            startActivity(i)
            return true
        }
        if (id == R.id.action_backup) {
            val i = Intent(this, BackupActivity::class.java)
            startActivity(i)
            return true
        }
        if (id == R.id.action_clone) {
            try {
                var door = getSelectedDoor()
                if (door != null) {
                    val obj = Settings.toJsonObject(door)
                    val new_id = Settings.getNewDoorIdentifier()
                    val new_name = Settings.getNewDoorName(door.name)
                    obj!!.put("id", new_id)
                    obj.put("name", new_name)
                    door = Settings.fromJsonObject(obj)
                    if (door != null) {
                        Settings.addDoor(door)
                    }
                    updateSpinner(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val BLUETOOTH_CONNECT_REQUEST_CODE = 0x01
    }
}
