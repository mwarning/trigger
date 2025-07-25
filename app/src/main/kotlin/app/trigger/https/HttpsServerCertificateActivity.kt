/*
* Copyright (C) 2025 The Trigger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package app.trigger.https

import android.os.Bundle
import app.trigger.AbstractCertificateActivity
import app.trigger.Door
import app.trigger.HttpsDoor
import app.trigger.SetupActivity
import java.security.cert.Certificate

class HttpsServerCertificateActivity : AbstractCertificateActivity() {
    private lateinit var httpsDoor: HttpsDoor

    override fun getDoor(): Door {
        return httpsDoor
    }

    override fun getCertificate(): Certificate? {
        return httpsDoor.server_certificate
    }

    override fun setCertificate(certificate: Certificate?) {
        httpsDoor.server_certificate = certificate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (SetupActivity.currentDoor is HttpsDoor) {
            httpsDoor = SetupActivity.currentDoor as HttpsDoor
        } else {
            // not expected to happen
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }

    companion object {
        private const val TAG = "HttpsServerCertificateActivity"
    }
}