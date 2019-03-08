package com.example.trigger.ssh;

import java.io.DataOutputStream;
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

        try {
            InetSocketAddress addr = Utils.parseSocketAddress(address, -1);
            Socket client = new Socket(addr.getAddress(), addr.getPort());

            OutputStream outToServer = client.getOutputStream();
            DataOutputStream out = new DataOutputStream(outToServer);
            keypair.writePublicKey(out, keypair.getPublicKeyComment());

            out.flush();
            out.close();

            client.close();
        } catch(Exception e) {
            return e.toString();
        }
        
        return "Done";
    }
    
    @Override
    protected void onPostExecute(String message) {
        listener.onRegisterIdentityTaskCompleted(message);
    }
}