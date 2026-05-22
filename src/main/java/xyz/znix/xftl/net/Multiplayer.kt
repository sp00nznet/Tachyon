package xyz.znix.xftl.net

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * The co-operative multiplayer session.
 *
 * This is the Step 1 handshake layer: it establishes a host/client TCP
 * connection, verifies the protocol version and keeps the link alive with
 * pings. Game-state sync and player commands will build on top of it.
 *
 * All networking runs on a single daemon thread; the UI thread only reads
 * the volatile [state] / [status] fields and calls [host] / [join] /
 * [disconnect].
 */
object Multiplayer {
    /** Bumped whenever the wire protocol changes incompatibly. */
    const val PROTOCOL_VERSION = 1

    const val DEFAULT_PORT = 7777

    private const val MAGIC = "TACHYON-MP"

    enum class State { IDLE, HOSTING, CONNECTING, CONNECTED, FAILED }

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var status: String = "Not connected"
        private set

    /** True if this game is the host (the authoritative simulation). */
    @Volatile
    var hosting: Boolean = false
        private set

    val isConnected: Boolean get() = state == State.CONNECTED

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    /** Start hosting, and wait for a player to join. */
    fun host() {
        if (state != State.IDLE && state != State.FAILED)
            return
        hosting = true
        state = State.HOSTING
        status = "Waiting for a player to join..."
        startThread {
            ServerSocket(DEFAULT_PORT).use { server ->
                serverSocket = server
                val accepted = server.accept()
                serverSocket = null
                runConnection(accepted, hostSide = true)
            }
        }
    }

    /** Connect to a host at the given address. */
    fun join(address: String) {
        if (state != State.IDLE && state != State.FAILED)
            return
        hosting = false
        state = State.CONNECTING
        status = "Connecting to $address..."
        startThread {
            val s = Socket()
            s.connect(InetSocketAddress(address, DEFAULT_PORT), 8000)
            runConnection(s, hostSide = false)
        }
    }

    /** Close any connection or pending host/join attempt. */
    fun disconnect() {
        state = State.IDLE
        status = "Not connected"
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        socket = null
    }

    private fun startThread(body: () -> Unit) {
        val thread = Thread {
            try {
                body()
            } catch (ex: Exception) {
                // Don't report an error if the user cancelled deliberately.
                if (state != State.IDLE) {
                    state = State.FAILED
                    status = "Connection failed: ${ex.message ?: ex.javaClass.simpleName}"
                }
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
        thread.name = "Tachyon multiplayer"
        thread.isDaemon = true
        thread.start()
    }

    private fun runConnection(s: Socket, hostSide: Boolean) {
        socket = s
        val out = DataOutputStream(s.getOutputStream())
        val input = DataInputStream(s.getInputStream())

        // Handshake - exchange the magic string and protocol version.
        out.writeUTF(MAGIC)
        out.writeInt(PROTOCOL_VERSION)
        out.flush()

        if (input.readUTF() != MAGIC)
            throw Exception("the other end isn't a Tachyon game")
        val theirVersion = input.readInt()
        if (theirVersion != PROTOCOL_VERSION)
            throw Exception("version mismatch (theirs $theirVersion, ours $PROTOCOL_VERSION)")

        state = State.CONNECTED
        status = if (hostSide) "Connected - a player joined your game" else "Connected to the host"

        // Keepalive: ping when idle, and detect a dropped peer.
        s.soTimeout = 2000
        while (state == State.CONNECTED) {
            try {
                input.readUTF() // The peer's pings - nothing to act on yet.
            } catch (timeout: SocketTimeoutException) {
                out.writeUTF("PING")
                out.flush()
            } catch (ex: Exception) {
                if (state == State.CONNECTED) {
                    state = State.IDLE
                    status = "The other player disconnected"
                }
                return
            }
        }
    }

    /** This machine's LAN address, for sharing with the joining player. */
    fun localAddress(): String {
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual)
                    continue
                for (addr in iface.inetAddresses) {
                    // Site-local IPv4 - a normal LAN address like 192.168.x.x.
                    if (addr.isSiteLocalAddress && addr.hostAddress.indexOf(':') < 0)
                        return addr.hostAddress
                }
            }
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }
}
