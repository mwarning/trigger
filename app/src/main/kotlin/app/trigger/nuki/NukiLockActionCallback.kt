package app.trigger.nuki

import app.trigger.Utils.byteArrayToHexString
import app.trigger.Utils.hexStringToByteArray
import app.trigger.DoorReply.ReplyCode
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGatt
import app.trigger.nuki.NukiCommand.NukiRequest
import app.trigger.nuki.NukiCommand.NukiChallenge
import app.trigger.nuki.NukiCommand.NukiStates
import app.trigger.nuki.NukiCommand.NukiStatus
import app.trigger.nuki.NukiCommand.NukiError
import app.trigger.nuki.NukiCommand.NukiLockAction
import app.trigger.*

internal class NukiLockActionCallback(door_id: Int, action: MainActivity.Action, listener: OnTaskCompleted, setup: NukiDoor, lock_action: Int)
        : NukiCallback(door_id, action, listener, KEYTURNER_SERVICE_UUID, KEYTURNER_USDIO_XTERISTIC_UUID) {
    var auth_id: Long
    var app_id: Long
    var lock_action: Int
    var shared_key: ByteArray
    var data = ByteArray(0)

    override fun onConnected(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "onConnected")
        val nr = NukiRequest(0x04)
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
        data = if (data.isEmpty()) {
            characteristic.value
        } else {
            NukiTools.concat(data, characteristic.value)
        }
        val message: ByteArray? = NukiRequestHandler.decrypt_message(shared_key, data)
        val command: NukiCommand? = NukiRequestHandler.parse(message)
        if (command == null) {
            Log.d(TAG, "NukiCommand is null")
            return
        } else {
            data = ByteArray(0)
        }
        if (command is NukiChallenge) {
            Log.d(TAG, "NukiCommand.NukiChallenge")
            val nla = NukiLockAction(lock_action, app_id, 0x00, command.nonce)
            val response: ByteArray? = NukiRequestHandler.encrypt_message(shared_key, auth_id, nla.generate(), null)
            characteristic.value = response
            val ok = gatt.writeCharacteristic(characteristic)
            if (!ok) {
                Log.e(TAG, "writeCharacteristic failed for NukiLockAction")
                closeConnection(gatt)
            }
        } else if (command is NukiStatus) {
            Log.d(TAG, "NukiCommand.NukiStatus")
            if (command.status == NukiStatus.STATUS_COMPLETE) {
                // do not wait until the Nuki closes the connection
                closeConnection(gatt)
            }
        } else if (command is NukiStates) {
            Log.d(TAG, "NukiCommand.NukiStates")
            val ns = command
            var extra = ""
            if (ns.battery_critical == 0x01) {
                extra = " (Battery Critical!)"
            }
            listener.onTaskResult(door_id, action, ReplyCode.SUCCESS, NukiTools.getLockState(ns.lock_state) + extra)
        } else if (command is NukiError) {
            Log.d(TAG, "NukiCommand.NukiError")
            listener.onTaskResult(door_id, action, ReplyCode.REMOTE_ERROR, command.asString())
            closeConnection(gatt)
        } else {
            Log.e(TAG, "Unhandled command")
            closeConnection(gatt)
        }
    }

    companion object {
        private const val TAG = "LockActionCallback"
    }

    init {
        shared_key = hexStringToByteArray(setup.shared_key)
        auth_id = setup.auth_id
        app_id = setup.app_id
        this.lock_action = lock_action
    }
}