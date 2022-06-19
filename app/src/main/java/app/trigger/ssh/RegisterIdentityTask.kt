package app.trigger.ssh

import app.trigger.Utils.readInputStreamWithTimeout
import app.trigger.Utils.rebuildAddress
import app.trigger.Utils.createSocketAddress
import java.io.DataOutputStream
import java.lang.Exception
import java.net.Socket

internal class RegisterIdentityTask(private val listener: OnTaskCompleted, private val address: String, private val keypair: KeyPairBean) : Thread() {
    interface OnTaskCompleted {
        fun onRegisterIdentityTaskCompleted(message: String?)
    }

    override fun run() {
        try {
            val addr = createSocketAddress(
                    rebuildAddress(address, 0)
            )
            if (addr.port == 0) {
                listener.onRegisterIdentityTaskCompleted("Missing port, use <address>:<port>")
                return
            }
            val client = Socket(addr.address, addr.port)
            val os = client.getOutputStream()
            val `is` = client.getInputStream()
            val writer = DataOutputStream(os)

            // send public key in PEM format
            os.write(keypair.openSSHPublicKey!!.toByteArray())
            os.flush()
            val reply = readInputStreamWithTimeout(`is`, 1024, 1000)
            client.close()
            if (reply.isNotEmpty()) {
                listener.onRegisterIdentityTaskCompleted(reply)
            } else {
                listener.onRegisterIdentityTaskCompleted("Done")
            }
        } catch (e: Exception) {
            listener.onRegisterIdentityTaskCompleted(e.toString())
        }
    }
}