package app.trigger.https

import android.os.Bundle
import app.trigger.AbstractCertificateActivity
import app.trigger.Door
import app.trigger.HttpsDoor
import app.trigger.SetupActivity
import java.security.cert.Certificate

class HttpsClientCertificateActivity : AbstractCertificateActivity() {
    private lateinit var httpsDoor: HttpsDoor

    override fun getDoor(): Door {
        return httpsDoor
    }

    override fun getCertificate(): Certificate? {
        return httpsDoor.client_certificate
    }

    override fun setCertificate(certificate: Certificate?) {
        httpsDoor.client_certificate = certificate
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
        private const val TAG = "HttpsClientCertificateActivity"
    }
}