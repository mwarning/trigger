package app.trigger.mqtt

import android.os.Bundle
import app.trigger.AbstractCertificateActivity
import app.trigger.Door
import app.trigger.MqttDoor
import app.trigger.SetupActivity
import java.security.cert.Certificate

class MqttClientCertificateActivity : AbstractCertificateActivity() {
    private lateinit var mqttDoor: MqttDoor

    override fun getDoor(): Door {
        return mqttDoor
    }

    override fun getCertificate(): Certificate? {
        return mqttDoor.client_certificate
    }

    override fun setCertificate(certificate: Certificate?) {
        mqttDoor.client_certificate = certificate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (SetupActivity.currentDoor is MqttDoor) {
            mqttDoor = SetupActivity.currentDoor as MqttDoor
        } else {
            // not expected to happen
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }

    companion object {
        private const val TAG = "MqttClientCertificateActivity"
    }
}