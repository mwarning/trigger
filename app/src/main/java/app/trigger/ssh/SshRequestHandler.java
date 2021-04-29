package app.trigger.ssh;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import app.trigger.WifiTools;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;

import app.trigger.MainActivity.Action;
import app.trigger.SshDoorSetup;
import app.trigger.DoorReply.ReplyCode;
import app.trigger.OnTaskCompleted;
import app.trigger.Log;


public class SshRequestHandler extends Thread {
    private final OnTaskCompleted listener;
    private final SshDoorSetup setup;
    private final Action action;

    public SshRequestHandler(OnTaskCompleted listener, SshDoorSetup setup, Action action) {
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
        KeyPairTrigger keypair = setup.keypair;
        int port = setup.port;
        Session session = null;

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

            if (keypair == null && password.length() == 0) {
                listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "No password or key set.");
                return;
            }

            final SSHClient ssh = new SSHClient();

            try {
                ssh.addHostKeyVerifier(new PromiscuousVerifier());
                ssh.connect(host, port);
                if (keypair != null) {
                    KeyProvider kp = ssh.loadKeys(keypair.getPrivateKeyPEM(), null, null);
                    ssh.authPublickey(user, kp);
                } else {
                    ssh.authPublickey(user, password);
                }
                session = ssh.startSession();
                Session.Command cmd = session.exec(command);
                cmd.join(5, TimeUnit.SECONDS);

                String output = IOUtils.readFully(cmd.getInputStream()).toString();
                if (cmd.getExitStatus() == 0) {
                    listener.onTaskResult(setup.getId(), ReplyCode.SUCCESS, output);
                } else {
                    listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, output);
                }
            } finally {
                try {
                    if (session != null) {
                        session.close();
                    }
                } catch (IOException e) {
                    // Do Nothing
                }

                ssh.disconnect();
            }
        } catch (Exception e) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, e.toString());
        }
    }
}
