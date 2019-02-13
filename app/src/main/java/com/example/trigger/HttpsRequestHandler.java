package com.example.trigger;

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

import static com.example.trigger.Utils.*;


public class HttpsRequestHandler extends AsyncTask<Object, Void, TaskResult> {
    private OnTaskCompleted listener;

    public HttpsRequestHandler(OnTaskCompleted listener){
        this.listener = listener;
    }

    @Override
    protected TaskResult doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e("HttpsRequestHandler.doInBackGround", "Unexpected number of params.");
            return TaskResult.error("Internal Error");
        }

        if (!(params[0] instanceof Action && params[1] instanceof HttpsDoorSetup)) {
            Log.e("HttpsRequestHandler.doInBackground", "Invalid type of params.");
            return TaskResult.error("Internal Error");
        }

        Action action = (Action) params[0];
        HttpsDoorSetup setup = (HttpsDoorSetup) params[1];

        if (setup.getId() < 0) {
            return TaskResult.error("Internal Error");
        }

        String command = "";

        switch (action) {
            case open_door:
                command = setup.getOpenQuery();
                break;
            case close_door:
                command = setup.getCloseQuery();
                break;
            case update_state:
                command = setup.getStatusQuery();
                break;
        }

        if (command.isEmpty()) {
            // ignore
            return TaskResult.empty();
        }

        try {
            if (setup.ignoreCertErrors()) {
                HttpsTrustManager.setVerificationDisable();
            } else {
                HttpsTrustManager.setVerificationEnable();
            }

            URL url = new URL(command);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(2000);

            String result = readStream(con.getInputStream());
            return TaskResult.msg(result);
        } catch (MalformedURLException mue) {
            return TaskResult.error("Malformed URL.");
        } catch (FileNotFoundException e) {
            return TaskResult.error("Server responds with an error.");
        } catch (java.net.SocketTimeoutException ste) {
            return TaskResult.error("Server not reachable.");
        } catch (java.net.SocketException se) {
            return TaskResult.error("Not connected to network.");
        } catch (Exception e) {
            //e.printStackTrace();
            return TaskResult.error("Unknown Error: " + e.toString());
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

        // strip HTML from response
        return android.text.Html.fromHtml(result).toString().trim();
    }

    protected void onPostExecute(TaskResult result) {
        listener.onTaskCompleted(result);
    }
}
