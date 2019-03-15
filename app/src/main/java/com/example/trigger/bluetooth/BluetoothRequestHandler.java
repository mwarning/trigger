package com.example.trigger.bluetooth;

import android.os.AsyncTask;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.util.Set;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import com.example.trigger.BluetoothDoorSetup;
import com.example.trigger.MainActivity.Action;
import com.example.trigger.DoorReply;
import com.example.trigger.DoorReply.ReplyCode;
import com.example.trigger.OnTaskCompleted;


public class BluetoothRequestHandler extends AsyncTask<Object, Void, DoorReply> {
    private OnTaskCompleted listener;

    public BluetoothRequestHandler(OnTaskCompleted listener) {
        this.listener = listener;
    }

    @Override
    protected DoorReply doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e("BluetoothRequestHandler.doInBackGround", "Unexpected number of params.");
            return DoorReply.internal_error();
        }

        if (!(params[0] instanceof Action && params[1] instanceof BluetoothDoorSetup)) {
            Log.e("BluetoothRequestHandler.doInBackground", "Invalid type of params.");
            return DoorReply.internal_error();
        }

        Action action = (Action) params[0];
        BluetoothDoorSetup setup = (BluetoothDoorSetup) params[1];

        if (setup.getId() < 0) {
            return DoorReply.internal_error();
        }

        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();

        try {
            // serial board port
            final UUID server_port = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            final String server_address = setup.server_address.toUpperCase();

            String request = "";
            String response = "";

            if (bluetooth == null) {
                return new DoorReply(ReplyCode.LOCAL_ERROR, "Device does not support bluetooth");
            } else if (!bluetooth.isEnabled()) {
                // request to enable
                //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return new DoorReply(ReplyCode.LOCAL_ERROR, "Bluetooth is disabled.");
            } else {
                String mydeviceaddress = bluetooth.getAddress();
                String mydevicename = bluetooth.getName();
                String state = "" + bluetooth.getState();
                Log.d("Bluetooth", "myself: " + mydevicename + " : " + mydeviceaddress + " " + state);
            }

            Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();

            if (pairedDevices.isEmpty()) {
                return new DoorReply(ReplyCode.LOCAL_ERROR, "No paired device found.");
            } else {
                for (BluetoothDevice device : pairedDevices) {
                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress();
                    Log.d("Bluetooth", "name: " + deviceName + ", mac: " + deviceHardwareAddress);
                    if (device.getAddress().equals(server_address)) {
                        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(server_port);
                        socket.connect();

                        switch (action) {
                            case open_door:
                                request = setup.open_query;
                                break;
                            case close_door:
                                request = setup.close_query;
                                break;
                            case update_state:
                                request = setup.status_query;
                                break;
                        }

                        if (!request.isEmpty()) {
                            OutputStream out = socket.getOutputStream();
                            out.write(request.getBytes());
                        }

                        InputStream in = socket.getInputStream();
                        int byteCount = in.available();

                        if (byteCount > 0 && byteCount < (10*1024)) {
                            byte[] bytes = new byte[byteCount];
                            in.read(bytes);
                            response = new String(bytes, "UTF-8");
                        }
                    }
                }
            }

            return new DoorReply(ReplyCode.SUCCESS, response);
        } catch (Exception e) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, e.toString());
        }
    }

    protected void onPostExecute(DoorReply result) {
        listener.onTaskCompleted(result);
    }
}
