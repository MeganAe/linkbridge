package com.linkbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Le relais qui tourne sur le téléphone qui partage.
 *
 * C'est un service de premier plan : il continue de servir la connexion quand l'application
 * est fermée ou quand la tâche est balayée des applications récentes. Le système peut le
 * recréer après un manque de mémoire ; il relit alors ses paramètres enregistrés, parce que
 * dans ce cas le système le rappelle avec un intent vide.
 */
class GatewayService : Service() {
    private var gateway: Socks5Gateway? = null
    private var watch: LinkWatch? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopGateway()
            stopSelf()
            return START_NOT_STICKY
        }

        // Intent vide : le système relance le service après l'avoir arrêté. On reprend le
        // dernier code enregistré au lieu d'abandonner silencieusement.
        var code = intent?.getStringExtra(EXTRA_CODE).orEmpty()
        var advertisedHost = intent?.getStringExtra(EXTRA_ADVERTISED_HOST)
        var source = intent?.getStringExtra(EXTRA_SOURCE)
        if (code.isBlank()) {
            val saved = prefs().getString(KEY_CODE, "").orEmpty()
            val savedHost = prefs().getString(KEY_HOST, null)
            if (saved.isBlank()) {
                stopSelf()
                return START_NOT_STICKY
            }
            code = saved
            advertisedHost = savedHost
            source = prefs().getString(KEY_SOURCE, SOURCE_DIRECT)
        }
        val host = advertisedHost ?: DEFAULT_GROUP_OWNER_ADDRESS

        val sessionSource = source ?: SOURCE_DIRECT
        prefs().edit()
            .putString(KEY_CODE, code)
            .putString(KEY_HOST, host)
            .putString(KEY_SOURCE, sessionSource)
            .apply()
        activeCode = code
        running = true

        startForegroundCompat(notification(code))
        // Une connexion plus précise que 192.168.49.1 peut arriver ensuite : le relais est
        // alors recréé sur cette adresse, pour ne pas écouter sur les interfaces habituelles.
        gateway?.stop()
        gateway = Socks5Gateway(
            port = Socks5Gateway.DEFAULT_PORT,
            pairingCode = code,
            advertisedHost = host,
            bindHost = host
        ).also { it.start() }
        if (sessionSource == SOURCE_DIRECT) {
            // Le relais suit le lien Wi‑Fi Direct même application fermée : sans lien il n'a
            // plus de client à servir, donc il n'annonce plus un partage qui n'existe pas.
            watch?.stop()
            watch = LinkWatch(this) { onLinkLost() }.also { it.start() }
        }
        return START_STICKY
    }

    /**
     * Le lien Wi‑Fi Direct est tombé : le relais s'arrête et prévient l'utilisateur, au lieu
     * de laisser une notification au vert pour une connexion morte.
     */
    private fun onLinkLost() {
        if (gateway == null) return
        try {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID_ENDED,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_linkbridge)
                    .setContentTitle("LinkBridge · partage terminé")
                    .setContentText("La connexion avec l'autre appareil s'est terminée.")
                    .setAutoCancel(true)
                    .build()
            )
        } catch (_: Throwable) {
        }
        stopGateway()
        stopSelf()
    }

    override fun onDestroy() {
        watch?.stop()
        watch = null
        stopGateway()
        running = false
        activeCode = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Met à jour la notification quand l'état de la session change. */
    fun refresh(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(message = message))
    }

    private fun stopGateway() {
        gateway?.stop()
        gateway = null
        prefs().edit().remove(KEY_CODE).remove(KEY_HOST).remove(KEY_SOURCE).apply()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun notification(message: String? = null, code: String? = null): Notification {
        createChannel()
        val shownCode = code ?: activeCode
        val text = message ?: if (shownCode.isNullOrBlank()) {
            "Partage Internet actif"
        } else {
            "Code ${shownCode.chunked(3).joinToString(" ")} · le partage continue en arrière-plan"
        }
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
            Intent(this, GatewayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_linkbridge)
            .setContentTitle("LinkBridge · partage actif")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, "Arrêter le partage", stop)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startForegroundCompat(notice: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notice,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notice)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Partage LinkBridge",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Affiche le code de liaison pendant que le partage est actif."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.linkbridge.app.gateway.START"
        const val ACTION_STOP = "com.linkbridge.app.gateway.STOP"
        const val EXTRA_CODE = "pairing_code"
        const val EXTRA_ADVERTISED_HOST = "advertised_host"
        const val EXTRA_SOURCE = "source"
        const val DEFAULT_GROUP_OWNER_ADDRESS = "192.168.49.1"
        private const val PREFS = "linkbridge_gateway_state"
        private const val KEY_CODE = "code"
        private const val KEY_HOST = "host"
        private const val KEY_SOURCE = "source"
        private const val CHANNEL_ID = "linkbridge_gateway"
        private const val NOTIFICATION_ID = 10
        private const val NOTIFICATION_ID_ENDED = 13

        /** Vrai pendant qu'un relais sert des clients : sert à restaurer l'écran au retour. */
        @Volatile
        var running: Boolean = false
            private set

        /** Code de la session en cours, pour que l'écran affiche le même que la notification. */
        @Volatile
        var activeCode: String? = null
            private set
    }
}
