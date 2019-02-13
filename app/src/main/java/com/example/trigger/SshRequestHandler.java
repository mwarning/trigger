package com.example.trigger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.os.AsyncTask;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;


public class SshRequestHandler extends AsyncTask<Object, Void, TaskResult> {
    private OnTaskCompleted listener;

    public SshRequestHandler(OnTaskCompleted listener){
        this.listener = listener;
    }

    @Override
    protected TaskResult doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e("SshRequestHandler.doInBackGround", "Unexpected number of params.");
            return TaskResult.error("Internal Error");
        }

        if (!(params[0] instanceof Action && params[1] instanceof SshDoorSetup)) {
            Log.e("SshRequestHandler.doInBackground", "Invalid type of params.");
            return TaskResult.error("Internal Error");
        }

        Action action = (Action) params[0];
        SshDoorSetup setup = (SshDoorSetup) params[1];

        if (setup.getId() < 0) {
            return TaskResult.error("Internal Error");
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
            return TaskResult.empty();
        }

        try {
            File keypath = new File(setup.getKeyPath());
            int port = Integer.parseInt(setup.getPort());
            return connectAndExecute(keypath, setup.getUser(), setup.getHost(), port, command);
        } catch (Exception e) {
            //e.printStackTrace();
            return TaskResult.error("Unknown Error: " + e.toString());
        }
    }

    protected void onPostExecute(TaskResult result) {
        listener.onTaskCompleted(result);
    }

    private static final boolean keyPairExists(File keyDir) {
        File privateKeyFile = new File(keyDir, "id_rsa");
        File publicKeyFile = new File(keyDir, "id_rsa.pub");

        if (privateKeyFile.exists() && publicKeyFile.exists()) {
            return true;
        } else {
            return false;
        }
    }

    private static final void generateNewIdentity(File keyDir, String comment, int strength) throws Exception {
        JSch jsch = new JSch();
        KeyPair newKeyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, strength);

        File privateKeyFile = new File(keyDir, "id_rsa");
        File publicKeyFile = new File(keyDir, "id_rsa.pub");

        if (privateKeyFile.exists()) {
            privateKeyFile.delete();
        }

        if (publicKeyFile.exists()) {
            publicKeyFile.delete();
        }

        newKeyPair.writePrivateKey(new FileOutputStream(privateKeyFile));
        newKeyPair.writePublicKey(new FileOutputStream(publicKeyFile), comment);

        newKeyPair.dispose();
    }

    private static final TaskResult connectAndExecute(File keyDir, String user, String host, int port, String command)
            throws Exception {
        JSch jsch = new JSch();

        File privateKeyFile = new File(keyDir, "id_rsa");
        File publicKeyFile = new File(keyDir, "id_rsa.pub");

        FileInputStream privateKeyIn = new FileInputStream(privateKeyFile);
        FileInputStream publicKeyIn = new FileInputStream(publicKeyFile);

        byte privateKey[] = new byte[(int) privateKeyFile.length()];
        byte publicKey[] = new byte[(int) publicKeyFile.length()];

        privateKeyIn.read(privateKey);
        publicKeyIn.read(publicKey);

        privateKeyIn.close();
        publicKeyIn.close();

        byte passphrase[] = new byte[0];

        jsch.addIdentity("authkey", privateKey, publicKey, passphrase);
        //System.out.println("identity added ");

        Session session = jsch.getSession(user, host, port);
        //System.out.println("session created.");

        session.setConfig("StrictHostKeyChecking", "no");
        //java.util.Properties config = new java.util.Properties();
        //config.put("StrictHostKeyChecking", "no");
        //session.setConfig(config);

        //System.out.println("session connected.....");

        StringBuilder outputBuffer = new StringBuilder();

        try {
            session.connect();

            Channel channel = session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);
            InputStream commandOutput = channel.getInputStream();
            channel.connect();
            int readByte = commandOutput.read();

            while (readByte != 0xffffffff) {
               outputBuffer.append((char)readByte);
               readByte = commandOutput.read();
            }

            channel.disconnect();
            session.disconnect();
        } catch(IOException ioe) {
            return TaskResult.error(ioe.getMessage());
        } catch(JSchException jse) {
            return TaskResult.error(jse.getMessage());
        }

        return TaskResult.msg(outputBuffer.toString());
    }
}
