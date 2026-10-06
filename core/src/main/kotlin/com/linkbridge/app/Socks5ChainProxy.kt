package com.linkbridge.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Local passwordless SOCKS5 proxy for browsers (Chrome and Edge cannot do
 * SOCKS5 authentication).
 *
 * It listens on 127.0.0.1 and chains every CONNECT through an upstream
 * LinkBridge relay (phone A or PC) using the authenticated [Socks5Client].
 * UDP ASSOCIATE is not supported in this first version.
 *
 * [onEvent] receives human-readable log lines (never passwords or codes).
 */
class Socks5ChainProxy(
    private val listenHost: String = "127.0.0.1",
    private val listenPort: Int,
    private val upstreamHost: String,
    private val upstreamPort: Int,
    private val upstreamUser: String,
    private val upstreamPass: String,
    private val onEvent: (String) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private val workers: ExecutorService = Executors.newCachedThreadPool()
    private val activeClientsCount = AtomicInteger(0)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    /** Port the proxy actually listens on (useful with [listenPort] = 0). */
    val boundPort: Int
        get() = serverSocket?.localPort ?: error("Le proxy n'est pas démarré")

    /** Number of browser connections currently being served. */
    val activeClients: Int
        get() = activeClientsCount.get()

    /** Binds synchronously so bind errors surface to the caller. */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName(listenHost), listenPort))
        }
        acceptThread = thread(name = "linkbridge-proxy-accept", start = true) {
            try {
                while (running.get()) {
                    val client = try {
                        serverSocket?.accept()
                    } catch (_: SocketException) {
                        null
                    }
                    if (client != null) {
                        try {
                            workers.execute { handleClient(client) }
                        } catch (_: RejectedExecutionException) {
                            try {
                                client.close()
                            } catch (_: IOException) {
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                running.set(false)
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        workers.shutdownNow()
        acceptThread?.interrupt()
        acceptThread = null
    }

    private fun handleClient(client: Socket) {
        activeClientsCount.incrementAndGet()
        onEvent("Navigateur connecté")
        try {
            handleClientUnsafe(client)
        } catch (_: Throwable) {
            // Browser disconnects and shutdown interrupts must never crash
            // the app from a worker thread.
        } finally {
            activeClientsCount.decrementAndGet()
            onEvent("Navigateur déconnecté")
        }
    }

    private fun handleClientUnsafe(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 30_000
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            // Browsers expect "no authentication" on 127.0.0.1.
            if (input.readUnsignedByte() != SOCKS_VERSION) return
            val methodCount = input.readUnsignedByte()
            input.skipBytes(methodCount)
            output.write(byteArrayOf(SOCKS_VERSION.toByte(), METHOD_NO_AUTH.toByte()))
            output.flush()

            val version = input.readUnsignedByte()
            val command = input.readUnsignedByte()
            input.readUnsignedByte() // reserved
            val addressType = input.readUnsignedByte()
            if (version != SOCKS_VERSION) return

            val target = readAddress(input, addressType)
            if (target == null) {
                writeReply(output, REP_ADDRESS_TYPE_NOT_SUPPORTED)
                return
            }
            if (command != CMD_CONNECT) {
                writeReply(output, REP_COMMAND_NOT_SUPPORTED)
                return
            }

            val upstream = try {
                Socks5Client.connect(
                    relayHost = upstreamHost,
                    relayPort = upstreamPort,
                    username = upstreamUser,
                    password = upstreamPass,
                    targetHost = target.host,
                    targetPort = target.port
                )
            } catch (e: IOException) {
                onEvent("Échec vers ${target.host}:${target.port} (${e.message})")
                writeReply(output, REP_CONNECTION_REFUSED)
                return
            }

            upstream.use { remote ->
                writeReply(output, REP_SUCCEEDED)
                output.flush()
                onEvent("Relais vers ${target.host}:${target.port}")
                relay(input, socket, remote)
            }
        }
    }

    private fun relay(input: InputStream, client: Socket, remote: Socket) {
        val forward = thread(name = "linkbridge-proxy-up", start = true) {
            try {
                input.copyTo(remote.getOutputStream())
                remote.shutdownOutput()
            } catch (_: IOException) {
            }
        }
        try {
            remote.getInputStream().copyTo(client.getOutputStream())
        } catch (_: IOException) {
        } finally {
            try {
                client.shutdownOutput()
            } catch (_: IOException) {
            }
            forward.join(2_000)
        }
    }

    private fun readAddress(input: DataInputStream, addressType: Int): ProxyAddress? {
        val host = when (addressType) {
            ATYP_IPV4 -> {
                val bytes = ByteArray(4)
                input.readFully(bytes)
                InetAddress.getByAddress(bytes).hostAddress
            }
            ATYP_DOMAIN -> {
                val size = input.readUnsignedByte()
                val bytes = ByteArray(size)
                input.readFully(bytes)
                bytes.toString(Charsets.UTF_8)
            }
            ATYP_IPV6 -> {
                val bytes = ByteArray(16)
                input.readFully(bytes)
                InetAddress.getByAddress(bytes).hostAddress
            }
            else -> return null
        }
        return ProxyAddress(host, input.readUnsignedShort())
    }

    private fun writeReply(output: DataOutputStream, code: Int) {
        output.writeByte(SOCKS_VERSION)
        output.writeByte(code)
        output.writeByte(0)
        output.writeByte(ATYP_IPV4)
        output.write(byteArrayOf(0, 0, 0, 0))
        output.writeShort(0)
        output.flush()
    }

    private data class ProxyAddress(val host: String, val port: Int)

    companion object {
        const val DEFAULT_PORT = 1080
        private const val SOCKS_VERSION = 5
        private const val METHOD_NO_AUTH = 0
        private const val CMD_CONNECT = 1
        private const val ATYP_IPV4 = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6 = 4
        private const val REP_SUCCEEDED = 0
        private const val REP_CONNECTION_REFUSED = 5
        private const val REP_COMMAND_NOT_SUPPORTED = 7
        private const val REP_ADDRESS_TYPE_NOT_SUPPORTED = 8
    }
}
