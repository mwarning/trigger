package app.trigger.https

import android.os.Bundle
import app.trigger.ssh.KeyPairBean
import app.trigger.AbstractClientKeyPairActivity
import app.trigger.HttpsDoor
import app.trigger.SetupActivity


class HttpsClientKeyPairActivity : AbstractClientKeyPairActivity() {
    private lateinit var httpsDoor: HttpsDoor

    override fun getKeyPair(): KeyPairBean? {
        return httpsDoor.client_keypair
    }

    override fun setKeyPair(keyPair: KeyPairBean?) {
        httpsDoor.client_keypair = keyPair
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_mqtt_client_keypair)

        if (SetupActivity.currentDoor is HttpsDoor) {
            httpsDoor = SetupActivity.currentDoor as HttpsDoor
        } else {
            // not expected to happen
            finish()
            return
        }
    }
}