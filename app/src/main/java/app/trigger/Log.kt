package app.trigger

import android.util.Log

/*
* Wrapper for android.util.Log to disable logging
*/
object Log {
    private fun contextString(context: Any): String {
        return if (context is String) {
            context
        } else {
            context.javaClass.simpleName
        }
    }

    @kotlin.jvm.JvmStatic
    fun d(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.d(tag, message)
        }
    }

    @kotlin.jvm.JvmStatic
    fun w(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.w(tag, message)
        }
    }

    @kotlin.jvm.JvmStatic
    fun i(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.i(tag, message)
        }
    }

    @kotlin.jvm.JvmStatic
    fun e(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.e(tag, message)
        }
    }
}
