package com.francotte.homecontroller.domain.scan

import com.francotte.homecontroller.domain.model.BleDevice
import kotlinx.coroutines.flow.Flow

/**
 * Contrat de scan BLE. L'implémentation vit dans la couche data.
 *
 * Le [Flow] retourné est FROID : le scan démarre à la souscription
 * et s'arrête automatiquement à l'annulation. Chaque émission est la
 * liste courante des appareils détectés (dédupliquée par adresse MAC).
 */
interface BleScanner {
    fun scan(): Flow<List<BleDevice>>
}
