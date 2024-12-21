package app.trigger.bluetooth

import app.trigger.BluetoothTools.createRfcommSocket
import android.bluetooth.BluetoothSocket
import app.trigger.DoorReply.ReplyCode
import android.bluetooth.BluetoothAdapter
import app.trigger.*
import java.io.IOException
import java.lang.Exception
import java.util.*


class BluetoothRequestHandler(private val listener: OnTaskCompleted, private val setup: BluetoothDoorSetup, private val action: MainActivity.Action) : Thread() {
    private var socket: BluetoothSocket? = null
    override fun run() {
        if (setup.id < 0) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Internal Error")
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            listener.onTaskResult(setup.id, ReplyCode.DISABLED, "Device does not support Bluetooth")
            return
        }

        if (!adapter.isEnabled) {
            // request to enable
            listener.onTaskResult(setup.id, ReplyCode.DISABLED, "Bluetooth is disabled.")
            return
        }

        val request = when (action) {
            MainActivity.Action.OPEN_DOOR -> setup.open_query
            MainActivity.Action.RING_DOOR -> setup.ring_query
            MainActivity.Action.CLOSE_DOOR -> setup.close_query
            MainActivity.Action.FETCH_STATE -> setup.status_query
        }
        if (request.isEmpty()) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "")
            return
        }
        try {
            val pairedDevices = adapter.bondedDevices
            var address = ""
            for (device in pairedDevices) {
                if (device.name != null && device.name == setup.device_name
                        || device.address == setup.device_name.toUpperCase()) {
                    address = device.address
                }
            }
            if (address.isEmpty()) {
                listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Device not paired yet.")
                return
            }
            val device = adapter.getRemoteDevice(address)
            socket = if (setup.service_uuid.isEmpty()) {
                createRfcommSocket(device)
            } else {
                val uuid = UUID.fromString(setup.service_uuid)
                device.createRfcommSocketToServiceRecord(uuid)
            }
            socket!!.connect()

            // Get the BluetoothSocket input and output streams
            val tmpIn = socket!!.inputStream
            val tmpOut = socket!!.outputStream
            tmpOut.write(request.toByteArray())
            tmpOut.flush()
            val response = try {
                val buffer = ByteArray(512)
                val bytes = tmpIn.read(buffer)
                String(buffer, 0, bytes)
            } catch (ioe: IOException) {
                listener.onTaskResult(setup.id, ReplyCode.REMOTE_ERROR, "Cannot reach remote device.")
                return
            }
            socket!!.close()
            listener.onTaskResult(setup.id, ReplyCode.SUCCESS, response)
        } catch (e: Exception) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, e.toString())
        }
    }
}