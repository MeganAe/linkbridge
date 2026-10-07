package com.linkbridge.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Socks5GatewayTest {
    @Test
    fun authenticatedConnectRelaysTcpBytes() {
        val upstream = ServerSocket(0)
        val upstreamThread = thread(start = true) {
            upstream.accept().use { socket ->
                val data = ByteArray(5)
                DataInputStream(socket.getInputStream()).readFully(data)
                socket.getOutputStream().write(data)
                socket.getOutputStream().flush()
            }
        }
        val gateway = Socks5Gateway(
            port = 0,
            pairingCode = "123456",
            advertisedHost = "127.0.0.1"
        )
        val port = gateway.startAndAwait()

        try {
            Socket("127.0.0.1", port).use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                assertTrue(authenticate(input, output, "linkbridge", "123456"))

                output.writeByte(5)
                output.writeByte(1) // CONNECT
                output.writeByte(0)
                output.writeByte(1)
                output.write(byteArrayOf(127, 0, 0, 1))
                output.writeShort(upstream.localPort)
                output.flush()

                assertEquals(5, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())
                input.readUnsignedByte()
                val addressType = input.readUnsignedByte()
                assertEquals(1, addressType)
                input.skipBytes(4)
                input.readUnsignedShort()

                output.write("hello".toByteArray())
                output.flush()
                val response = ByteArray(5)
                input.readFully(response)
                assertArrayEquals("hello".toByteArray(), response)
            }
        } finally {
            gateway.stop()
            upstream.close()
            upstreamThread.join(2_000)
        }
    }

    @Test
    fun wrongPasswordIsRejected() {
        val gateway = Socks5Gateway(
            port = 0,
            pairingCode = "123456",
            advertisedHost = "127.0.0.1"
        )
        val port = gateway.startAndAwait()

        try {
            Socket("127.0.0.1", port).use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                assertFalse(authenticate(input, output, "linkbridge", "000000"))
                // The server closes the connection after a failed login.
                assertEquals(-1, input.read())
            }
        } finally {
            gateway.stop()
        }
    }

    @Test
    fun methodOtherThanUserPassIsRejected() {
        val gateway = Socks5Gateway(
            port = 0,
            pairingCode = "123456",
            advertisedHost = "127.0.0.1"
        )
        val port = gateway.startAndAwait()

        try {
            Socket("127.0.0.1", port).use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                // Offer only "no authentication": not acceptable here.
                output.write(byteArrayOf(5, 1, 0))
                output.flush()
                assertEquals(5, input.readUnsignedByte())
                assertEquals(0xFF, input.readUnsignedByte())
            }
        } finally {
            gateway.stop()
        }
    }

    @Test
    fun unsupportedCommandRepliesCommandNotSupported() {
        val gateway = Socks5Gateway(
            port = 0,
            pairingCode = "123456",
            advertisedHost = "127.0.0.1"
        )
        val port = gateway.startAndAwait()

        try {
            Socket("127.0.0.1", port).use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                assertTrue(authenticate(input, output, "linkbridge", "123456"))

                output.writeByte(5)
                output.writeByte(2) // BIND, unsupported
                output.writeByte(0)
                output.writeByte(1)
                output.write(byteArrayOf(127, 0, 0, 1))
                output.writeShort(1)
                output.flush()

                assertEquals(5, input.readUnsignedByte())
                assertEquals(7, input.readUnsignedByte()) // command not supported
            }
        } finally {
            gateway.stop()
        }
    }

    @Test
    fun udpAssociateRelaysDatagrams() {
        // Local echo server playing the role of a UDP destination (DNS…).
        val echo = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val echoThread = thread(start = true) {
            try {
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                echo.receive(packet)
                echo.send(DatagramPacket(packet.data, packet.length, packet.address, packet.port))
            } catch (_: Exception) {
            }
        }

        val gateway = Socks5Gateway(
            port = 0,
            pairingCode = "123456",
            advertisedHost = "127.0.0.1"
        )
        val port = gateway.startAndAwait()

        try {
            Socket("127.0.0.1", port).use { control ->
                val input = DataInputStream(control.getInputStream())
                val output = DataOutputStream(control.getOutputStream())
                assertTrue(authenticate(input, output, "linkbridge", "123456"))

                // UDP ASSOCIATE for "any peer"; the reply advertises the relay.
                output.writeByte(5)
                output.writeByte(3)
                output.writeByte(0)
                output.writeByte(1)
                output.write(byteArrayOf(0, 0, 0, 0))
                output.writeShort(0)
                output.flush()

                assertEquals(5, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())
                input.readUnsignedByte()
                assertEquals(1, input.readUnsignedByte())
                val relayAddress = ByteArray(4)
                input.readFully(relayAddress)
                val relayPort = input.readUnsignedShort()
                assertTrue(relayPort > 0)

                val payload = "hello-udp".toByteArray()
                val requestBytes = ByteArrayOutputStream()
                val request = DataOutputStream(requestBytes)
                request.writeShort(0)
                request.writeByte(0) // no fragmentation
                request.writeByte(1) // IPv4
                request.write(byteArrayOf(127, 0, 0, 1))
                request.writeShort(echo.localPort)
                request.write(payload)
                request.flush()

                DatagramSocket(null).use { clientUdp ->
                    clientUdp.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
                    clientUdp.soTimeout = 5_000
                    val requestPacket = DatagramPacket(
                        requestBytes.toByteArray(),
                        requestBytes.size(),
                        InetAddress.getByAddress(relayAddress),
                        relayPort
                    )
                    clientUdp.send(requestPacket)

                    val buffer = ByteArray(2048)
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    clientUdp.receive(responsePacket)

                    val response = DataInputStream(
                        ByteArrayInputStream(responsePacket.data, responsePacket.offset, responsePacket.length)
                    )
                    assertEquals(0, response.readUnsignedShort())
                    assertEquals(0, response.readUnsignedByte()) // no fragmentation
                    assertEquals(1, response.readUnsignedByte()) // IPv4
                    response.skipBytes(4)
                    response.readUnsignedShort()
                    val echoed = ByteArray(responsePacket.length - 10)
                    response.readFully(echoed)
                    assertArrayEquals(payload, echoed)
                }
            }
        } finally {
            gateway.stop()
            echo.close()
            echoThread.join(2_000)
        }
    }

    private fun authenticate(
        input: DataInputStream,
        output: DataOutputStream,
        username: String,
        password: String
    ): Boolean {
        output.write(byteArrayOf(5, 1, 2))
        output.flush()
        assertEquals(5, input.readUnsignedByte())
        assertEquals(2, input.readUnsignedByte())

        val user = username.toByteArray()
        val pass = password.toByteArray()
        output.writeByte(1)
        output.writeByte(user.size)
        output.write(user)
        output.writeByte(pass.size)
        output.write(pass)
        output.flush()
        assertEquals(1, input.readUnsignedByte())
        return input.readUnsignedByte() == 0
    }

}
