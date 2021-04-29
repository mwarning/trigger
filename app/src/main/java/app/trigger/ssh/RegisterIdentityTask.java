package app.trigger.ssh;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import app.trigger.Utils;


class RegisterIdentityTask extends Thread {
    private OnTaskCompleted listener;
    private String address;
    private KeyPairTrigger keypair;

    public interface OnTaskCompleted {
        void onRegisterIdentityTaskCompleted(String message);
    }

    public RegisterIdentityTask(OnTaskCompleted listener, String address, KeyPairTrigger keypair) {
        this.listener = listener;
        this.address = address;
        this.keypair = keypair;
    }

    public void run() {
        try {
            InetSocketAddress addr = Utils.createSocketAddress(
                Utils.rebuildAddress(address, 0)
            );

            if (addr.getPort() == 0) {
                listener.onRegisterIdentityTaskCompleted("Missing port, use <address>:<port>");
                return;
            }

            Socket client = new Socket(addr.getAddress(), addr.getPort());

            OutputStream os = client.getOutputStream();
            InputStream is = client.getInputStream();
            DataOutputStream writer = new DataOutputStream(os);

            // send public key in PEM format
            os.write(keypair.getPublicKeyPEM().getBytes());
            os.flush();

            String reply = Utils.readInputStreamWithTimeout(is, 1024, 1000);
            client.close();

            if (reply.length() > 0) {
                listener.onRegisterIdentityTaskCompleted(reply);
            } else {
                listener.onRegisterIdentityTaskCompleted("Done");
            }
        } catch (Exception e) {
            listener.onRegisterIdentityTaskCompleted(e.toString());
        }
    }
}