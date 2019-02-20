package com.example.trigger;

import java.io.IOException;
import java.io.InputStream;

import android.os.AsyncTask;
import android.util.Log;

import com.example.trigger.ssh.SshTools;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;


public class SshRequestHandler extends AsyncTask<Object, Void, DoorReply> {
    private OnTaskCompleted listener;

    public SshRequestHandler(OnTaskCompleted listener){
        this.listener = listener;
    }

    @Override
    protected DoorReply doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e("SshRequestHandler.doInBackGround", "Unexpected number of params.");
            return DoorReply.internal_error();
        }

        if (!(params[0] instanceof Action && params[1] instanceof SshDoorSetup)) {
            Log.e("SshRequestHandler.doInBackground", "Invalid type of params.");
            return DoorReply.internal_error();
        }

        Action action = (Action) params[0];
        SshDoorSetup setup = (SshDoorSetup) params[1];

        if (setup.getId() < 0) {
            return DoorReply.internal_error();
        }

        String command = "";

        switch (action) {
            case open_door:
                command = setup.getOpenCommand();
                break;
            case close_door:
                command = setup.getCloseCommand();
                break;
            case update_state:
                command = setup.getStateCommand();
                break;
        }

        if (command.isEmpty()) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "");
        }

        try {
            int port = Integer.parseInt(setup.getPort());
            return connectAndExecute(setup.getKeyPair(), setup.getUser(), setup.getHost(), port, command);
        } catch (Exception e) {
            //e.printStackTrace();
            return new DoorReply(ReplyCode.LOCAL_ERROR, e.toString());
        }
    }

    protected void onPostExecute(DoorReply result) {
        listener.onTaskCompleted(result);
    }

    private static final DoorReply connectAndExecute(KeyPair keypair, String user, String host, int port, String command)
            throws Exception {
        JSch jsch = new JSch();

        SshTools.KeyPairData data = SshTools.keypairToBytes(keypair);

        byte passphrase[] = new byte[0];

        jsch.addIdentity("authkey", data.prvkey, data.pubkey, passphrase);

        Session session = jsch.getSession(user, host, port);
        session.setConfig("StrictHostKeyChecking", "no");
        //java.util.Properties config = new java.util.Properties();
        //config.put("StrictHostKeyChecking", "no");
        //session.setConfig(config);
        StringBuilder outputBuffer = new StringBuilder();

        try {
            session.connect();

            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            InputStream commandOutput = channel.getInputStream();
            channel.connect();
            int readByte = commandOutput.read();

            while (readByte != 0xffffffff) {
               outputBuffer.append((char)readByte);
               readByte = commandOutput.read();
            }

            channel.disconnect();
            session.disconnect();
        } catch (IOException ioe) {
            return new DoorReply(ReplyCode.REMOTE_ERROR, ioe.getMessage());
        } catch (JSchException jse) {
            return new DoorReply(ReplyCode.REMOTE_ERROR, jse.getMessage());
        }

        return new DoorReply(ReplyCode.SUCCESS, outputBuffer.toString());
    }
}
