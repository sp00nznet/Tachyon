package xyz.znix.xftl.net

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The co-operative multiplayer session.
 *
 * Host-authoritative: the host runs the real simulation and streams game-state
 * snapshots to the client, which renders them. A length-prefixed message
 * protocol carries pings and snapshots over a single TCP connection, with one
 * daemon thread reading and one writing.
 *
 * The UI thread only reads the volatile fields and calls [host] / [join] /
 * [disconnect] / [sendSnapshot].
 */
object Multiplayer {
    /** Bumped whenever the wire protocol changes incompatibly. */
    const val PROTOCOL_VERSION = 2

    const val DEFAULT_PORT = 7777

    private const val MAGIC = "TACHYON-MP"

    // Message types.
    private const val MSG_PING = 0
    private const val MSG_SNAPSHOT = 1

    // Reject absurd message sizes rather than allocating a huge buffer.
    private const val MAX_MESSAGE = 64 * 1024 * 1024

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

    /** The most recently received game-state snapshot, for the client to render. */
    @Volatile
    var latestSnapshot: ByteArray? = null
        private set

    /** Increments each time a new snapshot arrives, so consumers can detect one. */
    @Volatile
    var snapshotVersion: Int = 0
        private set

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private val outQueue = LinkedBlockingQueue<Pair<Int, ByteArray>>()

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
        latestSnapshot = null
        outQueue.clear()
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

    /** Queue a game-state snapshot to send to the peer (host -> client). */
    fun sendSnapshot(data: ByteArray) {
        if (state != State.CONNECTED)
            return
        // Only the freshest snapshot matters - drop any still queued.
        outQueue.removeIf { it.first == MSG_SNAPSHOT }
        outQueue.offer(MSG_SNAPSHOT to data)
    }

    private fun startThread(body: () -> Unit) {
        val thread = Thread {
            try {
                body()
            } catch (ex: Exception) {
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
        val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
        val input = DataInputStream(BufferedInputStream(s.getInputStream()))

        // Handshake - exchange the magic string and protocol version.
        out.writeUTF(MAGIC)
        out.writeInt(PROTOCOL_VERSION)
        out.flush()

        if (input.readUTF() != MAGIC)
            throw Exception("the other end isn't a Tachyon game")
        val theirVersion = input.readInt()
        if (theirVersion != PROTOCOL_VERSION)
            throw Exception("version mismatch (theirs $theirVersion, ours $PROTOCOL_VERSION)")

        latestSnapshot = null
        outQueue.clear()
        state = State.CONNECTED
        status = if (hostSide) "Connected - a player joined your game" else "Connected to the host"

        // Writer thread: drains the outgoing queue, pinging when idle.
        val writer = Thread {
            try {
                while (state == State.CONNECTED) {
                    val msg = outQueue.poll(2, TimeUnit.SECONDS)
                    if (msg == null) {
                        out.writeInt(MSG_PING)
                        out.writeInt(0)
                    } else {
                        out.writeInt(msg.first)
                        out.writeInt(msg.second.size)
                        out.write(msg.second)
                    }
                    out.flush()
                }
            } catch (_: Exception) {
                // The reader loop handles the disconnect state.
            }
        }
        writer.name = "Tachyon multiplayer writer"
        writer.isDaemon = true
        writer.start()

        // Reader loop (this thread): read framed messages until the link drops.
        while (state == State.CONNECTED) {
            val type: Int
            val length: Int
            try {
                type = input.readInt()
                length = input.readInt()
            } catch (ex: Exception) {
                if (state == State.CONNECTED) {
                    state = State.IDLE
                    status = "The other player disconnected"
                }
                return
            }

            if (length < 0 || length > MAX_MESSAGE)
                throw Exception("bad message length: $length")

            val data = ByteArray(length)
            input.readFully(data)

            if (type == MSG_SNAPSHOT) {
                latestSnapshot = data
                snapshotVersion++
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
