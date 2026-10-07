package com.linkbridge.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread

/**
 * État de l'application bureau et gestion des relais.
 *
 * Règles : toute erreur est capturée et affichée, aucun thread ne doit faire
 * crasher l'application, et les mots de passe / codes ne sont jamais écrits
 * dans les journaux.
 */
class DesktopAppState {
    // ----- Onglet « Partager » (ce PC a Internet) -----
    var interfaces by mutableStateOf(localNetworkInterfaces())
        private set
    var selectedIface by mutableStateOf<NetworkIface?>(null)
    var pairingCode by mutableStateOf(PairingCode.generate())
        private set
    var sharing by mutableStateOf(false)
        private set
    var shareError by mutableStateOf<String?>(null)
    val shareLog = mutableStateListOf<String>()
    var clientCount by mutableStateOf(0)
    private var gateway: Socks5Gateway? = null
    private val shareLogLock = Any()

    init {
        selectedIface = interfaces.firstOrNull()
    }

    val relayEndpoint: String?
        get() = selectedIface?.let { "${it.address}:${Socks5Gateway.DEFAULT_PORT}" }

    fun refreshInterfaces() {
        interfaces = localNetworkInterfaces()
        if (interfaces.none { it == selectedIface }) selectedIface = interfaces.firstOrNull()
    }

    fun regenerateCode() {
        if (sharing) return
        pairingCode = PairingCode.generate()
        addShareLog("Nouveau code de liaison généré")
    }

    fun startSharing() {
        if (sharing) return
        val iface = selectedIface
        if (iface == null) {
            shareError = "Aucune interface réseau disponible : vérifie ta connexion."
            return
        }
        shareError = null
        try {
            val gw = Socks5Gateway(
                port = Socks5Gateway.DEFAULT_PORT,
                pairingCode = pairingCode,
                advertisedHost = iface.address,
                bindHost = iface.address,
                onEvent = { event -> addShareLog(event) }
            )
            gw.start()
            gateway = gw
            sharing = true
            addShareLog("Relais démarré sur ${iface.address}:${Socks5Gateway.DEFAULT_PORT}")
        } catch (t: Throwable) {
            shareError = "Impossible de démarrer le relais : ${t.message ?: t}"
            addShareLog("Erreur au démarrage : ${t.message ?: t}")
        }
    }

    fun stopSharing() {
        try {
            gateway?.stop()
        } catch (t: Throwable) {
            addShareLog("Erreur à l'arrêt : ${t.message ?: t}")
        }
        gateway = null
        sharing = false
        clientCount = 0
        addShareLog("Relais arrêté")
    }

    fun refreshClients() {
        clientCount = gateway?.activeClients ?: 0
    }

    private fun addShareLog(line: String) {
        synchronized(shareLogLock) {
            shareLog.add("${now()}  $line")
            while (shareLog.size > 200) shareLog.removeAt(0)
        }
    }

    // ----- Onglet « Se connecter / Tester » -----
    var relayHost by mutableStateOf("")
    var relayPort by mutableStateOf(Socks5Gateway.DEFAULT_PORT.toString())
    var relayCode by mutableStateOf("")
    var testStatus by mutableStateOf<String?>(null)
    var testOk by mutableStateOf(false)
    var testing by mutableStateOf(false)
    var proxyRunning by mutableStateOf(false)
        private set
    var proxyError by mutableStateOf<String?>(null)
    val proxyLog = mutableStateListOf<String>()
    private var proxy: Socks5ChainProxy? = null
    private val proxyLogLock = Any()

    val proxyEndpoint: String
        get() = "127.0.0.1:${Socks5ChainProxy.DEFAULT_PORT}"

    /** Message d'aide quand la saisie est invalide, null sinon. */
    fun validationMessage(): String? {
        val host = relayHost.trim()
        val port = relayPort.trim().toIntOrNull()
        val code = relayCode.trim()
        return when {
            host.isEmpty() -> "Entre l'adresse IP du relais, celle du téléphone A ou du PC."
            port == null || port !in 1..65535 -> "Le port doit être compris entre 1 et 65535."
            code.length != 6 -> "Le code doit contenir 6 chiffres."
            else -> null
        }
    }

    fun runTest() {
        if (testing) return
        val invalid = validationMessage()
        if (invalid != null) {
            testStatus = invalid
            testOk = false
            return
        }
        val host = relayHost.trim()
        val port = relayPort.trim().toInt()
        val code = relayCode.trim()
        testing = true
        testStatus = "Test en cours…"
        testOk = false
        thread(name = "linkbridge-test", isDaemon = true) {
            val result = runCatching {
                Socks5Client.probeHttp(host, port, Socks5Gateway.USERNAME, code)
            }
            testing = false
            result.onSuccess { line ->
                testOk = true
                testStatus = "Connexion réussie à travers le relais : $line"
            }.onFailure { t ->
                testOk = false
                testStatus = t.message ?: t.toString()
            }
        }
    }

