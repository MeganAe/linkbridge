package com.linkbridge.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A small, authenticated SOCKS5 gateway that runs on phone A.
 *
 * It is deliberately bound only to the Wi-Fi Direct group interface by the
 * caller's network topology. The password is the one-time pairing code shown
 * by the app. TCP CONNECT and UDP ASSOCIATE are supported so the VPN side can
 * carry normal browsing, DNS and apps that use UDP.
 */
class Socks5Gateway(
    private val port: Int,
    private val pairingCode: String,
    private val advertisedHost: String,
    private val bindHost: String = advertisedHost
) {
    private val running = AtomicBoolean(false)
    private val workers: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acceptThread = thread(name = "linkbridge-socks-accept", start = true) {
            try {
                while (running.get() && serverSocket == null) {
                    try {
                        serverSocket = ServerSocket().apply {
                            reuseAddress = true
                            bind(InetSocketAddress(InetAddress.getByName(bindHost), port))
                        }
                    } catch (_: IOException) {
                        try {
                            Thread.sleep(250)
                        } catch (_: InterruptedException) {
                            return@thread
                        }
                    }
                }
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
        try {
            handleClientUnsafe(client)
        } catch (_: Throwable) {
            // Client disconnects (EOF, reset) and shutdown interrupts are
            // normal; they must never crash the app from a worker thread.
        }
    }

    private fun handleClientUnsafe(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 30_000
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            if (!authenticate(input, output)) return
            socket.soTimeout = 0

            val version = input.readUnsignedByte()
            val command = input.readUnsignedByte()
            input.readUnsignedByte() // reserved
            val addressType = input.readUnsignedByte()
            if (version != SOCKS_VERSION) return

            val target = readAddress(input, addressType) ?: run {
                writeReply(output, REP_ADDRESS_TYPE_NOT_SUPPORTED, null, 0)
                return
            }

            when (command) {
                CMD_CONNECT -> handleConnect(socket, input, output, target)
                CMD_UDP_ASSOCIATE -> handleUdpAssociate(socket, output)
                else -> writeReply(output, REP_COMMAND_NOT_SUPPORTED, null, 0)
            }
        }
    }

    private fun authenticate(input: DataInputStream, output: DataOutputStream): Boolean {
        if (input.readUnsignedByte() != SOCKS_VERSION) return false
        val methodCount = input.readUnsignedByte()
        val methods = ByteArray(methodCount)
        input.readFully(methods)
        if (!methods.contains(METHOD_USER_PASS.toByte())) {
            output.write(byteArrayOf(SOCKS_VERSION.toByte(), METHOD_NO_ACCEPTABLE.toByte()))
            output.flush()
            return false
        }
        output.write(byteArrayOf(SOCKS_VERSION.toByte(), METHOD_USER_PASS.toByte()))
        output.flush()

        if (input.readUnsignedByte() != AUTH_VERSION) return false
        val userLength = input.readUnsignedByte()
        val username = ByteArray(userLength)
        input.readFully(username)
        val passwordLength = input.readUnsignedByte()
        val password = ByteArray(passwordLength)
        input.readFully(password)

        val accepted = username.toString(Charsets.UTF_8) == USERNAME &&
            password.toString(Charsets.UTF_8) == pairingCode
        output.write(byteArrayOf(AUTH_VERSION.toByte(), if (accepted) 0 else 1))
        output.flush()
        return accepted
    }

    private fun handleConnect(
        client: Socket,
        input: DataInputStream,
        output: DataOutputStream,
        target: SocksAddress
    ) {
        val upstream = try {
            Socket().apply {
                connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS)
                tcpNoDelay = true
            }
        } catch (_: IOException) {
            writeReply(output, REP_CONNECTION_REFUSED, null, 0)
            return
        }

        upstream.use { remote ->
            writeReply(output, REP_SUCCEEDED, remote.localAddress, remote.localPort)
            output.flush()
            relay(client, remote)
        }
    }

    private fun relay(client: Socket, remote: Socket) {
        val forward = thread(name = "linkbridge-socks-up", start = true) {
            copyAndClose(client, remote)
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

    private fun copyAndClose(source: Socket, destination: Socket) {
        try {
            source.getInputStream().copyTo(destination.getOutputStream())
            destination.shutdownOutput()
        } catch (_: IOException) {
        }
    }

    private fun handleUdpAssociate(control: Socket, output: DataOutputStream) {
        val udp = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
                soTimeout = 1_000
            }
        } catch (_: SocketException) {
            writeReply(output, REP_GENERAL_FAILURE, null, 0)
            return
        }

        udp.use { datagramSocket ->
            writeReply(output, REP_SUCCEEDED, InetAddress.getByName(advertisedHost), datagramSocket.localPort)
            output.flush()

            val clientAddress = control.inetAddress ?: return
            val routes = ConcurrentHashMap<String, InetSocketAddress>()
            val buffer = ByteArray(65_535)

            while (!control.isClosed && running.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    datagramSocket.receive(packet)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    break
                }

                if (packet.address.hostAddress == clientAddress.hostAddress) {
                    val request = parseUdpRequest(packet.data, packet.offset, packet.length) ?: continue
                    val resolved = try {
                        InetAddress.getAllByName(request.address.host).firstOrNull()
                    } catch (_: IOException) {
                        null
                    } ?: continue

                    val destination = InetSocketAddress(resolved, request.address.port)
                    routes[endpointKey(resolved, request.address.port)] =
                        InetSocketAddress(packet.address, packet.port)
                    val payload = DatagramPacket(
                        request.payload,
                        request.payload.size,
                        destination.address,
                        destination.port
                    )
                    try {
                        datagramSocket.send(payload)
                    } catch (_: IOException) {
                    }
                } else {
                    val destination = routes[endpointKey(packet.address, packet.port)] ?: continue
                    val response = buildUdpResponse(packet.address, packet.port, packet.data, packet.offset, packet.length)
                    val reply = DatagramPacket(response, response.size, destination.address, destination.port)
                    try {
                        datagramSocket.send(reply)
                    } catch (_: IOException) {
                    }
                }
            }
        }
    }

    private fun parseUdpRequest(data: ByteArray, offset: Int, length: Int): UdpRequest? {
        if (length < 4) return null
        val input = DataInputStream(ByteArrayInputStream(data, offset, length))
        if (input.readUnsignedByte() != 0 || input.readUnsignedByte() != 0) return null
        if (input.readUnsignedByte() != 0) return null // fragmentation is not supported
        val addressType = input.readUnsignedByte()
        val address = readAddress(input, addressType) ?: return null
        val consumed = length - input.available()
        val payloadLength = length - consumed
        if (payloadLength <= 0) return null
        val payload = ByteArray(payloadLength)
        input.readFully(payload)
        return UdpRequest(address, payload)
    }

    private fun buildUdpResponse(
        address: InetAddress,
        port: Int,
        data: ByteArray,
        offset: Int,
        length: Int
    ): ByteArray {
        val output = ByteArrayOutputStream(length + 16)
        val out = DataOutputStream(output)
        out.writeShort(0)
        out.writeByte(0)
        val bytes = address.address
        if (bytes.size == 4) {
            out.writeByte(ATYP_IPV4)
            out.write(bytes)
        } else {
            out.writeByte(ATYP_IPV6)
            out.write(bytes)
        }
        out.writeShort(port)
        out.write(data, offset, length)
        out.flush()
        return output.toByteArray()
    }

    private fun readAddress(input: DataInputStream, addressType: Int): SocksAddress? {
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
        return SocksAddress(host, input.readUnsignedShort())
    }

    private fun writeReply(output: DataOutputStream, code: Int, address: InetAddress?, port: Int) {
        output.writeByte(SOCKS_VERSION)
        output.writeByte(code)
        output.writeByte(0)
        val bytes = address?.address ?: byteArrayOf(0, 0, 0, 0)
        if (bytes.size == 16) {
            output.writeByte(ATYP_IPV6)
        } else {
            output.writeByte(ATYP_IPV4)
        }
        output.write(bytes)
        output.writeShort(port)
        output.flush()
    }

    private fun endpointKey(address: InetAddress, port: Int): String =
        "${address.hostAddress}:$port"

    private fun ByteArray.contains(value: Byte): Boolean = any { it == value }

    private data class SocksAddress(val host: String, val port: Int)
    private data class UdpRequest(val address: SocksAddress, val payload: ByteArray)

    companion object {
        const val DEFAULT_PORT = 39_876
        const val USERNAME = "linkbridge"
        private const val SOCKS_VERSION = 5
        private const val AUTH_VERSION = 1
        private const val METHOD_USER_PASS = 2
        private const val METHOD_NO_ACCEPTABLE = 0xFF
        private const val CMD_CONNECT = 1
        private const val CMD_UDP_ASSOCIATE = 3
        private const val ATYP_IPV4 = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6 = 4
        private const val REP_SUCCEEDED = 0
        private const val REP_GENERAL_FAILURE = 1
        private const val REP_CONNECTION_REFUSED = 5
        private const val REP_COMMAND_NOT_SUPPORTED = 7
        private const val REP_ADDRESS_TYPE_NOT_SUPPORTED = 8
        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}
