package com.francotte.homecontroller.data.connection

import java.util.UUID

/** UUID du profil GATT HomeController (identiques au firmware ESP32). */
object GattProfile {
    val SERVICE_UUID: UUID = UUID.fromString("990c9205-4132-4360-aa80-2bd5ce8d6e93")
    val LED_UUID: UUID = UUID.fromString("d64abbf2-4dab-4198-8a93-fb7348943972")
    val COUNTER_UUID: UUID = UUID.fromString("2a554b2a-5f4b-4c89-9a59-e4d8f6d4b9d8")
    // Descripteur standard Client Characteristic Configuration (active les notifications).
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
