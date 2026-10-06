package com.linkbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import hev.htproxy.TProxyService
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-device VPN side running on phone B.
 *
 * Android gives the service a TUN interface. HevSocks5Tunnel then converts
 * packets from that interface into authenticated SOCKS5 connections to the
 * gateway running on phone A.
 */
class VpnTunnelService : VpnService() {
    private var tunFd: ParcelFileDescriptor? = null
    private var configFile: File? = null
    private var engineThread: Thread? = null
    private val stopping = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }

        val host = intent?.getStringExtra(EXTRA_GATEWAY_HOST).orEmpty()
        val code = intent?.getStringExtra(EXTRA_CODE).orEmpty()
        if (host.isBlank() || code.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        if (tunFd == null) startTunnel(host, code)
        return START_STICKY
    }

    private fun startTunnel(host: String, code: String) {
        stopping.set(false)
        val builder = Builder()
            .setSession("LinkBridge")
            .setMtu(TUN_MTU)
            .addAddress("198.18.0.1", 15)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("198.18.0.2")
            .addAddress("fc00::1", 64)
            .addRoute("::", 0)

        // The native tun2socks engine is part of this same application. Its
        // own SOCKS5 sockets must bypass our VPN or they would loop forever.
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
            // The tunnel can still run on devices that do not expose this
            // per-app exclusion; the protected-network path is attempted.
        }

        tunFd = builder.establish()
        if (tunFd == null) {
            stopSelf()
            return
        }

        val config = File(cacheDir, "linkbridge-tunnel.yml")
        writeConfig(config, host, code)
        configFile = config

        val fd = tunFd?.fd ?: run {
            stopTunnel()
            return
        }
        engineThread = Thread {
            val started = try {
                TProxyService.TProxyStartService(config.absolutePath, fd)
            } catch (_: Throwable) {
                false
            }
            if (!started && !stopping.get()) {
                stopTunnel()
                stopSelf()
            }
        }.also {
            it.name = "linkbridge-tun2socks"
            it.start()
        }
    }

    private fun writeConfig(file: File, host: String, code: String) {
        val yaml = """
            tunnel:
              name: tun0
              mtu: $TUN_MTU
              ipv4: 198.18.0.1
              ipv6: 'fc00::1'
              icmp: 'reply'
            socks5:
              address: '$host'
              port: ${Socks5Gateway.DEFAULT_PORT}
              udp: 'udp'
              username: '${Socks5Gateway.USERNAME}'
              password: '$code'
            mapdns:
              address: 198.18.0.2
              port: 53
              network: 198.19.0.0
              netmask: 255.255.0.0
              cache-size: 10000
            misc:
              log-level: warn
        """.trimIndent()
        FileOutputStream(file, false).use { it.write(yaml.toByteArray(Charsets.UTF_8)) }
    }

    private fun stopTunnel() {
        if (!stopping.compareAndSet(false, true)) return
        try {
            if (TProxyService.TProxyIsRunning()) TProxyService.TProxyStopService()
        } catch (_: Throwable) {
        }
        try {
            tunFd?.close()
        } catch (_: Exception) {
        }
        tunFd = null
        engineThread?.interrupt()
        engineThread = null
        configFile?.delete()
        configFile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_linkbridge)
            .setContentTitle("LinkBridge")
            .setContentText("Connexion Internet reçue")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Connexion LinkBridge", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?) = super.onBind(intent)

    companion object {
        const val ACTION_START = "com.linkbridge.app.vpn.START"
        const val ACTION_STOP = "com.linkbridge.app.vpn.STOP"
        const val EXTRA_GATEWAY_HOST = "gateway_host"
        const val EXTRA_CODE = "pairing_code"
        private const val CHANNEL_ID = "linkbridge_vpn"
        private const val NOTIFICATION_ID = 11
        private const val TUN_MTU = 1400
    }
}
