/*
* Copyright (C) 2025 The Trigger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package app.trigger.https

import android.os.AsyncTask
import app.trigger.Log
import java.lang.Exception
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class CertificateFetchTask(private val listener: OnTaskCompleted) : AsyncTask<Any?, Void?, CertificateFetchTask.Result>() {
    interface OnTaskCompleted {
        fun onCertificateFetchTaskCompleted(result: Result)
    }

    class Result internal constructor(var certificate: Certificate?, var error: String)

    override fun doInBackground(vararg params: Any?): Result {
        if (params.size != 1) {
            Log.e(TAG, "Unexpected number of params.")
            return Result(null, "Internal Error")
        }
        return try {
            var url = URL(params[0] as String)

            // try to establish TLS session only
            val port = if (url.port > 0) url.port else url.defaultPort
            url = URL("https", url.host, port, "")

            // disable all certification checks
            HttpsTools.disableDefaultHostnameVerifier()
            HttpsTools.disableDefaultCertificateValidation()
            val con = url.openConnection() as HttpsURLConnection
            con.connectTimeout = 2000
            con.connect()
            val certificates = con.serverCertificates
            con.disconnect()
            if (certificates.size == 0) {
                Result(null, "No certificate found.")
            } else {
                Result(certificates[0], "")
            }
        } catch (e: Exception) {
            Result(null, e.toString())
        }
    }

    override fun onPostExecute(result: Result) {
        listener.onCertificateFetchTaskCompleted(result)
    }

    companion object {
        const val TAG = "CertificateFetchTask"
    }
}