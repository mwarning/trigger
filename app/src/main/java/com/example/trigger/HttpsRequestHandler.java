package com.example.trigger;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;


enum Action {
    open_door,
    close_door,
    update_state
}

public class HttpsRequestHandler extends AsyncTask<Object, Void, String> {
    private SharedPreferences pref;
    private OnTaskCompleted listener;

    public HttpsRequestHandler(OnTaskCompleted l, SharedPreferences p){
        this.listener = l;
        this.pref = p;
    }

    @Override
    protected String doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e("HttpsRequestHandler.doInBackGround", "Unexpected number of params.");
            return "";
        }

        if (params[1] instanceof DummySetup) {
            // ignore
            return "";
        }

        if (!(params[0] instanceof Action && params[1] instanceof SphincterSetup)) {
            Log.e("HttpsRequestHandler.doInBackground", "Invalid type of params.");
            return "";
        }

        Action action = (Action) params[0];
        SphincterSetup setup = (SphincterSetup) params[1];

        if (setup.url.isEmpty() || setup.getId() < 0) {
            Log.w("HttpsRequestHandler.doInBackground", "Invalid url for id:  " + setup.getId());
            return "Empty URL.";
        }

        String url = setup.url;
        switch (action) {
            case open_door:
                url += "?action=open";
                break;
            case close_door:
                url += "?action=close";
                break;
            case update_state:
                url += "?action=state";
        }

        url += "&token=" + URLEncoder.encode(setup.token);

        try {
            if (setup.ignore) {
                HttpsTrustManager.setVerificationDisable();
            } else {
                HttpsTrustManager.setVerificationEnable();
            }

            HttpURLConnection con = (HttpURLConnection) (new URL(url)).openConnection();
            con.setConnectTimeout(2000);

            return readStream(con.getInputStream());
        } catch (MalformedURLException mue) {
            return "Malformed URL.";
        } catch (FileNotFoundException e) {
            return "Server responds with an error.";
        } catch (java.net.SocketTimeoutException ste) {
            return "Server not reachable.";
        } catch (java.net.SocketException se) {
            // not connected to a network
            return "";
        } catch (Exception e) {
            //e.printStackTrace();
            return "Unknown Error: " + e.toString();
        }
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
