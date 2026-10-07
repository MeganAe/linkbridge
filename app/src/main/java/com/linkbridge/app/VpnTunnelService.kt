package com.linkbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
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
    private var watch: LinkWatch? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var configFile: File? = null
    private var engineThread: Thread? = null
    private val stopping = AtomicBoolean(false)

    /** Signature de la session en cours, pour ne pas relancer le tunnel pour rien. */
    private var currentKey: String? = null

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

        val restrictApps = intent?.getBooleanExtra(EXTRA_RESTRICT_APPS, false) ?: false
        val allowedApps = intent?.getStringArrayListExtra(EXTRA_ALLOWED_APPS).orEmpty()
        val source = intent?.getStringExtra(EXTRA_SOURCE) ?: SOURCE_DIRECT

        val key = "$host|$code|$restrictApps|${allowedApps.joinToString(",")}"
        startForegroundCompat()
        if (tunFd != null && currentKey == key) {
            // Même session déjà en cours : la relancer couperait la connexion des applications.
            return START_STICKY
        }
        if (tunFd != null) stopTunnel()
        currentKey = key
        running = true
        startTunnel(host, code, restrictApps, allowedApps, source)
        return START_STICKY
    }

    private fun startTunnel(
        host: String,
        code: String,
        restrictApps: Boolean,
        allowedApps: List<String>,
        source: String
    ) {
        stopping.set(false)
        val builder = Builder()
            .setSession("LinkBridge")
            .setMtu(TUN_MTU)
            .addAddress("198.18.0.1", 15)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("198.18.0.2")
            .addAddress("fc00::1", 64)
            .addRoute("::", 0)

        if (restrictApps) {
            // Only the chosen applications use the tunnel; the others keep the
            // phone's normal connection. The native engine lives in this
            // application, which is not in the list, so its own sockets bypass
            // the VPN and cannot loop.
            var added = 0
            for (app in allowedApps) {
                if (app == packageName) continue
                try {
                    builder.addAllowedApplication(app)
                    added++
                } catch (_: PackageManager.NameNotFoundException) {
                    // Application uninstalled since it was chosen: skip it.
                }
            }
            if (added == 0) {
                // Never fall back to "every application": with no allowed
                // application Android would route the whole phone.
                notifyStopped()
                stopSelf()
                return
            }
        } else {
            // The native tun2socks engine is part of this same application. Its
            // own SOCKS5 sockets must bypass our VPN or they would loop forever.
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {
                // The tunnel can still run on devices that do not expose this
                // per-app exclusion; the protected-network path is attempted.
            }
        }

        tunFd = builder.establish()
        if (tunFd == null) {
            notifyStopped()
            stopSelf()
            return
        }

        if (source == SOURCE_DIRECT) {
            // L'application peut être fermée : c'est le service qui surveille le lien, pour
            // que le tunnel et sa notification s'arrêtent quand la connexion n'existe plus.
            watch?.stop()
            watch = LinkWatch(this) { onLinkLost() }.also { it.start() }
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

    /** Explique à l'utilisateur pourquoi le tunnel n'a pas pu démarrer. */
    private fun notifyStopped() {
        running = false
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(
                NOTIFICATION_ID_STOPPED,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_linkbridge)
                    .setContentTitle("LinkBridge n'a pas démarré")
                    .setContentText(
                        "Vérifie que le Wi‑Fi est actif et que les applications choisies sont " +
                            "toujours installées, puis réessaie."
                    )
                    .setAutoCancel(true)
                    .build()
            )
        } catch (_: Throwable) {
        }
    }

    /** Le lien Wi‑Fi Direct est tombé : on libère le tunnel et on prévient l'utilisateur. */
    private fun onLinkLost() {
        if (tunFd == null) return
        try {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID_ENDED,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_linkbridge)
                    .setContentTitle("LinkBridge · connexion terminée")
                    .setContentText("Le lien avec l'autre appareil s'est terminé.")
                    .setAutoCancel(true)
                    .build()
            )
        } catch (_: Throwable) {
        }
        stopTunnel()
        stopSelf()
    }

    private fun stopTunnel() {
        watch?.stop()
        watch = null
        if (!stopping.compareAndSet(false, true)) return
        running = false
        currentKey = null
        try {
            if (TProxyService.TProxyIsRunning()) TProxyService.TProxyStopService()
        } catch (_: Throwable) {
        }
        // Let the native engine finish before its TUN fd is closed, otherwise
        // it can crash the whole process while reading a closed descriptor.
        try {
            engineThread?.join(2_000)
        } catch (_: InterruptedException) {
        }
        engineThread = null
        try {
            tunFd?.close()
        } catch (_: Exception) {
        }
        tunFd = null
        configFile?.delete()
        configFile = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
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
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, VpnTunnelService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_linkbridge)
            .setContentTitle("LinkBridge · connexion reçue")
            .setContentText("Le tunnel continue en arrière-plan")
            .setContentIntent(open)
            .addAction(0, "Arrêter la connexion", stop)
            .setOngoing(true)
            .setShowWhen(false)
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
            NotificationChannel(
                CHANNEL_ID,
                "Connexion LinkBridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Affiche l'état de la connexion reçue."
                setShowBadge(false)
            }
        )
    }

    override fun onBind(intent: Intent?) = super.onBind(intent)

    companion object {
        const val ACTION_START = "com.linkbridge.app.vpn.START"
        const val ACTION_STOP = "com.linkbridge.app.vpn.STOP"
        const val EXTRA_GATEWAY_HOST = "gateway_host"
        const val EXTRA_CODE = "pairing_code"
        const val EXTRA_RESTRICT_APPS = "restrict_apps"
        const val EXTRA_ALLOWED_APPS = "allowed_apps"
        const val EXTRA_SOURCE = "source"
        private const val CHANNEL_ID = "linkbridge_vpn"
        private const val NOTIFICATION_ID = 11
        private const val NOTIFICATION_ID_STOPPED = 12
        private const val NOTIFICATION_ID_ENDED = 14
        private const val TUN_MTU = 1400

        /** Vrai pendant que le tunnel tourne : sert à restaurer l'écran au retour. */
        @Volatile
        var running: Boolean = false
            private set
    }
}
