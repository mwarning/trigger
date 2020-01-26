package com.example.trigger.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.Set;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import com.example.trigger.BluetoothDoorSetup;
import com.example.trigger.BluetoothTools;
import com.example.trigger.MainActivity.Action;
import com.example.trigger.DoorReply;
import com.example.trigger.DoorReply.ReplyCode;
import com.example.trigger.OnTaskCompleted;
import com.example.trigger.RequestHandler;
import com.example.trigger.Log;


public class BluetoothRequestHandler extends RequestHandler {
    private OnTaskCompleted listener;
    private BluetoothSocket socket;

    public BluetoothRequestHandler(OnTaskCompleted listener) {
        this.listener = listener;
    }

    @Override
    protected DoorReply doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e(this, "Unexpected number of params.");
            return DoorReply.internal_error();
        }

        if (!(params[0] instanceof Action && params[1] instanceof BluetoothDoorSetup)) {
            Log.e(this, "Invalid type of params.");
            return DoorReply.internal_error();
        }

        Action action = (Action) params[0];
        BluetoothDoorSetup setup = (BluetoothDoorSetup) params[1];

        if (setup.getId() < 0) {
            return DoorReply.internal_error();
        }

        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();

        if (bluetooth == null) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Device does not support bluetooth");
        } else if (!bluetooth.isEnabled()) {
            // request to enable
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Bluetooth is disabled.");
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
            return new DoorReply(ReplyCode.LOCAL_ERROR, "");
        }

        try {
            Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();

            String address = "";
            for (BluetoothDevice device : pairedDevices) {
                if ((device.getName() != null && device.getName().equals(setup.device_name))
					|| device.getAddress().equals(setup.device_name.toUpperCase())) {
                    address = device.getAddress();
                }
            }

            if (address.isEmpty()) {
                return new DoorReply(ReplyCode.LOCAL_ERROR, "Device not paired yet.");
            }

            BluetoothDevice device = bluetooth.getRemoteDevice(address);

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
                return new DoorReply(ReplyCode.REMOTE_ERROR, "Cannot reach remote device.");
            }

            socket.close();

            return new DoorReply(ReplyCode.SUCCESS, response);
        } catch (Exception e) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, e.toString());
        }
    }

    @Override
    protected void stop() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    protected void onPostExecute(DoorReply result) {
        listener.onTaskCompleted(result);
    }
}
