package com.example.trigger.https;

import java.net.URL;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;

import android.os.AsyncTask;

import com.example.trigger.Log;


public class CertificateFetchTask extends AsyncTask<Object, Void, CertificateFetchTask.Result> {
    private OnTaskCompleted listener;

    public interface OnTaskCompleted {
        void onCertificateFetchTaskCompleted(Result result);
    }

    public static class Result {
        String error;
        Certificate certificate;

        Result(Certificate certificate, String error) {
            this.certificate = certificate;
            this.error = error;
        }
    }

    public CertificateFetchTask(CertificateFetchTask.OnTaskCompleted listener){
        this.listener = listener;
    }

    @Override
    protected Result doInBackground(Object... params) {
        if (params.length != 1) {
            Log.e("CertificateFetchTask", "Unexpected number of params.");
            return new Result(null, "Internal Error");
        }

        try {
            URL url = new URL((String) params[0]);

            // try to establish TLS session only
            int port = (url.getPort() > 0) ? url.getPort() : url.getDefaultPort();
            url = new URL("https", url.getHost(), port, "");

            // disable all certification checks
            HttpsTools.disableDefaultHostnameVerifier();
            HttpsTools.disableDefaultCertificateValidation();

            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setConnectTimeout(2000);

            con.connect();
            Certificate[] certificates = con.getServerCertificates();
            con.disconnect();

            if (certificates.length == 0) {
                return new Result(null, "No certificate found.");
            } else {
                return new Result(certificates[0], "");
            }
        } catch (Exception e) {
            return new Result(null, e.toString());
        }
    }

    protected void onPostExecute(Result result) {
        listener.onCertificateFetchTaskCompleted(result);
    }
}
