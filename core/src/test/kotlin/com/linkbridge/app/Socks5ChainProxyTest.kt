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

    private fun startChain(gatewayPort: Int): Socks5ChainProxy =
        Socks5ChainProxy(
            listenHost = "127.0.0.1",
            listenPort = 0,
            upstreamHost = "127.0.0.1",
            upstreamPort = gatewayPort,
            upstreamUser = Socks5Gateway.USERNAME,
            upstreamPass = "123456"
        ).also { it.start() }

    private fun readHttpLine(input: DataInputStream): String {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1 || c == '\n'.code) break
            if (c != '\r'.code) line.append(c.toChar())
        }
        return line.toString()
    }

    @Test
    fun httpConnectRelaysThroughGateway() {
        val echo = ServerSocket(0)
        val echoThread = thread(start = true) {
            echo.accept().use { socket ->
                val data = ByteArray(4)
                DataInputStream(socket.getInputStream()).readFully(data)
                socket.getOutputStream().write(data)
                socket.getOutputStream().flush()
            }
        }
        val gateway = Socks5Gateway(port = 0, pairingCode = "123456", advertisedHost = "127.0.0.1")
        val port = gateway.startAndAwait()
        val proxy = startChain(port)

        try {
            Socket("127.0.0.1", proxy.boundPort).use { client ->
                client.soTimeout = 5_000
                val input = DataInputStream(client.getInputStream())
                val output = client.getOutputStream()

                // What the Windows system proxy sends for HTTPS.
                output.write(
                    "CONNECT 127.0.0.1:${echo.localPort} HTTP/1.1\r\nHost: 127.0.0.1:${echo.localPort}\r\n\r\n"
                        .toByteArray()
                )
                output.flush()
                assertTrue(readHttpLine(input).startsWith("HTTP/1.1 200"))
                assertEquals("", readHttpLine(input)) // end of response headers

                output.write("ping".toByteArray())
                output.flush()
                val response = ByteArray(4)
                input.readFully(response)
                assertArrayEquals("ping".toByteArray(), response)
            }
        } finally {
            proxy.stop()
            gateway.stop()
            echo.close()
            echoThread.join(2_000)
        }
    }

    @Test
    fun plainHttpRequestIsForwardedInOriginForm() {
        val web = ServerSocket(0)
        val seen = java.util.concurrent.LinkedBlockingQueue<List<String>>()
        val webThread = thread(start = true) {
            web.accept().use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val lines = mutableListOf<String>()
                while (true) {
                    val line = readHttpLine(input)
                    if (line.isEmpty()) break
                    lines += line
                }
                seen.add(lines)
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray()
                )
                socket.getOutputStream().flush()
            }
        }
        val gateway = Socks5Gateway(port = 0, pairingCode = "123456", advertisedHost = "127.0.0.1")
        val port = gateway.startAndAwait()
        val proxy = startChain(port)

        try {
            Socket("127.0.0.1", proxy.boundPort).use { client ->
                client.soTimeout = 5_000
                val input = DataInputStream(client.getInputStream())
                val output = client.getOutputStream()
                output.write(
                    ("GET http://127.0.0.1:${web.localPort}/hello?x=1 HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:${web.localPort}\r\n" +
                        "Proxy-Connection: keep-alive\r\n" +
                        "Accept: */*\r\n\r\n").toByteArray()
                )
                output.flush()

                assertEquals("HTTP/1.1 200 OK", readHttpLine(input))
                val rest = String(client.getInputStream().readBytes())
                assertTrue(rest.endsWith("ok"))
            }
            val request = seen.poll(2, java.util.concurrent.TimeUnit.SECONDS)!!
            assertEquals("GET /hello?x=1 HTTP/1.1", request[0])
            assertTrue(request.none { it.startsWith("Proxy-Connection", ignoreCase = true) })
            assertTrue(request.any { it.equals("Connection: close", ignoreCase = true) })
            assertTrue(request.any { it.startsWith("Accept:") })
        } finally {
            proxy.stop()
            gateway.stop()
            web.close()
            webThread.join(2_000)
        }
    }

    @Test
    fun httpConnectToUnreachableTargetReturns502() {
        val gateway = Socks5Gateway(port = 0, pairingCode = "123456", advertisedHost = "127.0.0.1")
        val port = gateway.startAndAwait()
        val proxy = startChain(port)

        try {
            Socket("127.0.0.1", proxy.boundPort).use { client ->
                client.soTimeout = 5_000
                val input = DataInputStream(client.getInputStream())
                client.getOutputStream().write(
                    "CONNECT 127.0.0.1:1 HTTP/1.1\r\nHost: 127.0.0.1:1\r\n\r\n".toByteArray()
                )
                client.getOutputStream().flush()
                assertTrue(readHttpLine(input).contains("502"))
            }
        } finally {
            proxy.stop()
            gateway.stop()
        }
    }

    @Test
    fun malformedHttpRequestReturns400() {
        val gateway = Socks5Gateway(port = 0, pairingCode = "123456", advertisedHost = "127.0.0.1")
        val port = gateway.startAndAwait()
        val proxy = startChain(port)

        try {
            Socket("127.0.0.1", proxy.boundPort).use { client ->
                client.soTimeout = 5_000
                val input = DataInputStream(client.getInputStream())
                client.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                client.getOutputStream().flush()
                // Origin-form (not an absolute URI) is not valid for a proxy.
                assertTrue(readHttpLine(input).contains("400"))
            }
        } finally {
            proxy.stop()
            gateway.stop()
        }
    }
}
