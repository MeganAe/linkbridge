package com.linkbridge.app

import java.io.File

/** Famille de logiciel : détermine comment on lui impose le proxy. */
enum class AppKind { CHROMIUM, FIREFOX }

/** Un logiciel détecté sur ce PC et lançable à travers LinkBridge. */
data class LaunchableApp(
    val id: String,
    val name: String,
    val executable: File,
    val kind: AppKind
) {
    /** Explication affichée à l'utilisateur. */
    val note: String
        get() = when (kind) {
            AppKind.CHROMIUM -> "Ouvre une fenêtre à part (profil LinkBridge) qui passe par le téléphone."
            AppKind.FIREFOX -> "Ouvre Firefox avec un profil LinkBridge déjà réglé sur le proxy."
        }
}

/**
 * Détecte quelques navigateurs courants et les démarre avec le proxy local de
 * LinkBridge déjà configuré, sans que l'utilisateur tape de commande.
 *
 * Chaque logiciel est lancé avec son propre profil (dossier LinkBridge) : un
 * navigateur déjà ouvert avec le profil habituel absorberait le lancement et
 * ignorerait l'option de proxy. Le profil est permanent, donc les connexions
 * aux sites y sont conservées d'un lancement à l'autre.
 */
object AppLauncher {
    private data class Candidate(val id: String, val name: String, val kind: AppKind, val paths: List<String>)

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    private fun candidates(): List<Candidate> {
        val pf = env("ProgramFiles")
        val pf86 = env("ProgramFiles(x86)")
        val local = env("LOCALAPPDATA")
        fun under(root: String?, vararg tail: String): String? =
            root?.let { File(it, tail.joinToString(File.separator)).path }
        return listOf(
            Candidate(
                "chrome", "Google Chrome", AppKind.CHROMIUM,
                listOfNotNull(
                    under(pf, "Google", "Chrome", "Application", "chrome.exe"),
                    under(pf86, "Google", "Chrome", "Application", "chrome.exe"),
                    under(local, "Google", "Chrome", "Application", "chrome.exe")
                )
            ),
            Candidate(
                "edge", "Microsoft Edge", AppKind.CHROMIUM,
                listOfNotNull(
                    under(pf86, "Microsoft", "Edge", "Application", "msedge.exe"),
                    under(pf, "Microsoft", "Edge", "Application", "msedge.exe")
                )
            ),
            Candidate(
                "brave", "Brave", AppKind.CHROMIUM,
                listOfNotNull(
                    under(pf, "BraveSoftware", "Brave-Browser", "Application", "brave.exe"),
                    under(pf86, "BraveSoftware", "Brave-Browser", "Application", "brave.exe"),
                    under(local, "BraveSoftware", "Brave-Browser", "Application", "brave.exe")
                )
            ),
            Candidate(
                "firefox", "Mozilla Firefox", AppKind.FIREFOX,
                listOfNotNull(
                    under(pf, "Mozilla Firefox", "firefox.exe"),
                    under(pf86, "Mozilla Firefox", "firefox.exe")
                )
            )
        )
    }

    /** Navigateurs trouvés sur ce PC (liste vide hors Windows). */
    fun detect(): List<LaunchableApp> {
        if (!WindowsSystemProxy.isSupported) return emptyList()
        return candidates().mapNotNull { candidate ->
            val exe = candidate.paths.map { File(it) }.firstOrNull { it.isFile } ?: return@mapNotNull null
            LaunchableApp(candidate.id, candidate.name, exe, candidate.kind)
        }
    }

    private fun profileDir(app: LaunchableApp): File {
        val base = env("LOCALAPPDATA") ?: System.getProperty("user.home")
        return File(File(File(base, "LinkBridge"), "profiles"), app.id)
    }

    /** Construit la ligne de commande (séparé du lancement pour rester testable). */
    internal fun commandFor(app: LaunchableApp, proxyPort: Int, profile: File): List<String> =
        when (app.kind) {
            AppKind.CHROMIUM -> listOf(
                app.executable.path,
                "--proxy-server=socks5://127.0.0.1:$proxyPort",
                "--user-data-dir=${profile.path}",
                "--no-first-run"
            )
            AppKind.FIREFOX -> listOf(
                app.executable.path,
                "-no-remote",
                "-profile",
                profile.path
            )
        }

    internal fun firefoxPrefs(proxyPort: Int): String = """
        user_pref("network.proxy.type", 1);
        user_pref("network.proxy.socks", "127.0.0.1");
        user_pref("network.proxy.socks_port", $proxyPort);
        user_pref("network.proxy.socks_version", 5);
        user_pref("network.proxy.socks_remote_dns", true);
        user_pref("network.proxy.no_proxies_on", "localhost, 127.0.0.1");
    """.trimIndent() + "\n"

    /** Démarre [app] à travers le proxy local. Ne lève jamais d'exception. */
    fun launch(app: LaunchableApp, proxyPort: Int): Result<String> = runCatching {
        val profile = profileDir(app)
        profile.mkdirs()
        if (app.kind == AppKind.FIREFOX) {
            File(profile, "user.js").writeText(firefoxPrefs(proxyPort))
        }
        ProcessBuilder(commandFor(app, proxyPort, profile))
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        "${app.name} lancé à travers LinkBridge."
    }
}
