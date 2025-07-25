/*
* Copyright (C) 2025 The Trigger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

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

    fun d(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.d(tag, message)
        }
    }

    fun w(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.w(tag, message)
        }
    }

    fun i(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.i(tag, message)
        }
    }

    fun e(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.e(tag, message)
        }
    }
}
