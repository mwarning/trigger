package app.trigger.nuki;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;

import java.util.UUID;

import app.trigger.DoorReply.ReplyCode;
import app.trigger.Log;
import app.trigger.OnTaskCompleted;


abstract class NukiCallback extends BluetoothGattCallback {
    private static final String TAG = "NukiCallback";
    protected final OnTaskCompleted listener;
    private final UUID service_uuid;
    private final UUID characteristic_uuid;
    protected final int setup_id;

    // Client Characteristic Configuration Descriptor
    static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Pairing UUIDs
    static final UUID PAIRING_SERVICE_UUID = UUID.fromString("a92ee100-5501-11e4-916c-0800200c9a66");
    static final UUID PAIRING_GDIO_XTERISTIC_UUID = UUID.fromString("a92ee101-5501-11e4-916c-0800200c9a66");

    // Keyturner UUIDs
    static final UUID KEYTURNER_SERVICE_UUID = UUID.fromString("a92ee200-5501-11e4-916c-0800200c9a66");
    static final UUID KEYTURNER_GDIO_XTERISTIC_UUID = UUID.fromString("a92ee201-5501-11e4-916c-0800200c9a66");
    static final UUID KEYTURNER_USDIO_XTERISTIC_UUID = UUID.fromString("a92ee202-5501-11e4-916c-0800200c9a66");

    NukiCallback(int setup_id, OnTaskCompleted listener, UUID service_uuid, UUID characteristic_uuid) {
        this.setup_id = setup_id;
        this.listener = listener;
        this.service_uuid = service_uuid;
        this.characteristic_uuid = characteristic_uuid;
    }

    /*
     * This is mostly called to end the connection quickly instead
     * of waiting for the other side to close the connection
    */
    protected void closeConnection(BluetoothGatt gatt) {
        gatt.close();
        NukiRequestHandler.bluetooth_in_use.set(false);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        //Log.i(TAG, "status: " + getGattStatus(status) + ", newState: " + newState);

        if (status == GATT_SUCCESS) {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    break:
                case BluetoothProfile.STATE_DISCONNECTED:
                case BluetoothProfile.STATE_DISCONNECTING:
                default:
                    closeConnection(gatt);
                    break;
            }
        } else {
            closeConnection(gatt);
            this.listener.onTaskResult(
                setup_id, ReplyCode.REMOTE_ERROR, "Connection error: " + NukiRequestHandler.getGattStatus(status)
            );
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == GATT_SUCCESS) {
            BluetoothGattService service = gatt.getService(this.service_uuid);
            if (service == null) {
                closeConnection(gatt);
                this.listener.onTaskResult(
                    setup_id, ReplyCode.REMOTE_ERROR, "Service not found: " + this.service_uuid
                );
                return;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(this.characteristic_uuid);
            if (characteristic == null) {
                closeConnection(gatt);
                this.listener.onTaskResult(
                    setup_id, ReplyCode.REMOTE_ERROR, "Characteristic not found: " + this.characteristic_uuid
                );
                return;
            }

            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID);
            if (descriptor == null) {
                closeConnection(gatt);
                this.listener.onTaskResult(
                    setup_id, ReplyCode.REMOTE_ERROR, "Descriptor not found: " + CCC_DESCRIPTOR_UUID
                );
                return;
            }
           
            //Log.i(TAG, "characteristic properties: " + NukiTools.getProperties(characteristic));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            boolean ok = gatt.writeDescriptor(descriptor);
            if (!ok) {
                Log.e(TAG, "descriptor write failed");
                closeConnection(gatt);
            }
        } else {
            closeConnection(gatt);
            this.listener.onTaskResult(
                setup_id, ReplyCode.LOCAL_ERROR, "Client not found: " + NukiRequestHandler.getGattStatus(status)
            );
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        //Log.i(TAG, "uiid: " + descriptor.getUuid() + ": " + Utils.byteArrayToHexString(descriptor.getValue()));
        if (status == GATT_SUCCESS) {
            onConnected(gatt, descriptor.getCharacteristic());
        } else {
            closeConnection(gatt);
            Log.e(TAG, "failed to write to client: " + NukiRequestHandler.getGattStatus(status));
        }
    }

    public abstract void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
}
