package app.trigger.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.spec.InvalidKeySpecException;
import app.trigger.Utils;
import app.trigger.WifiTools;

import app.trigger.MainActivity.Action;
import app.trigger.SshDoorSetup;
import app.trigger.DoorReply.ReplyCode;
import app.trigger.OnTaskCompleted;
import app.trigger.Log;

import com.trilead.ssh2.crypto.keys.Ed25519Provider;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.crypto.PEMDecoder;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;


public class SshRequestHandler extends Thread implements ConnectionMonitor {
    private static final String TAG = "SshRequestHandler";
    private final OnTaskCompleted listener;
    private final SshDoorSetup setup;
    private final Action action;
    private final String passphrase;

    static {
        Log.d(TAG, "Ed25519Provider.insertIfNeeded2");
        // Since this class deals with Ed25519 keys, we need to make sure this is available.
        Ed25519Provider.insertIfNeeded();
    }

    public SshRequestHandler(OnTaskCompleted listener, SshDoorSetup setup, Action action, String passphrase) {
        this.listener = listener;
        this.setup = setup;
        this.action = action;
        this.passphrase = passphrase;
    }

    public void run() {
        if (setup.getId() < 0) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Internal Error");
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

        final String username = setup.user.isEmpty() ? "root" : setup.user;
        final String password = setup.password;
        final String hostname = setup.host;
        final KeyPairBean keypair = setup.keypair;
        final int port = setup.port;

        if (command.isEmpty()) {
            listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "");
            return;
        }

        if (hostname.isEmpty()) {
            listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Server address is empty.");
            return;
        }

        Connection connection = null;
        Session session = null;

        try {
            connection = new Connection(hostname, port);
            connection.addConnectionMonitor(this);
            ConnectionInfo connectionInfo = connection.connect(
                null, // host key verifier
                2000, // connect timeout
                3000 // key exchange timeout
            );

            // authentication by key pair
            if (keypair != null && !connection.isAuthenticationComplete()) {
                if (!tryPublicKey(connection, username, keypair, this.passphrase)) {
                    if (Utils.isEmpty(this.passphrase)) {
                        listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, "Key pair password was not accepted.");
                    } else {
                        // continue with _additional_ password authentication
                    }
                }
            }

            // authentication by password
            if (!Utils.isEmpty(password) && !connection.isAuthenticationComplete()) {
                if (connection.isAuthMethodAvailable(username, "password")) {
                    if (!connection.authenticateWithPassword(username, password)) {
                        listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, "Password was not accepted.");
                        return;
                    }
                }
            }

            // try without authentication
            if (Utils.isEmpty(password) && !connection.isAuthenticationComplete()) {
                if (connection.authenticateWithNone(username)) {
                    // login successful
                } else {
                    listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, "Login without credentials failed.");
                    return;
                }
            }

            if (!connection.isAuthenticationComplete()) {
                listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, "Authentication failed.");
                return;
            }

            session = connection.openSession();
            byte[] buffer = new byte[1000];

            // clear any welcome message (stdout/stderr)
            //read(session, buffer, 0, buffer.length);

            session.execCommand(command);

            // read stdout (drop stderr)
            int bytes_read = read(session, buffer, 0, buffer.length, setup.timeout);
            String output = new String(buffer, 0, bytes_read);

            Integer ret = session.getExitStatus();
            if (ret == null || ret == 0) {
                listener.onTaskResult(setup.getId(), ReplyCode.SUCCESS, output);
            } else {
                listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, output);
            }
        } catch (Exception e) {
            listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, e.getMessage());

            Log.e(TAG, "Problem in SSH connection thread during authentication: " + e);

        } finally {
            if (session != null) {
                session.close();
            }

            if (connection != null) {
                connection.close();
            }
        }
    }

    private static final int conditions = ChannelCondition.STDOUT_DATA
        | ChannelCondition.STDERR_DATA
        | ChannelCondition.CLOSED
        | ChannelCondition.EOF;

    private static boolean tryPublicKey(Connection connection, String username, KeyPairBean kp, String passphrase) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyPair pair = null;

        if (KeyPairBean.KEY_TYPE_IMPORTED.equals(kp.type)) {
            // load specific key using pem format
            pair = PEMDecoder.decode(new String(kp.privateKey, "UTF-8").toCharArray(), passphrase);
        } else {
            // load using internal generated format
            PrivateKey privKey;
            try {
                privKey = PubkeyUtils.decodePrivate(kp.privateKey, kp.type, passphrase);
            } catch (Exception e) {
                Log.e(TAG, "Bad password for key. Authentication failed: " + e);
                return false;
            }

            PublicKey pubKey = PubkeyUtils.decodePublic(kp.publicKey, kp.type);

            // convert key to trilead format
            pair = new KeyPair(pubKey, privKey);
        }

        return connection.authenticateWithPublicKey(username, pair);
    }

    @Override
    public void connectionLost(Throwable reason) {
        Log.d(TAG, "connectionLost");
    }

    private static int read(Session session, byte[] buffer, int start, int len, int timeout_ms) throws IOException {
        int bytesRead = 0;

        if (session == null)
            return 0;

        InputStream stdout = session.getStdout();
        InputStream stderr = session.getStderr();

        int newConditions = session.waitForCondition(conditions, timeout_ms);

        if ((newConditions & ChannelCondition.STDOUT_DATA) != 0) {
            bytesRead = stdout.read(buffer, start, len);
        }

        if ((newConditions & ChannelCondition.STDERR_DATA) != 0) {
            byte[] discard = new byte[256];
            while (stderr.available() > 0) {
                stderr.read(discard);
                //Log.e(TAG, "stderr: " + (new String(discard)));
            }
        }

        if ((newConditions & ChannelCondition.EOF) != 0) {
            throw new IOException("Remote end closed connection");
        }

        return bytesRead;
    }

    private static void write(Session session, String command) throws IOException {
        OutputStream stdin = session.getStdin();
        if (stdin != null) {
            stdin.write(command.getBytes());
        }
    }
}
