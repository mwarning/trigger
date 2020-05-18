package app.trigger.ssh;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import app.trigger.Utils;
import com.jcraft.jsch.KeyPair;


class RegisterIdentityTask extends Thread {
    private OnTaskCompleted listener;
    private String address;
    private KeyPair keypair;

    public interface OnTaskCompleted {
        void onRegisterIdentityTaskCompleted(String message);
    }

    public RegisterIdentityTask(OnTaskCompleted listener, String address, KeyPair keypair) {
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
                listener.onRegisterIdentityTaskCompleted("Missing port, use <ip-address>:<port>");
                return;
            }

            Socket client = new Socket(addr.getAddress(), addr.getPort());

            OutputStream os = client.getOutputStream();
            InputStream is = client.getInputStream();
            DataOutputStream writer = new DataOutputStream(os);

            // write key in PEM format
            keypair.writePublicKey(writer, keypair.getPublicKeyComment());
            os.flush();

            String reply = Utils.readInputStreamWithTimeout(is, 1024, 500);
            client.close();

            if (reply.length() > 0) {
                listener.onRegisterIdentityTaskCompleted(reply);
            } else {
                listener.onRegisterIdentityTaskCompleted("Done");
            }
        } catch(Exception e) {
            listener.onRegisterIdentityTaskCompleted(e.toString());
        }
    }
}