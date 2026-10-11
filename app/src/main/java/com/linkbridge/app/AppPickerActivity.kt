package com.linkbridge.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Page de choix des applications qui utilisent la connexion reçue.
 *
 * C'est une activité à part, comme le guide : la page occupe tout l'écran, la flèche et le
 * geste de retour ramènent à l'écran principal, et le choix est écrit à chaque appui. Une
 * fenêtre de dialogue obligeait à tout faire dans un espace trop petit, et fermait sans
 * qu'on sache si le choix avait été pris en compte.
 */
class AppPickerActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<InstalledApp>>(emptyList())
    private var selected by mutableStateOf<Set<String>>(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = AppSelectionStore.load(this).packages

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        Thread {
            val loaded = AppSelectionStore.listLaunchableApps(this)
            runOnUiThread { apps = loaded }
        }.apply {
            name = "linkbridge-apps"
            start()
        }

        setContent {
            LinkBridgeTheme {
                AppPickerPage(
                    apps = apps,
                    selected = selected,
                    onToggle = { pkg ->
                        update(if (pkg in selected) selected - pkg else selected + pkg)
                    },
                    onSelectAll = { update(apps.map { it.packageName }.toSet()) },
                    onClearAll = { update(emptySet()) },
                    onDismiss = { goHome() }
                )
            }
        }
    }

    private fun update(packages: Set<String>) {
        selected = packages
        // Le choix part tout de suite dans les préférences : l'utilisateur peut revenir en
        // arrière sans perdre ce qu'il vient de cocher.
        AppSelectionStore.save(this, AppSelectionStore.load(this).restrict, packages)
    }

    /** Retour à l'écran principal, qui relit le choix en revenant au premier plan. */
    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
