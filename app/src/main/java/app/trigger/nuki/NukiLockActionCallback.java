package app.trigger.nuki;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import app.trigger.NukiDoorSetup;
import app.trigger.OnTaskCompleted;
import app.trigger.DoorReply.ReplyCode;
import app.trigger.Utils;
import app.trigger.Log;


class NukiLockActionCallback extends NukiCallback {
    private static final String TAG = "LockActionCallback";
    long auth_id;
    long app_id;
    int lock_action;
    byte[] shared_key;
    byte[] data;

    NukiLockActionCallback(int setup_id, OnTaskCompleted listener, NukiDoorSetup setup, int lock_action) {
        super(setup_id, listener, KEYTURNER_SERVICE_UUID, KEYTURNER_USDIO_XTERISTIC_UUID);
        this.shared_key = Utils.hexStringToByteArray(setup.shared_key);
        this.auth_id = setup.auth_id;
        this.app_id = setup.app_id;
        this.lock_action = lock_action;
    }

    public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "onConnected");
        NukiCommand.NukiRequest nr = new NukiCommand.NukiRequest(0x04);
        byte[] request = NukiRequestHandler.encrypt_message(this.shared_key, this.auth_id, nr.generate(), null);
        characteristic.setValue(request);
        boolean ok = gatt.writeCharacteristic(characteristic);
        if (!ok) {
            Log.e(TAG, "initial writeCharacteristic failed");
            closeConnection(gatt);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "onCharacteristicChanged, uiid: " + characteristic.getUuid() + ": " + Utils.byteArrayToHexString(characteristic.getValue()));
        if (data == null) {
            data = characteristic.getValue();
        } else {
            data = NukiTools.concat(data, characteristic.getValue());
        }

        byte[] message = NukiRequestHandler.decrypt_message(shared_key, data);
        NukiCommand command = NukiRequestHandler.parse(message);

        if (command == null) {
            Log.d(TAG, "NukiCommand is null");
            return;
        } else {
            data = null;
        }

        if (command instanceof NukiCommand.NukiChallenge) {
            Log.d(TAG, "NukiCommand.NukiChallenge");
            NukiCommand.NukiChallenge nc = (NukiCommand.NukiChallenge) command;
            NukiCommand.NukiLockAction nla = new NukiCommand.NukiLockAction(this.lock_action, this.app_id, 0x00, nc.nonce);
            byte[] response = NukiRequestHandler.encrypt_message(this.shared_key, this.auth_id, nla.generate(), null);

            characteristic.setValue(response);
            boolean ok = gatt.writeCharacteristic(characteristic);
            if (!ok) {
                Log.e(TAG, "writeCharacteristic failed for NukiLockAction");
                closeConnection(gatt);
            }
        } else if (command instanceof NukiCommand.NukiStatus) {
            Log.d(TAG, "NukiCommand.NukiStatus");
            NukiCommand.NukiStatus ns = (NukiCommand.NukiStatus) command;
            if (ns.status == NukiCommand.NukiStatus.STATUS_COMPLETE) {
                // do not wait until the Nuki closes the connection
                closeConnection(gatt);
            }
        } else if (command instanceof NukiCommand.NukiStates) {
            Log.d(TAG, "NukiCommand.NukiStates");
            NukiCommand.NukiStates ns = (NukiCommand.NukiStates) command;
            String extra = "";
            if (ns.battery_critical == 0x01) {
                extra = " (Battery Critical!)";
            }
            listener.onTaskResult(setup_id, ReplyCode.SUCCESS, NukiTools.getLockState(ns.lock_state) + extra);
        } else if (command instanceof NukiCommand.NukiError) {
            Log.d(TAG, "NukiCommand.NukiError");
            NukiCommand.NukiError ne = (NukiCommand.NukiError) command;
            this.listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, ne.asString());
            closeConnection(gatt);
        } else {
            Log.e(TAG, "Unhandled command");
            closeConnection(gatt);
        }
    }
}
