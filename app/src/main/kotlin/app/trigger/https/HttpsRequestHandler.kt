package app.trigger.https

import app.trigger.Utils.getSocketFactoryWithCertificateAndClientKey
import app.trigger.Utils.getSocketFactoryWithCertificate
import app.trigger.Utils.readInputStreamWithTimeout
import app.trigger.DoorReply.ReplyCode
import app.trigger.ssh.PubkeyUtils
import android.util.Base64
import app.trigger.*
import java.io.FileNotFoundException
import java.lang.Exception
import java.net.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*

class HttpsRequestHandler(private val listener: OnTaskCompleted, private val setup: HttpsDoor, private val action: MainActivity.Action) : Thread() {
    override fun run() {
        if (setup.id < 0) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Internal Error")
            return
        }

        var command = when (action) {
            MainActivity.Action.OPEN_DOOR -> setup.open_query
            MainActivity.Action.RING_DOOR -> setup.ring_query
            MainActivity.Action.CLOSE_DOOR -> setup.close_query
            MainActivity.Action.FETCH_STATE -> setup.status_query
            else -> ""
        }

        if (command.isEmpty()) {
            // ignore
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "")
            return
        }

        var method = when (action) {
            MainActivity.Action.OPEN_DOOR -> setup.open_method
            MainActivity.Action.RING_DOOR -> setup.ring_method
            MainActivity.Action.CLOSE_DOOR -> setup.close_method
            MainActivity.Action.FETCH_STATE -> setup.status_method
            else -> ""
        }

        if (method.isEmpty()) {
            // ignore
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "")
            return
        }

        try {
            val url = URL(command)
            val con = url.openConnection() as HttpURLConnection

            if (url.userInfo != null) {
                val basicAuth = "Basic " + Base64.encodeToString(url.userInfo.toByteArray(), Base64.NO_WRAP)
                con.setRequestProperty("Authorization", basicAuth)
            }

            if (con is HttpsURLConnection) {
                val https = con

                // hostname verification
                if (setup.ignore_hostname_mismatch) {
                    // ignore hostname mismatch
                    https.hostnameVerifier = HostnameVerifier { hostname: String?, session: SSLSession? -> true }
                }

                if (setup.server_certificate != null) {
                    // use given certificate only
                    val client_keypair = setup.client_keypair
                    val client_certificate = setup.client_certificate
                    if (client_keypair != null && client_certificate != null) {
                        val client_private_key = PubkeyUtils.decodePrivate(
                                client_keypair.privateKey, client_keypair.type
                        )
                        https.sslSocketFactory = getSocketFactoryWithCertificateAndClientKey(
                                setup.server_certificate, client_certificate, client_private_key)
                    } else if (setup.client_keypair == null && setup.client_certificate == null) {
                        if (setup.ignore_certificate) {
                            // disable entire certificate validity
                            val context = SSLContext.getInstance("TLS")
                            context.init(null, arrayOf<X509TrustManager>(object : X509TrustManager {
                                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                                override fun getAcceptedIssuers(): Array<X509Certificate> {
                                    return arrayOf<X509Certificate>()
                                }
                            }), SecureRandom())
                            https.sslSocketFactory = context.socketFactory
                        } else if (setup.ignore_expiration) {
                            // ignore notBefore/notAfter
                            https.sslSocketFactory = HttpsTools.getSocketFactoryIgnoreCertificateExpiredException()
                        } else {
                            https.sslSocketFactory = getSocketFactoryWithCertificate(setup.server_certificate)
                        }
                    } else {
                        throw Exception("Both client key and client certificate needed.")
                    }
                } else {
                    // use system certificate
                    https.sslSocketFactory = SSLContext.getDefault().socketFactory
                }
            }

            con.connectTimeout = 2500
            if (method.isNotEmpty()) {
                // make sure it is e.g. "GET" instead of "get"
                con.requestMethod = method.uppercase(Locale.getDefault())
            }

            if (con.responseCode == 200) {
                val result = readInputStreamWithTimeout(con.inputStream, 50000, 2500)
                listener.onTaskResult(setup.id, ReplyCode.SUCCESS, result)
            } else {
                val result = readInputStreamWithTimeout(con.errorStream, 50000, 2500)
                if (result.isNotEmpty()) {
                    listener.onTaskResult(setup.id, ReplyCode.REMOTE_ERROR, result)
                } else {
                    listener.onTaskResult(setup.id, ReplyCode.REMOTE_ERROR, con.responseMessage)
                }
            }
        } catch (mue: MalformedURLException) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Malformed URL.")
        } catch (e: FileNotFoundException) {
            listener.onTaskResult(setup.id, ReplyCode.REMOTE_ERROR, "Server responds with an error.")
        } catch (ste: SocketTimeoutException) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Server not reachable.")
        } catch (ce: ConnectException) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Failed to connect.")
        } catch (uhe: UnknownHostException) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, uhe.message ?: uhe.toString())
        } catch (se: SocketException) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, "Not connected to network.")
            //} catch (java.security.cert.CertPathValidatorException e) {
            //    return new DoorReply(ReplyCode.LOCAL_ERROR, "Certificate validation failed.");
        } catch (e: Exception) {
            listener.onTaskResult(setup.id, ReplyCode.LOCAL_ERROR, e.toString())
        }
    }

    companion object {
        private const val TAG = "HttpsRequestHandler"
    }
}