package com.linkbridge.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Socks5ChainProxyTest {
    @Test
    fun unauthenticatedBrowserRelaysThroughGateway() {
        // TCP echo server as the final destination.
        val echo = ServerSocket(0)
        val echoThread = thread(start = true) {
            echo.accept().use { socket ->
                val data = ByteArray(4)
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
        val proxy = Socks5ChainProxy(
            listenHost = "127.0.0.1",
            listenPort = 0,
            upstreamHost = "127.0.0.1",
            upstreamPort = port,
            upstreamUser = Socks5Gateway.USERNAME,
            upstreamPass = "123456"
        )
        proxy.start()

        try {
            Socket("127.0.0.1", proxy.boundPort).use { browser ->
                browser.soTimeout = 5_000
                val input = DataInputStream(browser.getInputStream())
                val output = DataOutputStream(browser.getOutputStream())

                // No-auth greeting, like Chrome or Edge.
                output.write(byteArrayOf(5, 1, 0))
                output.flush()
                assertEquals(5, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())

                output.writeByte(5)
                output.writeByte(1) // CONNECT
                output.writeByte(0)
                output.writeByte(1) // IPv4
                output.write(byteArrayOf(127, 0, 0, 1))
                output.writeShort(echo.localPort)
                output.flush()

                assertEquals(5, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())
                input.readUnsignedByte() // reserved
                assertEquals(1, input.readUnsignedByte()) // IPv4 bound address
                input.skipBytes(4 + 2) // bound address + port

                output.write("ping".toByteArray())
                output.flush()
                val response = ByteArray(4)
                input.readFully(response)
                assertArrayEquals("ping".toByteArray(), response)
            }
            assertTrue(gateway.activeClients >= 0)
        } finally {
            proxy.stop()
            gateway.stop()
            echo.close()
            echoThread.join(2_000)
        }
    }

    @Test
    fun udpAssociateIsRefusedWithClearReply() {
        val gateway = Socks5Gateway(
            port = 0,
            pairingCode = "123456",
            advertisedHost = "127.0.0.1"
        )
        val port = gateway.startAndAwait()
        val proxy = Socks5ChainProxy(
            listenHost = "127.0.0.1",
            listenPort = 0,
            upstreamHost = "127.0.0.1",
            upstreamPort = port,
            upstreamUser = Socks5Gateway.USERNAME,
            upstreamPass = "123456"
        )
        proxy.start()

        try {
            Socket("127.0.0.1", proxy.boundPort).use { browser ->
                browser.soTimeout = 5_000
                val input = DataInputStream(browser.getInputStream())
                val output = DataOutputStream(browser.getOutputStream())
                output.write(byteArrayOf(5, 1, 0))
                output.flush()
                assertEquals(5, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())

                output.writeByte(5)
                output.writeByte(3) // UDP ASSOCIATE, unsupported here
                output.writeByte(0)
                output.writeByte(1)
                output.write(byteArrayOf(0, 0, 0, 0))
                output.writeShort(0)
                output.flush()

                assertEquals(5, input.readUnsignedByte())
                assertEquals(7, input.readUnsignedByte()) // command not supported
            }
        } finally {
            proxy.stop()
            gateway.stop()
        }
    }

}
