/*
* Copyright (C) 2025 The Trigger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package app.trigger.https

import kotlin.Throws
import android.util.Base64
import app.trigger.Log
import java.io.ByteArrayInputStream
import java.lang.Exception
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*

object HttpsTools {
    private const val TAG = "HttpsTools"
    fun isValid(cert: X509Certificate): Boolean {
        return try {
            cert.checkValidity()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isSelfSigned(cert: X509Certificate): Boolean {
        return cert.issuerX500Principal.name ==
                cert.subjectX500Principal.name
    }

    fun serializeCertificate(certificate: Certificate?): String {
        if (certificate == null) {
            return ""
        }
        try {
            val prefix = "-----BEGIN CERTIFICATE-----"
            val mid = Base64.encodeToString(certificate.encoded, Base64.DEFAULT)
            val suffix = "-----END CERTIFICATE-----"
            return "${prefix}\n${mid}\n${suffix}"
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return ""
    }

    fun deserializeCertificate(certificate: String?): Certificate? {
        if (certificate.isNullOrEmpty()) {
            return null
        }
        try {
            val derInputStream = ByteArrayInputStream(certificate.toByteArray())
            val certificateFactory = CertificateFactory.getInstance("X.509") //KeyStore.getDefaultType() => "BKS"
            return certificateFactory.generateCertificate(derInputStream)
        } catch (e: Exception) {
            Log.e(this, e.toString())
        }
        return null
    }

    // disable any certificate validation
    fun disableDefaultHostnameVerifier() {
        HttpsURLConnection.setDefaultHostnameVerifier { arg0, arg1 -> true }
    }

    // disable any certificate validation
    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    fun disableDefaultCertificateValidation() {
        val trustManager: TrustManager = object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(cert: Array<X509Certificate>, authType: String) {
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(cert: Array<X509Certificate>, authType: String) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        }
        val trustManagers = arrayOf(trustManager)
        val context = SSLContext.getInstance("TLS")
        context.init(null, trustManagers, SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
    }

    @Throws(NoSuchAlgorithmException::class, KeyStoreException::class, KeyManagementException::class)
    fun getSocketFactoryIgnoreCertificateExpiredException(): SSLSocketFactory {
        val factory = TrustManagerFactory.getInstance("X509")
        factory.init(null as KeyStore?)
        val trustManagers = factory.trustManagers
        for (i in trustManagers.indices) {
            if (trustManagers[i] is X509TrustManager) {
                trustManagers[i] = IgnoreExpirationTrustManager(trustManagers[i] as X509TrustManager)
            }
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, null)
        return sslContext.socketFactory
    }
}
