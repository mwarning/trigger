package com.example.trigger.mqtt;

import javax.net.ssl.SSLContext;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.example.trigger.Utils;
import com.example.trigger.MainActivity.Action;
import com.example.trigger.MqttDoorSetup;
import com.example.trigger.DoorReply;
import com.example.trigger.DoorReply.ReplyCode;
import com.example.trigger.OnTaskCompleted;

import static com.example.trigger.MainActivity.Action.update_state;


public class MqttRequestHandler extends AsyncTask<Object, Void, DoorReply> implements MqttCallback {
    private OnTaskCompleted listener;

    public MqttRequestHandler(OnTaskCompleted listener) {
        this.listener = listener;
    }

    @Override
    protected DoorReply doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e("MqttRequestHandler", "Unexpected number of params.");
            return DoorReply.internal_error();
        }

        if (!(params[0] instanceof Action && params[1] instanceof MqttDoorSetup)) {
            Log.e("MqttRequestHandler", "Invalid type of params.");
            return DoorReply.internal_error();
        }

        Action action = (Action) params[0];
        MqttDoorSetup setup = (MqttDoorSetup) params[1];

        if (setup.getId() < 0) {
            return DoorReply.internal_error();
        }

        String query = "";

        switch (action) {
            case open_door:
                query = setup.open_query;
                break;
            case close_door:
                query = setup.close_query;
                break;
            case update_state:
                query = setup.status_query;
                break;
        }

        if (query.isEmpty()) {
            // ignore
            return new DoorReply(ReplyCode.LOCAL_ERROR, "");
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

            // false: broker will not keep messages
            opts.setCleanSession(false);

            if (address.startsWith("ssl://")) {
                if (setup.certificate != null) {
                    // use given certificate only
                    opts.setSocketFactory(
                            Utils.getFactoryWithCertificate(setup.certificate)
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

            if (action == update_state) {
                // subscribe
                client.subscribe(setup.topic);
            } else {
                //publish
                MqttMessage message = new MqttMessage(query.getBytes());
                message.setRetained(setup.retained);
                message.setQos(setup.qos);

                client.publish(setup.topic, message);
            }

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
