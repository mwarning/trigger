package app.trigger.ssh;

import java.io.IOException;
import java.io.InputStream;

import app.trigger.WifiTools;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;

import app.trigger.MainActivity.Action;
import app.trigger.SshDoorSetup;
import app.trigger.DoorReply.ReplyCode;
import app.trigger.OnTaskCompleted;
import app.trigger.Log;


public class SshRequestHandler extends Thread {
    private final OnTaskCompleted listener;
    private final SshDoorSetup setup;
    private final Action action;

    public SshRequestHandler(OnTaskCompleted listener, SshDoorSetup setup, Action action){
        this.listener = listener;
        this.setup = setup;
        this.action = action;
    }

    public void run() {
        if (setup.getId() < 0) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Internal Error");
            return;
        }

        if (setup.require_wifi && !WifiTools.isConnected()) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.DISABLED, "Wifi Disabled.");
            return;
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

        String user = setup.user;
        String password = setup.password;
        String host = setup.host;
        KeyPair keypair = setup.keypair;
        int port = setup.port;

        try {
            if (command.isEmpty()) {
                listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "");
                return;
            }

            if (host.isEmpty()) {
                listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Host is empty.");
                return;
            }

            // fallback
            if (setup.user.isEmpty()) {
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
                listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, ioe.getMessage());
            } catch (JSchException jse) {
                listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, jse.getMessage());
            }

            listener.onTaskResult(setup.getId(), ReplyCode.SUCCESS, outputBuffer.toString());
        } catch (Exception e) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, e.toString());
        }
    }
}
