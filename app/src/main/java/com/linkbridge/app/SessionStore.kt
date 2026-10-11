package com.linkbridge.app

import android.content.Context

/** Les deux rôles possibles d'un téléphone : celui qui partage, et celui qui reçoit. */
internal enum class LinkRole { SHARE, RECEIVE }

/**
 * Retient la session en cours : rôle, code de liaison, et adresse du relais.
 *
 * Sans cela, fermer puis rouvrir l'application pendant que le partage tourne affichait de
 * nouveau le code de départ et un écran vide, alors que le service continuait de tourner en
 * arrière-plan : l'utilisateur croyait que tout s'était arrêté.
 *
 * L'adresse du relais sert au téléphone qui reçoit : c'est elle qui permet de tester la
 * connexion après une fermeture de l'application, sans refaire toute la mise en place.
 *
 * Ces valeurs ne sont écrites que pendant une session active, et effacées dès qu'elle
 * s'arrête.
 */
internal object SessionStore {
    private const val PREFS = "linkbridge_session"
    private const val KEY_ACTIVE = "active"
    private const val KEY_ROLE = "role"
    private const val KEY_CODE = "code"
    private const val KEY_HOST = "host"

    internal data class Session(
        val active: Boolean,
        val role: LinkRole?,
        val code: String,
        val host: String
    )

    internal fun load(context: Context): Session {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val role = when (prefs.getString(KEY_ROLE, null)) {
            "share" -> LinkRole.SHARE
            "receive" -> LinkRole.RECEIVE
            else -> null
        }
        return Session(
            active = active,
            role = role,
            code = prefs.getString(KEY_CODE, "").orEmpty(),
            host = prefs.getString(KEY_HOST, "").orEmpty()
        )
    }

    internal fun save(context: Context, role: LinkRole, code: String, host: String = "") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_ROLE, if (role == LinkRole.SHARE) "share" else "receive")
            .putString(KEY_CODE, code)
            .putString(KEY_HOST, host)
            .apply()
    }

    internal fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
