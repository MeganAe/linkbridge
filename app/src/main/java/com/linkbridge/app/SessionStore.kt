package com.linkbridge.app

import android.content.Context

/** Les deux rôles possibles d'un téléphone : celui qui partage, et celui qui reçoit. */
internal enum class LinkRole { SHARE, RECEIVE }

/**
 * Retient la session en cours : rôle et code de liaison.
 *
 * Sans cela, fermer puis rouvrir l'application pendant que le partage tourne affichait de
 * nouveau le code de départ et un écran vide, alors que le service continuait de tourner en
 * arrière-plan : l'utilisateur croyait que tout s'était arrêté.
 *
 * Le code n'est écrit que pendant une session active et effacé dès qu'elle s'arrête.
 */
internal object SessionStore {
    private const val PREFS = "linkbridge_session"
    private const val KEY_ACTIVE = "active"
    private const val KEY_ROLE = "role"
    private const val KEY_CODE = "code"

    internal data class Session(val active: Boolean, val role: LinkRole?, val code: String)

    internal fun load(context: Context): Session {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val role = when (prefs.getString(KEY_ROLE, null)) {
            "share" -> LinkRole.SHARE
            "receive" -> LinkRole.RECEIVE
            else -> null
        }
        return Session(active, role, prefs.getString(KEY_CODE, "").orEmpty())
    }

    internal fun save(context: Context, role: LinkRole, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_ROLE, if (role == LinkRole.SHARE) "share" else "receive")
            .putString(KEY_CODE, code)
            .apply()
    }

    internal fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
