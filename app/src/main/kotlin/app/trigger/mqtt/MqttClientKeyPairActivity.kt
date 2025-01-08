package app.trigger.mqtt

import android.os.Bundle
import app.trigger.ssh.KeyPairBean
import app.trigger.AbstractClientKeyPairActivity
import app.trigger.MqttDoor
import app.trigger.SetupActivity


class MqttClientKeyPairActivity : AbstractClientKeyPairActivity() {
    private lateinit var mqttDoor: MqttDoor

    override fun getKeyPair(): KeyPairBean? {
        return mqttDoor.client_keypair
    }

    override fun setKeyPair(keyPair: KeyPairBean?) {
        mqttDoor.client_keypair = keyPair
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
}
