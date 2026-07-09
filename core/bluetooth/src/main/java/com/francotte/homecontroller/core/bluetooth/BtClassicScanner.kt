package com.francotte.homecontroller.core.bluetooth

import com.francotte.homecontroller.core.model.BtClassicDevice
import kotlinx.coroutines.flow.Flow

/**
 * Contrat de découverte Bluetooth Classic. Le [Flow] est FROID : la découverte
 * démarre à la souscription, fait UNE passe (~12 s) puis le Flow se termine ;
 * l'annulation arrête la découverte. Chaque émission = liste courante des
 * appareils détectés (dédupliquée par adresse MAC).
 */
interface BtClassicScanner {
    fun scan(): Flow<List<BtClassicDevice>>
}
