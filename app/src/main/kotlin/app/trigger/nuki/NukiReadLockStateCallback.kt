package app.trigger.nuki

import app.trigger.Utils.byteArrayToHexString
import app.trigger.Utils.hexStringToByteArray
import app.trigger.DoorReply.ReplyCode
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGatt
import app.trigger.*

internal class NukiReadLockStateCallback(door_id: Int, listener: OnTaskCompleted, setup: NukiDoor) : NukiCallback(door_id, listener, NukiCallback.Companion.KEYTURNER_SERVICE_UUID, NukiCallback.Companion.KEYTURNER_USDIO_XTERISTIC_UUID) {
    var auth_id: Long
    var shared_key: ByteArray
    var data: ByteArray = ByteArray(0)

    override fun onConnected(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "onConnected")
        val nr = NukiCommand.NukiRequest(0x0c)
        val request: ByteArray? = NukiRequestHandler.encrypt_message(shared_key, auth_id, nr.generate(), null)
        characteristic.value = request
        val ok = gatt.writeCharacteristic(characteristic)
        if (!ok) {
            Log.e(TAG, "initial writeCharacteristic failed")
            closeConnection(gatt)
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "onCharacteristicChanged, uiid: ${characteristic.uuid}: ${byteArrayToHexString(characteristic.value)}")
        data = if (data == null) {
            characteristic.value
        } else {
            NukiTools.concat(data, characteristic.value)
        }
        val message: ByteArray? = NukiRequestHandler.decrypt_message(shared_key, data)
        val m: NukiCommand? = NukiRequestHandler.parse(message)
        if (m == null) {
            Log.d(TAG, "NukiCommand is null")
            return
        } else {
            data = ByteArray(0)
        }

        if (m is NukiCommand.NukiStates) {
            val ns = m
            var extra = ""
            if (ns.battery_critical == 0x01) {
                extra = " (Battery Critical!)"
            }
            listener.onTaskResult(
                    door_id, ReplyCode.SUCCESS, NukiTools.getLockState(ns.lock_state) + extra
            )

            // do not wait until the Nuki closes the connection
            closeConnection(gatt)
        } else if (m is NukiCommand.NukiError) {
            listener.onTaskResult(door_id, ReplyCode.REMOTE_ERROR, m.asString())
            closeConnection(gatt)
        } else {
            Log.e(TAG, "Unhandled command.")
            closeConnection(gatt)
        }
    }

    companion object {
        private const val TAG = "ReadLockStateCallback"
    }

    init {
        shared_key = hexStringToByteArray(setup.shared_key)
        auth_id = setup.auth_id
    }
}