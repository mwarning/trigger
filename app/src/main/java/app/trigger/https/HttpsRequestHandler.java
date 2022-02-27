package app.trigger.https;

import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import android.util.Base64;

import app.trigger.MainActivity.Action;
import app.trigger.HttpsDoorSetup;
import app.trigger.DoorReply.ReplyCode;
import app.trigger.OnTaskCompleted;
import app.trigger.Utils;
import app.trigger.Log;
import app.trigger.WifiTools;


public class HttpsRequestHandler extends Thread {
    private static final String TAG = "HttpsRequestHandler";
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

        if (WifiTools.isConnected()) {
            String current_ssid = WifiTools.getCurrentSSID();
            if (setup.ssids.length() > 0 && !WifiTools.matchSSID(setup.ssids, current_ssid)) {
                this.listener.onTaskResult(setup.getId(), ReplyCode.DISABLED,
                    "SSID mismatch<br/>(connected to '" + current_ssid + "')");
                return;
            }
        } else {
            if (setup.require_wifi) {
                this.listener.onTaskResult(setup.getId(), ReplyCode.DISABLED, "Wifi Disabled");
                return;
            }
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
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            if (url.getUserInfo() != null) {
                String basicAuth = "Basic " + Base64.encodeToString(url.getUserInfo().getBytes(), Base64.NO_WRAP);
                con.setRequestProperty("Authorization", basicAuth);
            }

            if (con instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) con;

                // hostname verification
                if (setup.ignore_hostname_mismatch) {
                    // ignore hostname mismatch
                    https.setHostnameVerifier((String hostname, SSLSession session) -> true);
                }

                if (setup.ignore_certificate) {
                    // disable entire certificate validity
                    SSLContext context = SSLContext.getInstance("TLS");
                    context.init(null, new X509TrustManager[]{new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                        public void checkServerTrusted(X509Certificate[] chain,
                                        String authType) throws CertificateException {}
                        public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                        }}}, new SecureRandom());
                    https.setSSLSocketFactory(context.getSocketFactory());
                } else if (setup.certificate != null) {
                    // use custom certificate
                    https.setSSLSocketFactory(
                        Utils.getSocketFactoryWithCertificate(setup.certificate)
                    );
                } else if (setup.ignore_expiration) {
                    // ignore notBefore/notAfter
                    https.setSSLSocketFactory(
                        HttpsTools.getSocketFactoryIgnoreCertificateExpiredException()
                    );
                } else {
                    // use system certificate
                    https.setSSLSocketFactory(
                        SSLContext.getDefault().getSocketFactory()
                    );
                }
            }

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
        //    return new DoorReply(ReplyCode.LOCAL_ERROR, "Certificate validation failed.");
        } catch (Exception e) {
            this.listener.onTaskResult(setup.getId(), ReplyCode.LOCAL_ERROR, e.toString());
        }
    }
}
