package com.linkbridge.app

/**
 * Démarre le relais sur un port libre choisi par le système et attend qu'il
 * soit réellement prêt. Évite les conflits de port fixe entre tests et la
 * course « le port répond mais ce n'est pas encore ce relais ».
 */
internal fun Socks5Gateway.startAndAwait(): Int {
    start()
    check(awaitStarted()) { "Le relais n'a pas démarré à temps" }
    return boundPort
}
