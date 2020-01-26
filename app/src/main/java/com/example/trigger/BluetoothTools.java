package com.example.trigger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.example.trigger.Log;


public class BluetoothTools {
    static final String TAG = "BluetoothTools";
    private BluetoothAdapter bluetoothAdapter;

    BluetoothTools(Context context) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
        }
	}

    public boolean isEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public static BluetoothSocket createRfcommSocket(BluetoothDevice device) {
        BluetoothSocket tmp = null;
        try {
            Class class1 = device.getClass();
            Class aclass[] = new Class[1];
            aclass[0] = Integer.TYPE;
            Method method = class1.getMethod("createRfcommSocket", aclass);
            Object aobj[] = new Object[1];
            aobj[0] = Integer.valueOf(1);

            tmp = (BluetoothSocket) method.invoke(device, aobj);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            Log.e(TAG, "createRfcommSocket() failed " + e);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            Log.e(TAG, "createRfcommSocket() failed " + e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e(TAG, "createRfcommSocket() failed " + e);
        }
        return tmp;
    }
}
