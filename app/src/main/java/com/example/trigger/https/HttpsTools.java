package com.example.trigger.https;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.example.trigger.Log;


public class HttpsTools {

    public static boolean isValid(X509Certificate cert) {
        try {
            cert.checkValidity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSelfSigned(X509Certificate cert) {
        return cert.getIssuerX500Principal().getName().equals(
            cert.getSubjectX500Principal().getName()
        );
    }

    public static String serializeCertificate(Certificate cert) {
        if (cert == null) {
            return "";
        }

        try {
            return "-----BEGIN CERTIFICATE-----\n"
                + Base64.encodeToString(cert.getEncoded(), Base64.DEFAULT)
                + "\n-----END CERTIFICATE-----\n";
        } catch (Exception e) {
            Log.e("HttpsTools", e.toString());
        }

        return "";
    }

    public static Certificate deserializeCertificate(String cert) {
        if (cert == null || cert.length() == 0) {
            return null;
        }

        try {
            ByteArrayInputStream derInputStream = new ByteArrayInputStream(cert.getBytes());
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509"); //KeyStore.getDefaultType() => "BKS"
            return certificateFactory.generateCertificate(derInputStream);
        } catch (Exception e) {
            Log.e("HttpsTools", e.toString());
        }

        return null;
    }

    // disable any certificate validation
    public static void disableDefaultHostnameVerifier() {
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        });
    }

    // disable any certificate validation
    public static void disableDefaultCertificateValidation()
            throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] cert, String authType)
                    throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] cert, String authType)
                    throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }
        };

        TrustManager[] trustManagers = new TrustManager[]{ trustManager };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
    }
}
