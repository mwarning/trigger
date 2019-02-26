package com.example.trigger.https;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.os.AsyncTask;
import android.util.Log;

import com.example.trigger.MainActivity.Action;
import com.example.trigger.HttpsDoorSetup;
import com.example.trigger.DoorReply;
import com.example.trigger.DoorReply.ReplyCode;
import com.example.trigger.OnTaskCompleted;


public class HttpsRequestHandler extends AsyncTask<Object, Void, DoorReply> {
    private OnTaskCompleted listener;

    public HttpsRequestHandler(OnTaskCompleted listener){
        this.listener = listener;
    }

    @Override
    protected DoorReply doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e("HttpsRequestHandler.doInBackGround", "Unexpected number of params.");
            return DoorReply.internal_error();
        }

        if (!(params[0] instanceof Action && params[1] instanceof HttpsDoorSetup)) {
            Log.e("HttpsRequestHandler.doInBackground", "Invalid type of params.");
            return DoorReply.internal_error();
        }

        Action action = (Action) params[0];
        HttpsDoorSetup setup = (HttpsDoorSetup) params[1];

        if (setup.getId() < 0) {
            return DoorReply.internal_error();
        }

        String command = "";

        switch (action) {
            case open_door:
                command = setup.open_query;
                break;
            case close_door:
                command = setup.close_query;
                break;
            case update_state:
                command = setup.status_query;
                break;
        }

        if (command.isEmpty()) {
            // ignore
            return new DoorReply(ReplyCode.LOCAL_ERROR, "");
        }

        try {
            if (setup.ignore_cert) {
                // ignore cert errors
                HttpsTrustManager.setVerificationDisable();
            } else {
                HttpsTrustManager.setVerificationEnable();
            }

            URL url = new URL(command);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(2000);

            String result = readStream(con.getInputStream());
            return new DoorReply(ReplyCode.SUCCESS, result);
        } catch (MalformedURLException mue) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Malformed URL.");
        } catch (FileNotFoundException e) {
            return new DoorReply(ReplyCode.REMOTE_ERROR, "Server responds with an error.");
        } catch (java.net.SocketTimeoutException ste) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Server not reachable.");
        } catch (java.net.SocketException se) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Not connected to network.");
        } catch (Exception e) {
            //e.printStackTrace();
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Unknown Error: " + e.toString());
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

    protected void onPostExecute(DoorReply result) {
        listener.onTaskCompleted(result);
    }
}
