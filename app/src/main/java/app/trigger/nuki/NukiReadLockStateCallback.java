package app.trigger.nuki;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import app.trigger.DoorReply.ReplyCode;
import app.trigger.Log;
import app.trigger.NukiDoorSetup;
import app.trigger.OnTaskCompleted;
import app.trigger.Utils;


class NukiReadLockStateCallback extends NukiCallback {
    private static final String TAG = "ReadLockStateCallback";
    long auth_id;
    byte[] shared_key;
    byte[] data;

    NukiReadLockStateCallback(int setup_id, OnTaskCompleted listener, NukiDoorSetup setup) {
        super(setup_id, listener, KEYTURNER_SERVICE_UUID, KEYTURNER_USDIO_XTERISTIC_UUID);
        this.shared_key = Utils.hexStringToByteArray(setup.shared_key);
        this.auth_id = setup.auth_id;
    }

    public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "onConnected");
        NukiCommand.NukiRequest nr = new NukiCommand.NukiRequest(0x0c);
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
        NukiCommand m = NukiRequestHandler.parse(message);

        if (m == null) {
            Log.d(TAG, "NukiCommand is null");
            return;
        } else {
            data = null;
        }

        if (m instanceof NukiCommand.NukiStates) {
            NukiCommand.NukiStates ns = (NukiCommand.NukiStates) m;

            String extra = "";
            if (ns.battery_critical == 0x01) {
                extra = " (Battery Critical!)";
            }

            listener.onTaskResult(
                setup_id, ReplyCode.SUCCESS, NukiTools.getLockState(ns.lock_state) + extra
            );

            // do not wait until the Nuki closes the connection
            closeConnection(gatt);
        } else if (m instanceof NukiCommand.NukiError) {
            NukiCommand.NukiError ns = (NukiCommand.NukiError) m;
            this.listener.onTaskResult(setup_id, ReplyCode.REMOTE_ERROR, ns.asString());
            closeConnection(gatt);
        } else {
            Log.e(TAG, "Unhandled command.");
            closeConnection(gatt);
        }
    }
}
