package app.trigger

import android.bluetooth.BluetoothAdapter
import app.trigger.BluetoothTools
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import java.lang.reflect.InvocationTargetException


object BluetoothTools {
    private const val TAG = "BluetoothTools"
    private var adapter: BluetoothAdapter? = null

    fun init(context: Context?) {
        adapter = BluetoothAdapter.getDefaultAdapter()
    }

    fun isEnabled(): Boolean {
        return adapter != null && adapter!!.isEnabled
    }

    fun isSupported(): Boolean {
        return adapter != null
    }

    fun createRfcommSocket(device: BluetoothDevice): BluetoothSocket? {
        var tmp: BluetoothSocket? = null
        try {
            val class1: Class<*> = device.javaClass
            val aclass: Array<Class<*>> =  arrayOf(Integer.TYPE as Class<*>)
            val method = class1.getMethod("createRfcommSocket", *aclass)
            val aobj = arrayOfNulls<Any>(1)
            aobj[0] = Integer.valueOf(1)
            tmp = method.invoke(device, *aobj) as BluetoothSocket
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
            Log.e(TAG, "createRfcommSocket() failed $e")
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
            Log.e(TAG, "createRfcommSocket() failed $e")
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
            Log.e(TAG, "createRfcommSocket() failed $e")
        }
        return tmp
    }
}
