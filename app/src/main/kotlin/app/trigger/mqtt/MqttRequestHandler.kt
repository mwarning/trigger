package app.trigger.mqtt

import app.trigger.Utils.getSocketFactoryWithCertificateAndClientKey
import app.trigger.Utils.getSocketFactoryWithCertificate
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

class MqttRequestHandler(private val listener: OnTaskCompleted, private val door: MqttDoor, private val action: MainActivity.Action)
        : Thread(), MqttCallback {
    override fun run() {
        if (door.id < 0) {
            listener.onTaskResult(door.id, action, ReplyCode.LOCAL_ERROR, "Internal Error")
            return
        }
        if (door.server.isEmpty()) {
            listener.onTaskResult(door.id, action, ReplyCode.LOCAL_ERROR, "MQTT broker address not set.")
            return
        }
        if (action === MainActivity.Action.FETCH_STATE && door.status_topic.isEmpty()) {
            listener.onTaskResult(door.id, action, ReplyCode.LOCAL_ERROR, "")
            return
        }
        if ((action === MainActivity.Action.OPEN_DOOR || action === MainActivity.Action.RING_DOOR || action === MainActivity.Action.CLOSE_DOOR)
                && door.command_topic.isEmpty()) {
            listener.onTaskResult(door.id, action, ReplyCode.LOCAL_ERROR, "No command topic set.")
            return
        }
        if (action === MainActivity.Action.CLOSE_DOOR && door.close_command.isEmpty()) {
            listener.onTaskResult(door.id, action, ReplyCode.LOCAL_ERROR, "No close command set.")
            return
        }
        if (door.qos != 0 && door.qos != 1 && door.qos != 2) {
            listener.onTaskResult(door.id, action, ReplyCode.LOCAL_ERROR, "Invalid QoS value: ${door.qos}")
            return
        }
        val clientId = MqttClient.generateClientId()
        val opts = MqttConnectOptions()
        val persistence = MemoryPersistence()
        var address = door.server
        try {
            if (address.startsWith("mqtt://")) {
                address = address.replaceFirst("mqtt://".toRegex(), "tcp://")
                address = rebuildAddress(address, 1883)
            } else if (address.startsWith("mqtts://")) {
                address = address.replaceFirst("mqtts://".toRegex(), "ssl://")
                address = rebuildAddress(address, 8883)
            } else if (door.server.startsWith("tcp://")) {
                address = rebuildAddress(address, 1883)
            } else if (door.server.startsWith("ssl://")) {
                address = rebuildAddress(address, 8883)
            } else {
                throw Exception(
                        "Server address needs to start with 'mqtt://' or 'mqtts://'."
                )
            }
            if (door.username.isNotEmpty()) {
                opts.userName = door.username
            }
            if (door.password.isNotEmpty()) {
                opts.password = door.password.toCharArray()
            }

            // false: broker will not keep messages
            opts.isCleanSession = false
            if (address.startsWith("ssl://")) {
                opts.isHttpsHostnameVerificationEnabled = !door.ignore_hostname_mismatch
                val client_keypair = door.client_keypair
                val client_certificate = door.client_certificate
                if (door.server_certificate != null) {
                    // use given certificate only
                    if (client_keypair != null && client_certificate != null) {
                        val client_private_key = PubkeyUtils.decodePrivate(
                                client_keypair.privateKey, client_keypair.type
                        )
                        opts.socketFactory = getSocketFactoryWithCertificateAndClientKey(
                                door.server_certificate, door.client_certificate!!, client_private_key)
                    } else if (client_keypair == null && client_certificate == null) {
                        if (door.ignore_certificate) {
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
                        } else if (door.ignore_expiration) {
                            // ignore notBefore/notAfter
                            opts.socketFactory = HttpsTools.getSocketFactoryIgnoreCertificateExpiredException()
                        } else {
                            opts.socketFactory = getSocketFactoryWithCertificate(door.server_certificate)
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
                    client.subscribe(door.status_topic)
                MainActivity.Action.OPEN_DOOR -> {
                    // publish
                    val message = MqttMessage(door.open_command.toByteArray())
                    message.isRetained = door.retained
                    message.qos = door.qos
                    client.publish(door.command_topic, message)
                }
                MainActivity.Action.RING_DOOR -> {
                    // publish
                    val message = MqttMessage(door.ring_command.toByteArray())
                    message.isRetained = door.retained
                    message.qos = door.qos
                    client.publish(door.command_topic, message)
                }
                MainActivity.Action.CLOSE_DOOR -> {
                    // publish
                    val message = MqttMessage(door.close_command.toByteArray())
                    message.isRetained = door.retained
                    message.qos = door.qos
                    client.publish(door.command_topic, message)
                }
            }
            listener.onTaskResult(door.id, action, ReplyCode.SUCCESS, "")
        } catch (me: MqttException) {
            //me.getMessage() returns "MqttException" only
            listener.onTaskResult(door.id, action, ReplyCode.REMOTE_ERROR, me.toString())
        } catch (e: Exception) {
            listener.onTaskResult(door.id, action, ReplyCode.LOCAL_ERROR, e.message!!)
        }
    }

    override fun connectionLost(cause: Throwable) {
        listener.onTaskResult(door.id, action, ReplyCode.REMOTE_ERROR, cause.toString())
    }

    override fun messageArrived(topic: String, message: MqttMessage) {
        listener.onTaskResult(door.id, action, ReplyCode.SUCCESS, message.toString())
    }

    override fun deliveryComplete(token: IMqttDeliveryToken) {
        // ignore
    }

    protected fun onPostExecute(result: DoorReply) {
        // already on GUI thread
        listener.onTaskResult(door.id, action, result.code, result.message)
    }

    companion object {
        private const val TAG = "MqttRequestHandler"
    }
}