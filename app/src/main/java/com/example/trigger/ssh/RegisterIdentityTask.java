package com.example.trigger.ssh;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.example.trigger.Utils;
import com.jcraft.jsch.KeyPair;


class RegisterIdentityTask extends AsyncTask<Object, Void, String> {
    private Context context;

    public RegisterIdentityTask(Context context){
        this.context = context;
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
            Toast.makeText(context, "Done.", Toast.LENGTH_SHORT).show();
        } catch(Exception e) {
            Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show();
        }
        
        return null;
    }
    
    @Override
    protected void onPostExecute(String str) {
    }
}