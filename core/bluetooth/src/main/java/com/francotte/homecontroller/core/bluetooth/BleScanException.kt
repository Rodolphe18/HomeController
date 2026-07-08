package com.francotte.homecontroller.core.bluetooth

/**
 * Émise dans le Flow de [BleScanner.scan] quand le scan échoue.
 *
 * @property errorCode code d'erreur (codes ScanCallback.SCAN_FAILED_*,
 *   ou une valeur négative interne si l'adaptateur est indisponible).
 */
class BleScanException(val errorCode: Int) :
    Exception("Le scan BLE a échoué (code $errorCode)")
