package com.linkbridge.app

import java.security.SecureRandom

/**
 * Six-digit pairing code shown on phone A and typed on phone B.
 *
 * A fresh code is generated for every sharing session and is never persisted,
 * so a code cannot be replayed on a later session.
 */
object PairingCode {
    private val random = SecureRandom()

    fun generate(): String = (100_000 + random.nextInt(900_000)).toString()
}
