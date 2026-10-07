package com.linkbridge.app

import java.net.Inet4Address
import java.net.NetworkInterface

/** Une interface réseau locale avec son adresse IPv4. */
data class NetworkIface(val name: String, val address: String) {
    override fun toString(): String = "$name — $address"
}

/**
 * Liste les interfaces IPv4 utilisables (hors loopback). Ne lève jamais
 * d'exception : l'interface affiche simplement une liste vide.
 */
fun localNetworkInterfaces(): List<NetworkIface> {
    val result = mutableListOf<NetworkIface>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (intf in interfaces) {
            try {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        result += NetworkIface(intf.displayName ?: intf.name, host)
                    }
                }
            } catch (_: Exception) {
                // Une interface exotique ne doit pas empêcher de lister les autres.
            }
        }
    } catch (_: Exception) {
    }
    return result.sortedWith(compareBy({ it.name }, { it.address }))
}
