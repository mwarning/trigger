package com.example.sphincter;


import android.content.SharedPreferences;
import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;


enum Action {
    open_door,
    close_door,
    update_state
}

public class SphincterRequestHandler extends AsyncTask<Action, Void, String> {

    private OnTaskCompleted listener;
    private SharedPreferences sharedPreferences;

    public SphincterRequestHandler(OnTaskCompleted l, SharedPreferences p){
        this.listener = l;
        this.sharedPreferences = p;
    }

    @Override
    protected String doInBackground(Action... params) {
        String url = sharedPreferences.getString("prefURL", "");

        if (url.isEmpty()) {
            return "";
        }

        switch (params[0]) {
            case open_door:
                url += "?action=open";
                break;
            case close_door:
                url += "?action=close";
                break;
            case update_state:
                url += "?action=state";
        }

        url += "&token=" + sharedPreferences.getString("prefToken", "");

        return CallSphincterAPI(url);
    }
/*
    public static void disableCertificateValidation()
    {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {
                new TrustAllManager()
        };

        // Ignore differences between given hostname and certificate hostname
        HostnameVerifier hv = new TrustAllHostnameVerifier();

        // Install the all-trusting trust manager
        try
        {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(hv);
        } catch (Exception e) {}
    }
*/

    final String CallSphincterAPI(String urlstr) {
        try {
            // TODO: call on checkbox press
            if (sharedPreferences.getBoolean("prefIgnore", false)) {
                System.out.println("allow all ssl");
                HttpsTrustManager.allowAllSSL();
            } //else...

            URL url = new URL(urlstr);
            URLConnection connection =  url.openConnection();

            //TODO: remove
            if (connection instanceof HttpsURLConnection) {
                System.out.println("is https connection");
            }

            if (connection instanceof HttpURLConnection) {
                HttpURLConnection con = (HttpURLConnection) connection;
                con.setConnectTimeout(2000);
                return readStream(con.getInputStream());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    private String readStream(InputStream in) {
        BufferedReader reader = null;
        String result ="";

        try {
            reader = new BufferedReader(new InputStreamReader(in));
            String line = "";

            while ((line = reader.readLine()) != null) {
                result += line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    protected void onPostExecute(String result) {
        listener.onTaskCompleted(result.trim());
    }
}
