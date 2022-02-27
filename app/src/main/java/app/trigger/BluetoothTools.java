package app.trigger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class BluetoothTools {
    private static final String TAG = "BluetoothTools";
    private static BluetoothAdapter adapter;

    static void init(Context context) {
        adapter = BluetoothAdapter.getDefaultAdapter();
	}

    public static boolean isEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    public static boolean isSupported() {
        return (adapter != null);
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
