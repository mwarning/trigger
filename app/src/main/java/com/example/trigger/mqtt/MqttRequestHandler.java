package com.example.trigger.mqtt;

import javax.net.ssl.SSLContext;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import android.support.v7.app.AppCompatActivity;

import com.example.trigger.Utils;
import com.example.trigger.MainActivity.Action;
import com.example.trigger.MqttDoorSetup;
import com.example.trigger.DoorReply;
import com.example.trigger.DoorReply.ReplyCode;
import com.example.trigger.OnTaskCompleted;
import com.example.trigger.RequestHandler;
import com.example.trigger.Log;


public class MqttRequestHandler extends RequestHandler implements MqttCallback {
    private OnTaskCompleted listener;

    public MqttRequestHandler(OnTaskCompleted listener) {
        this.listener = listener;
    }

    @Override
    protected DoorReply doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e(this, "Unexpected number of params.");
            return DoorReply.internal_error();
        }

        if (!(params[0] instanceof Action && params[1] instanceof MqttDoorSetup)) {
            Log.e(this, "Invalid type of params.");
            return DoorReply.internal_error();
        }

        Action action = (Action) params[0];
        MqttDoorSetup setup = (MqttDoorSetup) params[1];

        if (setup.getId() < 0) {
            return DoorReply.internal_error();
        }

        switch (action) {
            case fetch_state:
                if (setup.status_topic.isEmpty()) {
                    return new DoorReply(ReplyCode.LOCAL_ERROR, "");
                }
                break;
            case open_door:
            case ring_door:
            case close_door:
                if (setup.command_topic.isEmpty()) {
                    return new DoorReply(ReplyCode.LOCAL_ERROR, "No command topic set.");
                }
                if (setup.close_command.isEmpty()) {
                    return new DoorReply(ReplyCode.LOCAL_ERROR, "No close command set.");
                }
                break;
        }

        if (setup.qos != 0 && setup.qos != 1 && setup.qos != 2) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Invalid QoS value: " + setup.qos);
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
                if (setup.certificate != null) {
                    // use given certificate only
                    opts.setSocketFactory(
                            Utils.getSocketFactoryWithCertificate(setup.certificate)
                    );
                } else {
                    // use system default certificates
                    opts.setSocketFactory(
                            SSLContext.getDefault().getSocketFactory()
                    );
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
            // this reply will be ignored
            return new DoorReply(ReplyCode.SUCCESS, "");
        } catch (MqttException me) {
            return new DoorReply(ReplyCode.REMOTE_ERROR, me.getMessage());
        } catch (Exception e) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, e.getMessage());
        }
    }

    private void callOnGuiThread(final ReplyCode code, final String message) {
        ((AppCompatActivity) this.listener).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listener.onTaskCompleted(
                   new DoorReply(code, message)
                );
            }
        });
    }

    @Override
    public void connectionLost(Throwable cause) {
        callOnGuiThread(ReplyCode.REMOTE_ERROR, cause.toString());
    }

    @Override
    public void messageArrived(String topic, final MqttMessage message) {
        callOnGuiThread(ReplyCode.SUCCESS, message.toString());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // ignore
    }

    protected void onPostExecute(DoorReply result) {
        // already on GUI thread
        listener.onTaskCompleted(result);
    }
}
