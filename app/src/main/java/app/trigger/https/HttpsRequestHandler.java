package app.trigger.https;

import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import javax.net.ssl.SSLContext;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import app.trigger.MainActivity.Action;
import app.trigger.HttpsDoorSetup;
import app.trigger.DoorReply.ReplyCode;
import app.trigger.OnTaskCompleted;
import app.trigger.Utils;
import app.trigger.Log;
import app.trigger.WifiTools;


public class HttpsRequestHandler extends Thread {
    private final OnTaskCompleted listener;
    private final HttpsDoorSetup setup;
    private final Action action;

    public HttpsRequestHandler(OnTaskCompleted listener, HttpsDoorSetup setup, Action action) {
        this.listener = listener;
        this.setup = setup;
        this.action = action;
    }

    public void run() {
        if (setup.getId() < 0) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Internal Error");
            return;
        }

        if (setup.require_wifi && !WifiTools.isConnected()) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.DISABLED, "Wifi Disabled.");
            return;
        }

        String command = "";

        switch (action) {
            case open_door:
                command = setup.open_query;
                break;
            case ring_door:
                command = setup.ring_query;
                break;
            case close_door:
                command = setup.close_query;
                break;
            case fetch_state:
                command = setup.status_query;
                break;
        }

        if (command.isEmpty()) {
            // ignore
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "");
            return;
        }

        try {
            URL url = new URL(command);

            // hostname verification
            if (setup.ignore_hostname_mismatch) {
                // ignore hostname mismatch
                HttpsURLConnection.setDefaultHostnameVerifier((String hostname, SSLSession session) -> true);
            } else {
                HttpsURLConnection.setDefaultHostnameVerifier((String hostname, SSLSession session) -> false);
            }

            // certificate verification
            if (setup.certificate != null) {
                // custom certificate
                HttpsURLConnection.setDefaultSSLSocketFactory(
                    Utils.getSocketFactoryWithCertificate(setup.certificate)
                );
            } else {
                // system certificate
                HttpsURLConnection.setDefaultSSLSocketFactory(
                    SSLContext.getDefault().getSocketFactory()
                );
            }

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(2500);

            if (!setup.method.isEmpty()) {
                con.setRequestMethod(setup.method.toUpperCase());
            }

            if (con.getResponseCode() == 200) {
                String result = Utils.readInputStreamWithTimeout(con.getInputStream(), 50000, 2500);
                this.listener.onTaskResult(setup.getId(), ReplyCode.SUCCESS, result);
            } else {
                String result = Utils.readInputStreamWithTimeout(con.getErrorStream(), 50000, 2500);
                if (!Utils.isEmpty(result)) {
                    this.listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, result);
                } else {
                    this.listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, con.getResponseMessage());
                }
            }
        } catch (MalformedURLException mue) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Malformed URL.");
        } catch (FileNotFoundException e) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.REMOTE_ERROR, "Server responds with an error.");
        } catch (java.net.SocketTimeoutException ste) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Server not reachable.");
        } catch (java.net.SocketException se) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, "Not connected to network.");
        //} catch (java.security.cert.CertPathValidatorException e) {
        //	return new DoorReply(ReplyCode.LOCAL_ERROR, "Certificate validation failed.");
        } catch (Exception e) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, e.toString());
        }
    }
}
