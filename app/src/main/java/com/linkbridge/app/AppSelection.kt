package com.linkbridge.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Une application installée que l'utilisateur peut envoyer dans le tunnel.
 *
 * L'icône est chargée en même temps que le nom, sur le fil d'arrière-plan : sans elle, la
 * liste n'était qu'une suite de noms, et retrouver « Firefox » demandait plus d'attention
 * que nécessaire. L'image est réduite à la taille d'affichage pour ne pas garder en mémoire
 * un logo de plusieurs centaines de pixels.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap? = null
)

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

    /** Côté affiché de l'icône, en pixels : deux fois les 48 dp de la liste. */
    private const val ICON_PX = 96

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
                    InstalledApp(
                        packageName = activity.packageName,
                        label = info.loadLabel(pm).toString(),
                        icon = loadIcon(pm, info)
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Icône d'une application, ou rien.
     *
     * Chaque échec est isolé : une seule icône illisible ne doit pas vider la liste entière,
     * et certaines applications du système refusent de fournir leur image.
     */
    private fun loadIcon(pm: PackageManager, info: android.content.pm.ResolveInfo): ImageBitmap? {
        return try {
            val drawable = info.loadIcon(pm) ?: return null
            drawable.toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
}
