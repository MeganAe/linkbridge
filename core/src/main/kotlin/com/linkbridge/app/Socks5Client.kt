package com.linkbridge.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal RFC 1928 / 1929 SOCKS5 client.
 *
 * Used by the desktop app to reach the Internet through a LinkBridge relay
 * (phone A or PC) with username/password authentication.
 */
object Socks5Client {
    private const val SOCKS_VERSION = 5
    private const val AUTH_VERSION = 1
    private const val METHOD_USER_PASS = 2
    private const val CMD_CONNECT = 1
    private const val ATYP_IPV4 = 1
    private const val ATYP_DOMAIN = 3
    private const val ATYP_IPV6 = 4

    class Socks5Exception(message: String) : IOException(message)

    /**
     * Opens an authenticated TCP tunnel to [targetHost]:[targetPort] through
     * the relay at [relayHost]:[relayPort]. The caller owns the returned
     * socket and its raw streams.
     */
    fun connect(
        relayHost: String,
        relayPort: Int,
        username: String,
        password: String,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int = 15_000
    ): Socket {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(relayHost, relayPort), timeoutMs)
            socket.soTimeout = timeoutMs
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            // Method negotiation: we only offer username/password.
            output.write(byteArrayOf(SOCKS_VERSION.toByte(), 1, METHOD_USER_PASS.toByte()))
            output.flush()
            val version = input.readUnsignedByte()
            val method = input.readUnsignedByte()
            if (version != SOCKS_VERSION || method != METHOD_USER_PASS) {
                throw Socks5Exception("Le relais n'accepte pas l'authentification LinkBridge")
            }

            // RFC 1929 authentication.
            val userBytes = username.toByteArray(Charsets.UTF_8)
            val passBytes = password.toByteArray(Charsets.UTF_8)
            output.writeByte(AUTH_VERSION)
            output.writeByte(userBytes.size)
            output.write(userBytes)
            output.writeByte(passBytes.size)
            output.write(passBytes)
            output.flush()
            if (input.readUnsignedByte() != AUTH_VERSION) {
                throw Socks5Exception("Réponse d'authentification invalide du relais")
            }
            if (input.readUnsignedByte() != 0) {
                throw Socks5Exception("Authentification refusée : code de liaison incorrect")
            }

            // CONNECT request.
            output.writeByte(SOCKS_VERSION)
            output.writeByte(CMD_CONNECT)
            output.writeByte(0)
            writeAddress(output, targetHost, targetPort)
            output.flush()
            if (input.readUnsignedByte() != SOCKS_VERSION) {
                throw Socks5Exception("Réponse invalide du relais")
            }
            when (val reply = input.readUnsignedByte()) {
                0 -> Unit
                1 -> throw Socks5Exception("Échec général du relais")
                2 -> throw Socks5Exception("Règles du relais refusant la connexion")
                3 -> throw Socks5Exception("Réseau injoignable")
                4 -> throw Socks5Exception("Hôte injoignable")
                5 -> throw Socks5Exception("Connexion refusée par la destination")
                7 -> throw Socks5Exception("Commande non supportée par le relais")
                8 -> throw Socks5Exception("Type d'adresse non supporté")
                else -> throw Socks5Exception("Erreur du relais (code $reply)")
            }
            skipReplyAddress(input)
            socket.soTimeout = 0
            return socket
        } catch (t: Throwable) {
            try {
                socket.close()
            } catch (_: IOException) {
            }
            throw t
        }
    }

    /**
     * Health check used by the desktop "Tester" button: fetches the first
     * line of `http://<targetHost>/` through the relay and returns it
     * (e.g. "HTTP/1.1 200 OK").
     */
    fun probeHttp(
        relayHost: String,
        relayPort: Int,
        username: String,
        password: String,
        targetHost: String = "example.com",
        targetPort: Int = 80
    ): String {
        connect(relayHost, relayPort, username, password, targetHost, targetPort).use { socket ->
            val request =
                "GET / HTTP/1.1\r\nHost: $targetHost\r\nUser-Agent: LinkBridge\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val statusLine = StringBuilder()
            while (true) {
                val c = input.read()
                if (c == -1 || c == '\n'.code) break
                if (c != '\r'.code) statusLine.append(c.toChar())
            }
            if (statusLine.isEmpty()) {
                throw Socks5Exception("Aucune réponse HTTP à travers le relais")
            }
            return statusLine.toString()
        }
    }

    private fun writeAddress(output: DataOutputStream, host: String, port: Int) {
        val literal = parseLiteral(host)
        when (literal) {
            is Inet4Address -> {
                output.writeByte(ATYP_IPV4)
                output.write(literal.address)
            }
            is Inet6Address -> {
                output.writeByte(ATYP_IPV6)
                output.write(literal.address)
            }
            else -> {
                val bytes = host.toByteArray(Charsets.UTF_8)
                if (bytes.size > 255) throw Socks5Exception("Nom d'hôte trop long")
                output.writeByte(ATYP_DOMAIN)
                output.writeByte(bytes.size)
                output.write(bytes)
            }
        }
        output.writeShort(port)
    }

    /** Parses IPv4/IPv6 literals only; hostnames are sent as domains. */
    private fun parseLiteral(host: String): InetAddress? {
        val looksLikeLiteral = host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) || host.contains(':')
        if (!looksLikeLiteral) return null
        return try {
            InetAddress.getByName(host)
        } catch (_: IOException) {
            null
        }
    }

    private fun skipReplyAddress(input: DataInputStream) {
        when (input.readUnsignedByte()) {
            ATYP_IPV4 -> input.skipBytes(4)
            ATYP_DOMAIN -> input.skipBytes(input.readUnsignedByte())
            ATYP_IPV6 -> input.skipBytes(16)
        }
        input.skipBytes(2) // port
    }
}
