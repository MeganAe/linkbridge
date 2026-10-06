package com.linkbridge.app

import android.content.Context
import java.security.SecureRandom

object PairingCode {
    private const val PREFS = "linkbridge_preferences"
    private const val KEY_CODE = "gateway_pairing_code"
    private val random = SecureRandom()

    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CODE, null)
        if (!existing.isNullOrBlank()) return existing
        val value = (100_000 + random.nextInt(900_000)).toString()
        prefs.edit().putString(KEY_CODE, value).apply()
        return value
    }
}
