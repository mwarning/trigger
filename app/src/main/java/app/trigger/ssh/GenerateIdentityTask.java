package app.trigger.ssh;

import android.os.AsyncTask;

import app.trigger.Log;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;


class GenerateIdentityTask extends AsyncTask<Object, Void, String> {
    public OnTaskCompleted listener = null;
    public KeyPair keypair = null;

    public interface OnTaskCompleted {
        void onGenerateIdentityTaskCompleted(String message, KeyPair keypair);
    }

    public GenerateIdentityTask(OnTaskCompleted listener) {
        this.listener = listener;
    }

    @Override
    protected String doInBackground(Object... params) {
        if (params.length != 1) {
            Log.e("GenerateIdentityTask", "Unexpected number of params.");
            return "Internal Error";
        }

        try {
            Integer bits = (Integer) params[0];

            JSch jsch = new JSch();
            keypair = KeyPair.genKeyPair(jsch, KeyPair.RSA, bits);
            keypair.setPublicKeyComment(null);
        } catch (Exception e) {
            return e.getMessage();
        }

        return "Done";
    }

    @Override
    protected void onPostExecute(String message) {
    	listener.onGenerateIdentityTaskCompleted(message, keypair);
    }
}