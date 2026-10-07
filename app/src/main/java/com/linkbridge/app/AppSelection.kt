package com.linkbridge.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Une application installée que l'utilisateur peut envoyer dans le tunnel. */
data class InstalledApp(val packageName: String, val label: String)

/** Choix mémorisé sur le téléphone : toutes les applis, ou seulement certaines. */
data class AppSelection(val restrict: Boolean, val packages: Set<String>)

/**
 * Mémorise (en local uniquement) quelles applications du téléphone B passent
 * par la connexion reçue, et liste celles qui peuvent être choisies.
 */
object AppSelectionStore {
    private const val PREFS = "linkbridge_prefs"
    private const val KEY_RESTRICT = "restrict_apps"
    private const val KEY_PACKAGES = "allowed_apps"

    fun load(context: Context): AppSelection {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppSelection(
            restrict = prefs.getBoolean(KEY_RESTRICT, false),
            packages = prefs.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()
        )
    }

    fun save(context: Context, restrict: Boolean, packages: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESTRICT, restrict)
            .putStringSet(KEY_PACKAGES, HashSet(packages))
            .apply()
    }

    /**
     * Applications qui ont une icône de lancement (celles que l'utilisateur
     * reconnaît), triées par nom. LinkBridge lui-même est exclu : son moteur
     * doit rester hors du tunnel.
     */
    fun listLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
                .asSequence()
                .mapNotNull { info ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    if (activity.packageName == context.packageName) return@mapNotNull null
                    InstalledApp(activity.packageName, info.loadLabel(pm).toString())
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
