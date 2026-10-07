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
 * Local passwordless proxy for browsers and Windows (Chrome and Edge cannot do
 * SOCKS5 authentication).
 *
 * It listens on 127.0.0.1 and chains every connection through an upstream
 * LinkBridge relay (phone A or PC) using the authenticated [Socks5Client].
 * The same port speaks two protocols, told apart by the first byte received:
 *
 * - SOCKS5 (first byte 0x05): CONNECT only, UDP ASSOCIATE is not supported;
 * - HTTP proxy (anything else): `CONNECT host:port` for HTTPS and absolute-URI
 *   requests (`GET http://host/path`) for plain HTTP. This is what the Windows
 *   system proxy setting sends, so already-open browsers can use the relay.
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
            val buffered = BufferedInputStream(socket.getInputStream())
            // Peek at the first byte without consuming it: 0x05 is a SOCKS5
            // greeting, anything else is treated as an HTTP proxy request.
            buffered.mark(1)
            val first = buffered.read()
            if (first == -1) return
            buffered.reset()
            val input = DataInputStream(buffered)
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            if (first == SOCKS_VERSION) {
                handleSocks(socket, input, output)
            } else {
                handleHttp(socket, input, output)
            }
        }
    }

    // ----- SOCKS5 -----

    private fun handleSocks(socket: Socket, input: DataInputStream, output: DataOutputStream) {
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

        val upstream = connectUpstream(target) ?: run {
            writeReply(output, REP_CONNECTION_REFUSED)
            return
        }

        upstream.use { remote ->
            writeReply(output, REP_SUCCEEDED)
            output.flush()
            onEvent("Relais vers ${target.host}:${target.port}")
            // The handshake is over: an idle connection must not time out.
            socket.soTimeout = 0
            relay(input, socket, remote)
        }
    }

    // ----- HTTP proxy -----

    private fun handleHttp(socket: Socket, input: DataInputStream, output: DataOutputStream) {
        val requestLine = readLine(input) ?: return
        val headers = mutableListOf<String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            headers += line
            if (headers.size > MAX_HTTP_HEADERS) {
                writeHttpError(output, 431, "Request Header Fields Too Large")
                return
            }
        }

        val parts = requestLine.split(' ')
        if (parts.size < 3) {
            writeHttpError(output, 400, "Bad Request")
            return
        }
        val method = parts[0]
        val targetText = parts[1]
        val version = parts[2]

        if (method.equals("CONNECT", ignoreCase = true)) {
            val target = parseHostPort(targetText, 443)
            if (target == null) {
                writeHttpError(output, 400, "Bad Request")
                return
            }
            val upstream = connectUpstream(target) ?: run {
                writeHttpError(output, 502, "Bad Gateway")
                return
            }
            upstream.use { remote ->
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                output.flush()
                onEvent("Relais HTTPS vers ${target.host}:${target.port}")
                socket.soTimeout = 0
                relay(input, socket, remote)
            }
            return
        }

        // Plain HTTP: the request line carries an absolute URI.
        val url = parseAbsoluteHttpUrl(targetText)
        if (url == null) {
            writeHttpError(output, 400, "Bad Request")
            return
        }
        val upstream = connectUpstream(url.address) ?: run {
            writeHttpError(output, 502, "Bad Gateway")
            return
        }
        upstream.use { remote ->
            // Rewrite to origin-form and force one request per connection:
            // later requests on the same browser connection may target other
            // hosts, which a raw relay could not follow.
            val forwarded = StringBuilder()
            forwarded.append(method).append(' ').append(url.path).append(' ').append(version).append("\r\n")
            for (header in headers) {
                val name = header.substringBefore(':').trim().lowercase()
                if (name in HOP_BY_HOP_HEADERS) continue
                forwarded.append(header).append("\r\n")
            }
            forwarded.append("Connection: close\r\n\r\n")
            remote.getOutputStream().write(forwarded.toString().toByteArray(Charsets.ISO_8859_1))
            remote.getOutputStream().flush()
            onEvent("Relais HTTP vers ${url.address.host}:${url.address.port}")
            socket.soTimeout = 0
            relay(input, socket, remote)
        }
    }

    private fun connectUpstream(target: ProxyAddress): Socket? = try {
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
        null
    }

    /** Reads one header line (without CRLF); null on end of stream. */
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return null
            if (c == '\n'.code) return line.toString()
            if (c != '\r'.code) line.append(c.toChar())
            if (line.length > MAX_HTTP_LINE) throw IOException("Ligne HTTP trop longue")
        }
    }

    private fun writeHttpError(output: DataOutputStream, code: Int, text: String) {
        output.write(
            "HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.ISO_8859_1)
        )
        output.flush()
    }

    /** Parses `host`, `host:port`, `[v6]` or `[v6]:port`. */
    private fun parseHostPort(text: String, defaultPort: Int): ProxyAddress? {
        if (text.isEmpty()) return null
        val host: String
        val portText: String?
        if (text.startsWith("[")) {
            val end = text.indexOf(']')
            if (end < 0) return null
            host = text.substring(1, end)
            val rest = text.substring(end + 1)
            portText = when {
                rest.isEmpty() -> null
                rest.startsWith(":") -> rest.substring(1)
                else -> return null
            }
        } else {
            val last = text.lastIndexOf(':')
            when {
                last < 0 -> {
                    host = text
                    portText = null
                }
                text.indexOf(':') == last -> {
                    host = text.substring(0, last)
                    portText = text.substring(last + 1)
                }
                else -> return null // bare IPv6 literal without brackets
            }
        }
        if (host.isEmpty()) return null
        val port = if (portText == null) defaultPort else portText.toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return ProxyAddress(host, port)
    }

    private fun parseAbsoluteHttpUrl(text: String): HttpTarget? {
        if (!text.startsWith("http://", ignoreCase = true)) return null
        val rest = text.substring("http://".length)
        val cut = rest.indexOfFirst { it == '/' || it == '?' }
        val authority = (if (cut < 0) rest else rest.substring(0, cut)).substringAfterLast('@')
        val path = when {
            cut < 0 -> "/"
            rest[cut] == '?' -> "/" + rest.substring(cut)
            else -> rest.substring(cut)
        }
        val address = parseHostPort(authority, 80) ?: return null
        return HttpTarget(address, path)
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
    private data class HttpTarget(val address: ProxyAddress, val path: String)

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
        private const val MAX_HTTP_LINE = 16_384
        private const val MAX_HTTP_HEADERS = 100
        private val HOP_BY_HOP_HEADERS = setOf(
            "proxy-connection", "proxy-authorization", "connection", "keep-alive"
        )
    }
}
