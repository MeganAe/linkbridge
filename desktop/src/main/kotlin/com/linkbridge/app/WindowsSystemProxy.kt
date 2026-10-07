package com.linkbridge.app

import java.io.File
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Active / désactive le proxy système de Windows (celui de Paramètres →
 * Réseau et Internet → Proxy). Chrome et Edge le suivent, même s'ils sont
 * déjà ouverts.
 *
 * Sécurité : les réglages d'origine sont sauvegardés dans un fichier AVANT
 * toute modification. Si LinkBridge plante ou est fermé de force, [restoreIfLeftover]
 * les remet au prochain démarrage, pour ne jamais laisser Windows pointer vers
 * un proxy éteint (ce qui couperait Internet).
 *
 * Aucune méthode ne lève d'exception : le résultat est un message lisible.
 */
object WindowsSystemProxy {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
    private const val BYPASS = "localhost;127.*;10.*;192.168.*;<local>"

    val isSupported: Boolean
        get() = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private val backupFile: File
        get() {
            val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("user.home")
            return File(File(base, "LinkBridge"), "system-proxy-backup.properties")
        }

    /** Vrai si LinkBridge a modifié le proxy système et ne l'a pas encore restauré. */
    val isActive: Boolean
        get() = backupFile.exists()

    /** Active le proxy système vers [hostPort] (ex. « 127.0.0.1:1080 »). */
    fun enable(hostPort: String): Result<String> = runCatching {
        check(isSupported) { "Le proxy système n'est géré que sous Windows." }
        if (!backupFile.exists()) saveBackup()
        setValue("ProxyEnable", "REG_DWORD", "1")
        setValue("ProxyServer", "REG_SZ", hostPort)
        setValue("ProxyOverride", "REG_SZ", BYPASS)
        notifyChange()
        "Proxy système activé sur $hostPort."
    }

    /** Remet les réglages d'origine. Sans effet si LinkBridge n'avait rien changé. */
    fun disable(): Result<String> = runCatching {
        if (!isSupported || !backupFile.exists()) return@runCatching "Proxy système déjà dans son état d'origine."
        restoreFromBackup()
        "Proxy système remis comme avant."
    }

    /** À appeler au démarrage : répare un arrêt brutal précédent. */
    fun restoreIfLeftover(): Result<String> = disable()

    // ----- sauvegarde / restauration -----

    private fun saveBackup() {
        val props = Properties()
        for (name in listOf("ProxyEnable", "ProxyServer", "ProxyOverride")) {
            val current = readValue(name)
            if (current != null) props.setProperty(name, current.first + "|" + current.second)
        }
        backupFile.parentFile?.mkdirs()
        backupFile.outputStream().use { props.store(it, "LinkBridge - réglages proxy Windows d'origine") }
    }

    private fun restoreFromBackup() {
        val props = Properties()
        backupFile.inputStream().use { props.load(it) }
        for (name in listOf("ProxyEnable", "ProxyServer", "ProxyOverride")) {
            val saved = props.getProperty(name)
            if (saved == null) {
                // La valeur n'existait pas avant : on la supprime.
                if (name == "ProxyEnable") setValue(name, "REG_DWORD", "0") else deleteValue(name)
            } else {
                val type = saved.substringBefore('|')
                val value = saved.substringAfter('|')
                setValue(name, type, value)
            }
        }
        notifyChange()
        backupFile.delete()
    }

    // ----- registre (commande « reg ») -----

    private fun run(vararg command: String): Pair<Int, String> {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Commande trop longue : ${command.first()}")
        }
        return process.exitValue() to output
    }

    private fun setValue(name: String, type: String, value: String) {
        val (code, out) = run("reg", "add", KEY, "/v", name, "/t", type, "/d", value, "/f")
        check(code == 0) { "Écriture du registre refusée pour $name : ${out.trim()}" }
    }

    private fun deleteValue(name: String) {
        // Code d'erreur ignoré : la valeur peut ne pas exister.
        run("reg", "delete", KEY, "/v", name, "/f")
    }

    private fun readValue(name: String): Pair<String, String>? {
        val (code, out) = run("reg", "query", KEY, "/v", name)
        return if (code == 0) parseRegQuery(out, name) else null
    }

    /**
     * Extrait (type, valeur) d'une sortie de « reg query ». Pour REG_DWORD,
     * la valeur hexadécimale (0x1) est convertie en décimal.
     */
    internal fun parseRegQuery(output: String, name: String): Pair<String, String>? {
        val regex = Regex("""^\s*${Regex.escape(name)}\s+(REG_\w+)\s*(.*)$""", RegexOption.IGNORE_CASE)
        for (line in output.lineSequence()) {
            val match = regex.find(line.trimEnd()) ?: continue
            val type = match.groupValues[1].uppercase()
            var value = match.groupValues[2].trim()
            if (type == "REG_DWORD") {
                value = value.removePrefix("0x").removePrefix("0X").toLongOrNull(16)?.toString() ?: return null
            }
            return type to value
        }
        return null
    }

    /** Prévient Windows et les navigateurs que le proxy a changé (meilleur effort). */
    private fun notifyChange() {
        val script = """
            ${'$'}sig = '[DllImport("wininet.dll")] public static extern bool InternetSetOption(IntPtr h, int o, IntPtr b, int l);'
            ${'$'}t = Add-Type -MemberDefinition ${'$'}sig -Name LinkBridgeWininet -Namespace LB -PassThru
            ${'$'}t::InternetSetOption([IntPtr]::Zero, 39, [IntPtr]::Zero, 0) | Out-Null
            ${'$'}t::InternetSetOption([IntPtr]::Zero, 37, [IntPtr]::Zero, 0) | Out-Null
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        try {
            run("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
        } catch (_: Exception) {
            // Les navigateurs relisent aussi le registre ; ce n'est qu'un coup de pouce.
        }
    }
}
