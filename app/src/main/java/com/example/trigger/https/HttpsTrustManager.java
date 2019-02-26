package com.example.trigger.https;

import android.util.Log;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLContext;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLSocketFactory;


public class HttpsTrustManager implements X509TrustManager {
    private static TrustManager[] trustManagers;
    private static final X509Certificate[] _AcceptedIssuers = new X509Certificate[]{};

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
            throws java.security.cert.CertificateException {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
            throws java.security.cert.CertificateException {
    }

    public boolean isClientTrusted(X509Certificate[] chain) {
        return true;
    }

    public boolean isServerTrusted(X509Certificate[] chain) {
        return true;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return _AcceptedIssuers;
    }

    static SSLSocketFactory default_sslsocket_factory = null;
    static HostnameVerifier default_hostname_verifier = null;

    public static void setVerificationEnable() {
        setVerification(true);
    }

    public static void setVerificationDisable() {
        setVerification(false);
    }

    public static void setVerification(boolean on) {
        if (default_hostname_verifier == null) {
            default_hostname_verifier = HttpsURLConnection.getDefaultHostnameVerifier();
        }

        if (default_sslsocket_factory == null) {
            default_sslsocket_factory = HttpsURLConnection.getDefaultSSLSocketFactory();
        }

        if (on) {
            Log.i("[HttpsTrustManager]", "Verify on");
            HttpsURLConnection.setDefaultHostnameVerifier(default_hostname_verifier);
            HttpsURLConnection.setDefaultSSLSocketFactory(default_sslsocket_factory);
        } else {
            Log.i("[HttpsTrustManager]", "Verify off");
            allowAllSSL();
        }
    }

    public static void allowAllSSL() {

/*

SSLContext sc = SSLContext.getInstance("SSL");
sc.init(null, trustAllCerts, new java.security.SecureRandom());
HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
 */
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        });

        SSLContext context = null;
        if (trustManagers == null) {
            trustManagers = new TrustManager[]{new HttpsTrustManager()};
        }

        try {
            context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }

        HttpsURLConnection.setDefaultSSLSocketFactory(context != null ? context.getSocketFactory() : null);
    }
}
