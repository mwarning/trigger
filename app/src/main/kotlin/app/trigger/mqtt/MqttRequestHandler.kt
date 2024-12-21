package app.trigger.mqtt

import app.trigger.Utils.getSocketFactoryWithCertificateAndClientKey
import app.trigger.Utils.getSocketFactoryWithCertificate
import app.trigger.Utils.isEmpty
import app.trigger.Utils.rebuildAddress
import app.trigger.DoorReply.ReplyCode
import app.trigger.https.HttpsTools
import app.trigger.ssh.PubkeyUtils
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import app.trigger.*
import java.lang.Exception
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class MqttRequestHandler(private val listener: OnTaskCompleted, private val setup: MqttDoorSetup, private val action: MainActivity.Action) : Thread(), MqttCallback {
    override fun run() {
        if (setup.id < 0) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Internal Error")
            return
        }
        if (isEmpty(setup.server)) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "MQTT broker address not set.")
            return
        }
        if (action === MainActivity.Action.FETCH_STATE && setup.status_topic.isEmpty()) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "")
            return
        }
        if ((action === MainActivity.Action.OPEN_DOOR || action === MainActivity.Action.RING_DOOR || action === MainActivity.Action.CLOSE_DOOR)
                && setup.command_topic.isEmpty()) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "No command topic set.")
            return
        }
        if (action === MainActivity.Action.CLOSE_DOOR && setup.close_command.isEmpty()) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "No close command set.")
            return
        }
        if (setup.qos != 0 && setup.qos != 1 && setup.qos != 2) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Invalid QoS value: " + setup.qos)
            return
        }
        val clientId = MqttClient.generateClientId()
        val opts = MqttConnectOptions()
        val persistence = MemoryPersistence()
        var address = setup.server
        try {
            if (address.startsWith("mqtt://")) {
                address = address.replaceFirst("mqtt://".toRegex(), "tcp://")
                address = rebuildAddress(address, 1883)
            } else if (address.startsWith("mqtts://")) {
                address = address.replaceFirst("mqtts://".toRegex(), "ssl://")
                address = rebuildAddress(address, 8883)
            } else if (setup.server.startsWith("tcp://")) {
                address = rebuildAddress(address, 1883)
            } else if (setup.server.startsWith("ssl://")) {
                address = rebuildAddress(address, 8883)
            } else {
                throw Exception(
                        "Server address needs to start with 'mqtt://' or 'mqtts://'."
                )
            }
            if (!setup.username.isEmpty()) {
                opts.userName = setup.username
            }
            if (!setup.password.isEmpty()) {
                opts.password = setup.password.toCharArray()
            }

            // false: broker will not keep messages
            opts.isCleanSession = false
            if (address.startsWith("ssl://")) {
                opts.isHttpsHostnameVerificationEnabled = !setup.ignore_hostname_mismatch
                val client_keypair = setup.client_keypair
                val client_certificate = setup.client_certificate
                if (setup.server_certificate != null) {
                    // use given certificate only
                    if (client_keypair != null && client_certificate != null) {
                        val client_private_key = PubkeyUtils.decodePrivate(
                                client_keypair.privateKey, client_keypair.type
                        )
                        opts.socketFactory = getSocketFactoryWithCertificateAndClientKey(
                                setup.server_certificate, setup.client_certificate!!, client_private_key)
                    } else if (client_keypair == null && client_certificate == null) {
                        if (setup.ignore_certificate) {
                            // disable entire certificate validity
                            val context = SSLContext.getInstance("TLS")
                            context.init(null, arrayOf<X509TrustManager>(object : X509TrustManager {
                                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                                override fun getAcceptedIssuers(): Array<X509Certificate> {
                                    return arrayOf<X509Certificate>()
                                }
                            }), SecureRandom())
                            opts.socketFactory = context.socketFactory
                        } else if (setup.ignore_expiration) {
                            // ignore notBefore/notAfter
                            opts.socketFactory = HttpsTools.getSocketFactoryIgnoreCertificateExpiredException()
                        } else {
                            opts.socketFactory = getSocketFactoryWithCertificate(setup.server_certificate)
                        }
                    } else {
                        throw Exception("Both client key and client certificate needed.")
                    }
                } else {
                    if (client_keypair != null || client_certificate != null) {
                        throw Exception("Client key and client certificate needed.")
                    } else {
                        // use system default certificates
                        opts.socketFactory = SSLContext.getDefault().socketFactory
                    }
                }
            }
            val client = MqttClient(address, clientId, persistence)
            client.timeToWait = 3000 // 3 seconds
            client.setCallback(this)
            client.connect(opts)
            when (action) {
                MainActivity.Action.FETCH_STATE ->                     // subscribe
                    client.subscribe(setup.status_topic)
                MainActivity.Action.OPEN_DOOR -> {
                    // publish
                    val open = MqttMessage(setup.open_command.toByteArray())
                    open.isRetained = setup.retained
                    open.qos = setup.qos
                    client.publish(setup.command_topic, open)
                }
                MainActivity.Action.RING_DOOR -> {
                    // publish
                    val ring = MqttMessage(setup.ring_command.toByteArray())
                    ring.isRetained = setup.retained
                    ring.qos = setup.qos
                    client.publish(setup.command_topic, ring)
                }
                MainActivity.Action.CLOSE_DOOR -> {
                    // publish
                    val close = MqttMessage(setup.close_command.toByteArray())
                    close.isRetained = setup.retained
                    close.qos = setup.qos
                    client.publish(setup.command_topic, close)
                }
            }
            listener.onTaskResult(setup.id, ReplyCode.SUCCESS, "")
        } catch (me: MqttException) {
            //me.getMessage() returns "MqttException" only
            listener.onTaskResult(setup.id, ReplyCode.REMOTE_ERROR, me.toString())
        } catch (e: Exception) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, e.message!!)
        }
    }

    override fun connectionLost(cause: Throwable) {
        listener.onTaskResult(setup.id, ReplyCode.REMOTE_ERROR, cause.toString())
    }

    override fun messageArrived(topic: String, message: MqttMessage) {
        listener.onTaskResult(setup.id, ReplyCode.SUCCESS, message.toString())
    }

    override fun deliveryComplete(token: IMqttDeliveryToken) {
        // ignore
    }

    protected fun onPostExecute(result: DoorReply) {
        // already on GUI thread
        listener.onTaskResult(setup.id, result.code, result.message)
    }

    companion object {
        private const val TAG = "MqttRequestHandler"
    }
}