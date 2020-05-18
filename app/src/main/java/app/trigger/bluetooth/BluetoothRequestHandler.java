package app.trigger.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.Set;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import app.trigger.BluetoothDoorSetup;
import app.trigger.BluetoothTools;
import app.trigger.MainActivity.Action;
import app.trigger.DoorReply.ReplyCode;
import app.trigger.OnTaskCompleted;
import app.trigger.Log;


public class BluetoothRequestHandler extends Thread {
    private final OnTaskCompleted listener;
    private final BluetoothDoorSetup setup;
    private final Action action;
    private BluetoothSocket socket;

    public BluetoothRequestHandler(OnTaskCompleted listener, BluetoothDoorSetup setup, Action action) {
        this.listener = listener;
        this.setup = setup;
        this.action = action;
    }

    public void run() {
        if (setup.getId() < 0) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Internal Error");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.DISABLED, "Device does not support Bluetooth");
            return;
        }

        if (!adapter.isEnabled()) {
            // request to enable
            this.listener.onTaskResult(setup.getId(), ReplyCode.DISABLED, "Bluetooth is disabled.");
            return;
        }

        String request = "";
        String response = "";

        switch (action) {
            case open_door:
                request = setup.open_query;
                break;
            case ring_door:
                request = setup.ring_query;
                break;
            case close_door:
                request = setup.close_query;
                break;
            case fetch_state:
                request = setup.status_query;
                break;
        }

        if (request.isEmpty()) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "");
            return;
        }

        try {
            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

            String address = "";
            for (BluetoothDevice device : pairedDevices) {
                if ((device.getName() != null && device.getName().equals(setup.device_name))
					|| device.getAddress().equals(setup.device_name.toUpperCase())) {
                    address = device.getAddress();
                }
            }

            if (address.isEmpty()) {
                this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Device not paired yet.");
                return;
            }

            BluetoothDevice device = adapter.getRemoteDevice(address);

            if (setup.service_uuid.isEmpty()) {
                socket = BluetoothTools.createRfcommSocket(device);
            } else {
                UUID uuid = UUID.fromString(setup.service_uuid);
                socket = device.createRfcommSocketToServiceRecord(uuid);
            }

            socket.connect();

            // Get the BluetoothSocket input and output streams
            InputStream tmpIn = socket.getInputStream();
            OutputStream tmpOut = socket.getOutputStream();

            tmpOut.write(request.getBytes());
            tmpOut.flush();

            try {
                byte[] buffer = new byte[512];
                int bytes = tmpIn.read(buffer);
                response = new String(buffer, 0, bytes);
            } catch (IOException ioe) {
                this.listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, "Cannot reach remote device.");
                return;
            }

            socket.close();

            this.listener.onTaskResult(setup.getId(), ReplyCode.SUCCESS, response);
        } catch (Exception e) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, e.toString());
        }
    }
}