    fun startProxy() {
        if (proxyRunning) return
        val invalid = validationMessage()
        if (invalid != null) {
            proxyError = invalid
            return
        }
        proxyError = null
        try {
            val px = Socks5ChainProxy(
                listenHost = "127.0.0.1",
                listenPort = Socks5ChainProxy.DEFAULT_PORT,
                upstreamHost = relayHost.trim(),
                upstreamPort = relayPort.trim().toInt(),
                upstreamUser = Socks5Gateway.USERNAME,
                upstreamPass = relayCode.trim(),
                onEvent = { event -> addProxyLog(event) }
            )
            px.start()
            proxy = px
            proxyRunning = true
            addProxyLog("Proxy local démarré sur $proxyEndpoint")
        } catch (t: Throwable) {
            proxyError = "Impossible de démarrer le proxy local : ${t.message ?: t}"
            addProxyLog("Erreur : ${t.message ?: t}")
        }
    }

    fun stopProxy() {
        // Si Windows pointe vers ce proxy, on le remet d'abord comme avant :
        // sinon plus rien ne se chargerait une fois le proxy arrêté.
        if (systemProxyOn) disableSystemProxy()
        try {
            proxy?.stop()
        } catch (t: Throwable) {
            addProxyLog("Erreur à l'arrêt : ${t.message ?: t}")
        }
        proxy = null
        proxyRunning = false
        addProxyLog("Proxy local arrêté")
    }

    private fun addProxyLog(line: String) {
        synchronized(proxyLogLock) {
            proxyLog.add("${now()}  $line")
            while (proxyLog.size > 200) proxyLog.removeAt(0)
        }
    }

    /** Arrêt propre de tout, appelé à la fermeture de la fenêtre. */
    fun shutdown() {
        try {
            if (systemProxyOn) disableSystemProxy()
        } catch (_: Throwable) {
        }
        try {
            stopSharing()
        } catch (_: Throwable) {
        }
        try {
            stopProxy()
        } catch (_: Throwable) {
        }
    }

    // ----- Onglet « Applications » -----
    var systemProxyOn by mutableStateOf(false)
        private set
    var systemProxyMessage by mutableStateOf<String?>(null)
    var appsMessage by mutableStateOf<String?>(null)
    val launchableApps: List<LaunchableApp> = AppLauncher.detect()
    val systemProxySupported: Boolean = WindowsSystemProxy.isSupported
    private var shutdownHookAdded = false

    /** Envoie tout le PC (applis qui suivent le proxy Windows) vers le relais. */
    fun enableSystemProxy() {
        if (systemProxyOn) return
        if (!proxyRunning) {
            systemProxyMessage = "Démarre d'abord le proxy local depuis la page Se connecter."
            return
        }
        WindowsSystemProxy.enable(proxyEndpoint)
            .onSuccess { message ->
                systemProxyOn = true
                systemProxyMessage = message
                addProxyLog(message)
                if (!shutdownHookAdded) {
                    shutdownHookAdded = true
                    // Filet de sécurité : fermeture « brutale » (session Windows, etc.).
                    Runtime.getRuntime().addShutdownHook(Thread { WindowsSystemProxy.disable() })
                }
            }
            .onFailure { t ->
                systemProxyMessage = "Impossible d'activer le proxy système : ${t.message ?: t}"
                // Au cas où un changement partiel aurait eu lieu.
                WindowsSystemProxy.disable()
            }
    }

    fun disableSystemProxy() {
        WindowsSystemProxy.disable()
            .onSuccess { message ->
                systemProxyOn = false
                systemProxyMessage = message
                addProxyLog(message)
            }
            .onFailure { t ->
                systemProxyMessage = "Impossible de remettre le proxy système : ${t.message ?: t}"
            }
    }

    /** Lance un navigateur déjà réglé sur le proxy local de LinkBridge. */
    fun launchApp(app: LaunchableApp) {
        if (!proxyRunning) {
            appsMessage = "Démarre d'abord le proxy local depuis la page Se connecter."
            return
        }
        AppLauncher.launch(app, Socks5ChainProxy.DEFAULT_PORT)
            .onSuccess { appsMessage = it; addProxyLog(it) }
            .onFailure { appsMessage = "Impossible de lancer ${app.name} : ${it.message ?: it}" }
    }

    init {
        // Répare un arrêt brutal précédent : si Windows pointait encore vers un
        // proxy LinkBridge éteint, on remet les réglages d'origine.
        if (WindowsSystemProxy.isActive) {
            WindowsSystemProxy.restoreIfLeftover().onSuccess {
                addProxyLog("Réglages proxy Windows restaurés après un arrêt inattendu.")
            }
        }
    }

    private fun now(): String = try {
        java.time.LocalTime.now().withNano(0).toString()
    } catch (_: Throwable) {
        ""
    }
}
