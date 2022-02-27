package app.trigger.mqtt;

import javax.net.ssl.SSLContext;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.security.PrivateKey;

import app.trigger.Utils;
import app.trigger.MainActivity.Action;
import app.trigger.MqttDoorSetup;
import app.trigger.DoorReply;
import app.trigger.DoorReply.ReplyCode;
import app.trigger.OnTaskCompleted;
import app.trigger.Log;
import app.trigger.WifiTools;
import app.trigger.ssh.PubkeyUtils;


public class MqttRequestHandler extends Thread implements MqttCallback {
    private static final String TAG = "MqttRequestHandler";
    private final OnTaskCompleted listener;
    private final MqttDoorSetup setup;
    private final Action action;

    public MqttRequestHandler(OnTaskCompleted listener, MqttDoorSetup setup, Action action) {
        this.listener = listener;
        this.setup = setup;
        this.action = action;
    }

    public void run() {
        if (setup.getId() < 0) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Internal Error");
            return;
        }

        if (WifiTools.isConnected()) {
            String current_ssid = WifiTools.getCurrentSSID();
            if (setup.ssids.length() > 0 && !WifiTools.matchSSID(setup.ssids, current_ssid)) {
                this.listener.onTaskResult(setup.getId(), ReplyCode.DISABLED,
                    "SSID mismatch<br/>(connected to '" + current_ssid + "')");
                return;
            }
        } else {
            if (setup.require_wifi) {
                this.listener.onTaskResult(setup.getId(), ReplyCode.DISABLED, "Wifi Disabled");
                return;
            }
        }

        if (Utils.isEmpty(setup.server)) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "MQTT broker address not set.");
            return;
        }

        if (action == Action.fetch_state && setup.status_topic.isEmpty()) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "");
            return;
        }

        if ((action == Action.open_door || action == Action.ring_door || action == Action.close_door)
                && setup.command_topic.isEmpty()) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "No command topic set.");
            return;
        }

        if (action == Action.close_door && setup.close_command.isEmpty()) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "No close command set.");
            return;
        }

        if (setup.qos != 0 && setup.qos != 1 && setup.qos != 2) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Invalid QoS value: " + setup.qos);
            return;
        }

        String clientId = MqttClient.generateClientId();
        MqttConnectOptions opts = new MqttConnectOptions();
        MemoryPersistence persistence = new MemoryPersistence();
        final String address;

        try {
            if (setup.server.startsWith("tcp://")) {
                address = Utils.rebuildAddress(setup.server, 1883);
            } else if (setup.server.startsWith("ssl://")) {
                address = Utils.rebuildAddress(setup.server, 8883);
            } else {
                throw new Exception(
                    "Server address needs to start with 'tcp://' or 'ssl://'."
                );
            }

            if (!setup.username.isEmpty()) {
                opts.setUserName(setup.username);
            }

            if (!setup.password.isEmpty()) {
                opts.setPassword(setup.password.toCharArray());
            }

            // false: broker will not keep messages
            opts.setCleanSession(false);

            if (address.startsWith("ssl://")) {
                opts.setHttpsHostnameVerificationEnabled(!setup.ignore_hostname_mismatch);

                if (setup.server_certificate != null) {
                    // use given certificate only
                    if (setup.client_keypair != null && setup.client_certificate != null) {
                        PrivateKey client_private_key = PubkeyUtils.decodePrivate(
                            setup.client_keypair.getPrivateKey(), setup.client_keypair.getType()
                        );
                        opts.setSocketFactory(
                            Utils.getSocketFactoryWithCertificateAndClientKey(
                                setup.server_certificate, setup.client_certificate, client_private_key)
                        );
                    } else if (setup.client_keypair == null && setup.client_certificate == null) {
                        opts.setSocketFactory(
                            Utils.getSocketFactoryWithCertificate(setup.server_certificate)
                        );
                    } else {
                        throw new Exception("Both client key and client certificate needed.");
                    }
                } else {
                    if (setup.client_keypair != null || setup.client_certificate != null) {
                        throw new Exception("Client key and client certificate needed.");
                    } else {
                        // use system default certificates
                        opts.setSocketFactory(
                            SSLContext.getDefault().getSocketFactory()
                        );
                    }
                }
            }

            MqttClient client = new MqttClient(address, clientId, persistence);
            client.setTimeToWait(3000); // 3 seconds
            client.setCallback(this);
            client.connect(opts);

            switch (action) {
                case fetch_state:
                    // subscribe
                    client.subscribe(setup.status_topic);
                    break;
                case open_door:
                    // publish
                    MqttMessage open = new MqttMessage(setup.open_command.getBytes());
                    open.setRetained(setup.retained);
                    open.setQos(setup.qos);

                    client.publish(setup.command_topic, open);
                    break;
                case ring_door:
                    // publish
                    MqttMessage ring = new MqttMessage(setup.ring_command.getBytes());
                    ring.setRetained(setup.retained);
                    ring.setQos(setup.qos);

                    client.publish(setup.command_topic, ring);
                    break;
                case close_door:
                    // publish
                    MqttMessage close = new MqttMessage(setup.close_command.getBytes());
                    close.setRetained(setup.retained);
                    close.setQos(setup.qos);

                    client.publish(setup.command_topic, close);
                    break;
            }
            this.listener.onTaskResult(setup.getId(), ReplyCode.SUCCESS, "");
        } catch (MqttException me) {
            //me.getMessage() returns "MqttException" only
            this.listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, me.toString());
        } catch (Exception e) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, cause.toString());
    }

    @Override
    public void messageArrived(String topic, final MqttMessage message) {
        listener.onTaskResult(setup.getId(), ReplyCode.SUCCESS, message.toString());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // ignore
    }

    protected void onPostExecute(DoorReply result) {
        // already on GUI thread
        listener.onTaskResult(setup.getId(), result.code, result.message);
    }
}
