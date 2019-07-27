package com.example.trigger.mqtt;

import javax.net.ssl.SSLContext;

//import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import android.content.Context;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.example.trigger.Utils;
import com.example.trigger.MainActivity.Action;
import com.example.trigger.MqttDoorSetup;
import com.example.trigger.DoorReply;
import com.example.trigger.DoorReply.ReplyCode;
import com.example.trigger.OnTaskCompleted;

public class MqttRequestHandler extends AsyncTask<Object, Void, DoorReply> implements MqttCallback {
    private OnTaskCompleted listener;
    //Context ctx;

    public MqttRequestHandler(OnTaskCompleted listener) {
        this.listener = listener;
        //this.ctx = ((AppCompatActivity) this.listener).getApplicationContext();
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
            return new DoorReply(ReplyCode.LOCAL_ERROR,
                    "Invalid QoS value: " + setup.qos
            );
        }

        String clientId = MqttClient.generateClientId();
        MqttConnectOptions opts = new MqttConnectOptions();
        MemoryPersistence persistence = new MemoryPersistence();
        final String hostname = setup.hostname;
        final String addr;

        try {
            // publish
            if (hostname.startsWith("tcp://")) {
                addr = Utils.rebuildAddress(hostname, 1883);
            } else if (hostname.startsWith("ssl://")) {
                addr = Utils.rebuildAddress(hostname, 8883);
            } else {
                throw new Exception(
                        "Hostname needs to start with 'tcp://' or 'ssl://'."
                );
            }

            //MqttConnectOptions opts = new MqttConnectOptions();
            // false: broker will not keep messages
            opts.setCleanSession(false);

            if (hostname.startsWith("ssl://")) {
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

            MqttClient client = new MqttClient(addr, clientId, persistence);
            client.setTimeToWait(3000); // 3 seconds
            // connect
            client.connect(opts);
            client.setCallback(this);

            // true for status
            // false for command
            MqttMessage message = new MqttMessage(query.getBytes());
            message.setRetained(setup.retained);
            message.setQos(setup.qos);

            // publish
            client.publish(setup.topic, message);

            //client.disconnect();
            // we do not get a message in return ...
            Log.d("MqttRequestHandler", "end of doInBackground reached");
            return new DoorReply(ReplyCode.SUCCESS, "");
        } catch (MqttException me) {
            return new DoorReply(ReplyCode.REMOTE_ERROR, me.getMessage());
        } catch (Exception e) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, e.toString());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.d("MqttRequestHandler", "connectionLost: " + cause.toString());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message)
            throws Exception {
        Log.d("MqttRequestHandler", "messageArrived: " + message);
        //this.listener.onTaskCompleted(
        // new DoorReply(ReplyCode.SUCCESS, message.toStrng())
        // );
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d("MqttRequestHandler", "deliveryComplete");
    }

    protected void onPostExecute(DoorReply result) {
        listener.onTaskCompleted(result);
    }
}
