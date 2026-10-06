package com.linkbridge.app

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Socks5ClientTest {
    @Test
    fun probeFetchesHttpStatusThroughGateway() {
        // Fake HTTP server acting as example.com.
        val http = ServerSocket(0)
        val httpThread = thread(start = true) {
            http.accept().use { socket ->
                BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                val writer = OutputStreamWriter(socket.getOutputStream())
                writer.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok")
                writer.flush()
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
            val status = Socks5Client.probeHttp(
                relayHost = "127.0.0.1",
                relayPort = Socks5Gateway.DEFAULT_PORT,
                username = Socks5Gateway.USERNAME,
                password = "123456",
                targetHost = "127.0.0.1",
                targetPort = http.localPort
            )
            assertEquals("HTTP/1.1 200 OK", status)
        } finally {
            gateway.stop()
            http.close()
            httpThread.join(2_000)
        }
    }

    @Test
    fun wrongPasswordIsReportedClearly() {
        val gateway = Socks5Gateway(
            port = Socks5Gateway.DEFAULT_PORT,
            pairingCode = "123456",
            advertisedHost = "127.0.0.1"
        )
        gateway.start()

        try {
            waitForPort(Socks5Gateway.DEFAULT_PORT)
            try {
                Socks5Client.probeHttp(
                    relayHost = "127.0.0.1",
                    relayPort = Socks5Gateway.DEFAULT_PORT,
                    username = Socks5Gateway.USERNAME,
                    password = "000000",
                    targetHost = "127.0.0.1",
                    targetPort = 9
                )
                fail("L'authentification aurait dû être refusée")
            } catch (e: Socks5Client.Socks5Exception) {
                assertTrue(e.message!!.contains("code"))
            }
        } finally {
            gateway.stop()
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
