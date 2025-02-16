package app.trigger.ssh

import app.trigger.DoorReply.ReplyCode
import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import app.trigger.*
import com.trilead.ssh2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.lang.Exception
import java.net.SocketTimeoutException
import java.security.KeyPair


class SshRequestHandler(private val listener: OnTaskCompleted, private val setup: SshDoor, private val action: MainActivity.Action)
        : Thread(), ConnectionMonitor {
    companion object {
        private const val TAG = "SshRequestHandler"
        private const val conditions = (ChannelCondition.STDOUT_DATA
                or ChannelCondition.STDERR_DATA
                or ChannelCondition.EXIT_STATUS
                or ChannelCondition.CLOSED
                or ChannelCondition.EOF)
        private const val exitConditions = (ChannelCondition.EOF
                or ChannelCondition.EXIT_STATUS
                or ChannelCondition.TIMEOUT
                or ChannelCondition.CLOSED)

        fun testPassphrase(kp: KeyPairBean?, passphrase: String): Boolean {
            return kp != null && decodeKeyPair(kp, passphrase) != null
        }

        private fun decodeKeyPair(kp: KeyPairBean, passphrase: String): KeyPair? {
            return if (KeyPairBean.KEY_TYPE_IMPORTED == kp.type) {
                // load specific key using pem format
                PEMDecoder.decode(String(kp.privateKey, Charsets.UTF_8).toCharArray(), passphrase)
            } else {
                // load using internal generated format
                val privKey = try {
                    PubkeyUtils.decodePrivate(kp.privateKey, kp.type, passphrase)
                } catch (e: Exception) {
                    Log.e(TAG, "Bad passphrase for key. Authentication failed: $e")
                    return null
                }
                val pubKey = PubkeyUtils.decodePublic(kp.publicKey, kp.type)

                // convert key to trilead format
                KeyPair(pubKey, privKey)
            }
        }

        private fun read(session: Session?, buffer: ByteArray, start: Int, len: Int, timeout_ms: Long): Int {
            var bytesRead = 0
            if (session == null) return 0
            val stdout = session.stdout
            val stderr = session.stderr

            runBlocking {
                withTimeout(timeout_ms) {
                    while (true) {
                        val newConditions = withContext(Dispatchers.IO) {
                            session.waitForCondition(conditions, timeout_ms)
                        }

                        if (newConditions and ChannelCondition.STDOUT_DATA != 0) {
                            val read = withContext(Dispatchers.IO) { stdout.read(buffer, start + bytesRead, len - bytesRead) }
                            bytesRead += read
                        }

                        if (newConditions and ChannelCondition.STDERR_DATA != 0) {
                            val discard = ByteArray(256)
                            withContext(Dispatchers.IO) {
                                while (stderr.available() > 0) {
                                    stderr.read(discard)
                                    //Log.e(TAG, "stderr: " + (new String(discard)));
                                }
                            }
                        }

                        if (newConditions and exitConditions != 0) {
                            break
                        }
                    }
                }
            }

            return bytesRead
        }

        private fun write(session: Session, command: String) {
            val stdin = session.stdin
            stdin?.write(command.toByteArray())
        }

        init {
            Log.d(TAG, "Ed25519Provider.insertIfNeeded2")
            // Since this class deals with Ed25519 keys, we need to make sure this is available.
            Ed25519Provider.insertIfNeeded()
        }
    }

    override fun run() {
        if (setup.id < 0) {
            listener.onTaskResult(setup.id, action, ReplyCode.LOCAL_ERROR, "Internal Error")
            return
        }

        val command = when (action) {
            MainActivity.Action.OPEN_DOOR -> setup.open_command
            MainActivity.Action.CLOSE_DOOR -> setup.close_command
            MainActivity.Action.RING_DOOR -> setup.ring_command
            MainActivity.Action.FETCH_STATE -> setup.state_command
        }

        val username = setup.user.ifEmpty { "root" }
        val password = setup.password
        val hostname = setup.host
        val keypair = setup.keypair
        val port = setup.port

        if (command.isEmpty()) {
            listener.onTaskResult(setup.id, action, ReplyCode.LOCAL_ERROR, "")
            return
        }

        if (hostname.isEmpty()) {
            listener.onTaskResult(setup.id, action, ReplyCode.LOCAL_ERROR, "Server address is empty.")
            return
        }

        var connection: Connection? = null
        var session : Session? = null
        try {
            connection = Connection(hostname, port)
            connection.addConnectionMonitor(this)

            val connectionInfo = connection.connect(
                null,  // host key verifier
                2000,  // connect timeout
                3000 // key exchange timeout
            )

            // authentication by key pair
            if (keypair != null) {
                val kp = decodeKeyPair(keypair, setup.passphrase_tmp)
                if (kp != null) {
                    if (connection.authenticateWithPublicKey(username, kp)) {
                        // login successful
                    } else {
                        listener.onTaskResult(setup.id, action, ReplyCode.REMOTE_ERROR, "Key was not accepted.")
                        return
                    }
                } else {
                    if (keypair.encrypted) {
                        setup.passphrase_tmp = "" // reset (incorrect) passphrase
                        listener.onTaskResult(setup.id, action, ReplyCode.LOCAL_ERROR, "Key pair passphrase was not accepted.")
                        return
                    } else {
                        listener.onTaskResult(setup.id, action, ReplyCode.LOCAL_ERROR, "Failed to decode key pair.")
                        return
                    }
                }
            }

            // authentication by password
            else if (password.isNotEmpty()) {
                if (connection.isAuthMethodAvailable(username, "password")) {
                    if (connection.authenticateWithPassword(username, password)) {
                        // login successful
                    } else {
                        listener.onTaskResult(setup.id, action, ReplyCode.REMOTE_ERROR, "Password was not accepted.")
                        return
                    }
                } else {
                    listener.onTaskResult(setup.id, action, ReplyCode.REMOTE_ERROR, "Host does not support password authentication.")
                    return
                }
            }

            // try without authentication
            else if (password.isEmpty()) {
                if (connection.authenticateWithNone(username)) {
                    // login successful
                } else {
                    listener.onTaskResult(setup.id, action, ReplyCode.REMOTE_ERROR, "Login without any credentials failed.")
                    return
                }
            }

            // final check
            if (!connection.isAuthenticationComplete) {
                listener.onTaskResult(setup.id, action, ReplyCode.REMOTE_ERROR, "Authentication failed.")
                return
            }

            session = connection.openSession()
            val buffer = ByteArray(1000)

            // clear any welcome message (stdout/stderr)
            //read(session, buffer, 0, buffer.length);
            session.execCommand(command)

            // read stdout (drop stderr)
            val bytes_read = read(session, buffer, 0, buffer.size, setup.timeout.toLong())
            val output = String(buffer, 0, bytes_read)
            val ret = session.exitStatus
            if (ret == null || ret == 0) {
                listener.onTaskResult(setup.id, action, ReplyCode.SUCCESS, output)
            } else {
                listener.onTaskResult(setup.id, action, ReplyCode.REMOTE_ERROR, output)
            }
        } catch (e: SocketTimeoutException) {
            listener.onTaskResult(setup.id, action, ReplyCode.LOCAL_ERROR, "Connection timeout. Connected to the right network?")
        } catch (e: TimeoutCancellationException) {
            listener.onTaskResult(setup.id, action, ReplyCode.LOCAL_ERROR, "Command timeout after ${setup.timeout} ms")
        } catch (e: Exception) {
            listener.onTaskResult(setup.id, action, ReplyCode.LOCAL_ERROR, e.message!!)
            Log.e(TAG, "Problem in SSH connection thread during authentication: $e")
        } finally {
            session?.close()
            connection?.close()
        }
    }

    override fun connectionLost(reason: Throwable) {
        Log.d(TAG, "connectionLost")
    }
}