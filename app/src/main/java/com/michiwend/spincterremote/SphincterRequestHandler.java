package com.michiwend.spincterremote;


import android.os.AsyncTask;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

enum Action {
    open_door,
    close_door,
    update_state
}

public class SphincterRequestHandler extends AsyncTask<Action, Void, String> {

    private OnTaskCompleted listener;

    public SphincterRequestHandler(OnTaskCompleted listener){
        this.listener=listener;
    }

    @Override
    protected String doInBackground(Action... params) {
        // FIXME: Get BaseURL from settings
        // FIXME: Use URI Class and its methods to build url

        return CallSphincterAPI("http://files.michiwend.com/fakeapi/sphincter");
    }

    final String CallSphincterAPI(String urlstr) {
        try {
            URL url = new URL(urlstr);
            HttpURLConnection con = (HttpURLConnection) url
                    .openConnection();
            return readStream(con.getInputStream());
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
