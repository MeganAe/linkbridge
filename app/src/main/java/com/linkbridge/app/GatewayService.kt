package com.linkbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat

class GatewayService : Service() {
    private var gateway: Socks5Gateway? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopGateway()
            stopSelf()
            return START_NOT_STICKY
        }

        val code = intent?.getStringExtra(EXTRA_CODE).orEmpty()
        val advertisedHost = intent?.getStringExtra(EXTRA_ADVERTISED_HOST)
            ?: DEFAULT_GROUP_OWNER_ADDRESS
        if (code.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notice = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notice,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notice)
        }
        // A connection callback can provide a more precise P2P address than
        // the usual 192.168.49.1 default. Rebind to that address so the
        // gateway is not exposed on the phone's normal network interfaces.
        gateway?.stop()
        gateway = Socks5Gateway(
            port = Socks5Gateway.DEFAULT_PORT,
            pairingCode = code,
            advertisedHost = advertisedHost,
            bindHost = advertisedHost
        ).also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        stopGateway()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopGateway() {
        gateway?.stop()
        gateway = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun notification(): Notification {
        createChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_linkbridge)
            .setContentTitle("LinkBridge")
            .setContentText("Partage Internet actif")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Partage LinkBridge", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val ACTION_START = "com.linkbridge.app.gateway.START"
        const val ACTION_STOP = "com.linkbridge.app.gateway.STOP"
        const val EXTRA_CODE = "pairing_code"
        const val EXTRA_ADVERTISED_HOST = "advertised_host"
        const val DEFAULT_GROUP_OWNER_ADDRESS = "192.168.49.1"
        private const val CHANNEL_ID = "linkbridge_gateway"
        private const val NOTIFICATION_ID = 10
    }
}
