package com.francotte.homecontroller.feature.btlowenergy

import com.francotte.homecontroller.core.model.BleDevice
import kotlin.math.roundToInt

/**
 * Stabilise l'affichage d'un flux de scans BLE.
 *
 * Le RSSI brut est très bruité : trier dessus à chaque paquet fait « danser »
 * la liste. Cette classe lisse le RSSI de chaque appareil par une moyenne
 * glissante exponentielle (EMA) et rend la liste triée par RSSI lissé
 * décroissant, l'adresse servant de départage stable en cas d'égalité.
 *
 * Instance à usage unique par session de scan (elle accumule un état interne).
 *
 * @param alpha poids du dernier échantillon dans l'EMA (0..1). Plus il est bas,
 *   plus le lissage est fort (réactivité moindre, stabilité accrue).
 */
class BleRssiStabilizer(private val alpha: Double = 0.3) {

    private val smoothedRssi = LinkedHashMap<String, Double>()
    private val latestDevice = LinkedHashMap<String, BleDevice>()

    /** Intègre un instantané de scan et renvoie la liste stabilisée à afficher. */
    fun update(snapshot: List<BleDevice>): List<BleDevice> {
        for (device in snapshot) {
            val previous = smoothedRssi[device.address]
            smoothedRssi[device.address] = if (previous == null) {
                device.rssi.toDouble()
            } else {
                alpha * device.rssi + (1 - alpha) * previous
            }
            latestDevice[device.address] = device
        }

        return smoothedRssi.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Double>> { it.value }
                    .thenBy { it.key }
            )
            .map { (address, ema) ->
                latestDevice.getValue(address).copy(rssi = ema.roundToInt())
            }
    }
}
