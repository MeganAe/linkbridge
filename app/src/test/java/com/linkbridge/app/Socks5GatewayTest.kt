package com.linkbridge.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
            port = Socks5Gateway.DEFAULT_PORT,
            pairingCode = "123456",
            advertisedHost = "127.0.0.1"
        )
        gateway.start()

        try {
            waitForPort(Socks5Gateway.DEFAULT_PORT)
            Socket("127.0.0.1", Socks5Gateway.DEFAULT_PORT).use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                output.write(byteArrayOf(5, 1, 2))
                output.flush()
                assertEquals(5, input.readUnsignedByte())
                assertEquals(2, input.readUnsignedByte())

                output.writeByte(1)
                val username = "linkbridge".toByteArray()
                output.writeByte(username.size)
                output.write(username)
                output.writeByte(6)
                output.write("123456".toByteArray())
                output.flush()
                assertEquals(1, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())

                output.writeByte(5)
                output.writeByte(1)
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

    private fun waitForPort(port: Int) {
        repeat(50) {
            try {
                Socket("127.0.0.1", port).use { return }
            } catch (_: Exception) {
                Thread.sleep(20)
            }
        }
        error("Gateway did not start")
    }
}
