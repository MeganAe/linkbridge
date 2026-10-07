package com.linkbridge.app

import android.content.Context

/**
 * Mémorise si le guide de démarrage a déjà été montré sur ce téléphone.
 * Rien d'autre n'est enregistré, et rien ne quitte l'appareil.
 */
object GuideStore {
    private const val PREFS = "linkbridge_guide"
    private const val KEY_SEEN = "guide_seen"

    fun hasSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SEEN, true)
            .apply()
    }
}
