package com.linkbridge.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Vraies notifications Android pour les événements importants (connexion établie, échec,
 * lien perdu, Wi‑Fi ou localisation à activer). Elles s'affichent en bandeau au-dessus de
 * l'écran, au lieu d'un simple texte dans l'application qu'on ne voit pas.
 *
 * Deux canaux, parce que tout ne mérite pas la même attention : les bonnes nouvelles
 * s'affichent en silence, les problèmes sonnent et vibrent. Sans cette séparation, chaque
 * connexion réussie faisait exactement le même bruit qu'un échec, et l'utilisateur finissait
 * par ne plus rien écouter.
 */
object Notifier {
    private const val CHANNEL_INFO = "linkbridge_infos"
    private const val CHANNEL_ALERT = "linkbridge_alerts"

    const val ID_LINK = 2001
    const val ID_ERROR = 2002
    const val ID_SETUP = 2003

    /**
     * Affiche une notification.
     *
     * @param important vrai pour un problème qui demande une action : la notification sonne.
     *   Faux pour une information de bonne marche : elle s'affiche sans bruit.
     */
    @SuppressLint("MissingPermission")
    fun post(
        context: Context,
        id: Int,
        title: String,
        text: String,
        important: Boolean = false
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannels(context)
            val open = PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(
                context,
                if (important) CHANNEL_ALERT else CHANNEL_INFO
            )
                .setSmallIcon(R.drawable.linkbridge_icone)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(
                    if (important) NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_LOW
                )
                .setCategory(
                    if (important) NotificationCompat.CATEGORY_MESSAGE
                    else NotificationCompat.CATEGORY_STATUS
                )
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(open)
            if (important) {
                // Le canal porte déjà le son et la vibration : on ne les ajoute pas ici,
                // sinon l'utilisateur ne peut plus les couper depuis les réglages Android.
            } else {
                builder.setSilent(true)
            }
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: Throwable) {
            // Une notification manquée ne doit jamais faire tomber l'application.
        }
    }

    fun cancel(context: Context, id: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(id)
        } catch (_: Throwable) {
        }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INFO,
                    "LinkBridge · informations",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Connexions établies et fin de session, sans bruit."
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERT,
                    "LinkBridge · problèmes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Connexion impossible, demande non aboutie, réglages à activer."
                    setShowBadge(true)
                }
            )
        } catch (_: Throwable) {
        }
    }
}
