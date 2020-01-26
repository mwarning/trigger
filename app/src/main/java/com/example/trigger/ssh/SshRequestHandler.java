package com.example.trigger.ssh;

import java.io.IOException;
import java.io.InputStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;

import com.example.trigger.MainActivity.Action;
import com.example.trigger.SshDoorSetup;
import com.example.trigger.DoorReply;
import com.example.trigger.DoorReply.ReplyCode;
import com.example.trigger.OnTaskCompleted;
import com.example.trigger.RequestHandler;
import com.example.trigger.Log;


public class SshRequestHandler extends RequestHandler {
    private OnTaskCompleted listener;

    public SshRequestHandler(OnTaskCompleted listener){
        this.listener = listener;
    }

    @Override
    protected DoorReply doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e(this, "Unexpected number of params.");
            return DoorReply.internal_error();
        }

        if (!(params[0] instanceof Action && params[1] instanceof SshDoorSetup)) {
            Log.e(this, "Invalid type of params.");
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
                command = setup.open_command;
                break;
            case close_door:
                command = setup.close_command;
                break;
            case ring_door:
                command = setup.ring_command;
                break;
            case fetch_state:
                command = setup.state_command;
                break;
        }

        try {
            return connectAndExecute(
                setup.keypair, setup.user, setup.password,
                setup.host, setup.port, command
            );
        } catch (Exception e) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, e.toString());
        }
    }

    protected void onPostExecute(DoorReply result) {
        listener.onTaskCompleted(result);
    }

    private static final DoorReply connectAndExecute(KeyPair keypair, String user, String password, String host, int port, String command)
            throws Exception {

        if (command.isEmpty()) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "");
        }

        if (host.isEmpty()) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Host is empty.");
        }

        // fallback
        if (user.isEmpty()) {
            user = "root";
        }

        JSch jsch = new JSch();

        if (keypair != null) {
            SshTools.KeyPairData data = SshTools.keypairToBytes(keypair);
            byte passphrase[] = new byte[0];

            jsch.addIdentity("authkey", data.prvkey, data.pubkey, passphrase);
        }

        Session session = jsch.getSession(user, host, port);

        if (password.length() > 0) {
            session.setPassword(password);
        }

        session.setConfig("StrictHostKeyChecking", "no");

        StringBuilder outputBuffer = new StringBuilder();

        try {
            session.connect(5000); // 5sec timeout

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
