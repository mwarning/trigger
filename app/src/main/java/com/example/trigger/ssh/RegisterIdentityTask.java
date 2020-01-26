package com.example.trigger.ssh;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.os.AsyncTask;

import com.example.trigger.Utils;
import com.jcraft.jsch.KeyPair;


class RegisterIdentityTask extends AsyncTask<Object, Void, String> {
    private OnTaskCompleted listener;

    public interface OnTaskCompleted {
        void onRegisterIdentityTaskCompleted(String message);
    }

    public RegisterIdentityTask(OnTaskCompleted listener){
        this.listener = listener;
    }

    @Override
    protected String doInBackground(Object... params) {
        String address = (String) params[0];
        KeyPair keypair = (KeyPair) params[1];
        String reply;

        try {
            InetSocketAddress addr = Utils.createSocketAddress(
                Utils.rebuildAddress(address, -1)
            );
            Socket client = new Socket(addr.getAddress(), addr.getPort());

            OutputStream os = client.getOutputStream();
            InputStream is = client.getInputStream();
            DataOutputStream writer = new DataOutputStream(os);

            // write key in PEM format
            keypair.writePublicKey(writer, keypair.getPublicKeyComment());
            os.flush();

            reply = Utils.readStringFromStream(is, 200);

            os.close();

            client.close();
        } catch(Exception e) {
            return e.toString();
        }
        
        return (reply != null && reply.length() > 0) ? reply : "Done";
    }

    @Override
    protected void onPostExecute(String message) {
        listener.onRegisterIdentityTaskCompleted(message);
    }
}