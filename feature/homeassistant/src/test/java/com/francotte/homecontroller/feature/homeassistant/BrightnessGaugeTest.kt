package com.francotte.homecontroller.feature.homeassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessGaugeTest {

    @Test
    fun `haut de la jauge vaut 100`() {
        assertEquals(100, brightnessFromOffset(pointerY = 0f, gaugeHeightPx = 200f))
    }

    @Test
    fun `bas de la jauge vaut 0`() {
        assertEquals(0, brightnessFromOffset(pointerY = 200f, gaugeHeightPx = 200f))
    }

    @Test
    fun `milieu vaut 50`() {
        assertEquals(50, brightnessFromOffset(pointerY = 100f, gaugeHeightPx = 200f))
    }

    @Test
    fun `au-dessus du haut est borne a 100`() {
        assertEquals(100, brightnessFromOffset(pointerY = -30f, gaugeHeightPx = 200f))
    }

    @Test
    fun `en-dessous du bas est borne a 0`() {
        assertEquals(0, brightnessFromOffset(pointerY = 260f, gaugeHeightPx = 200f))
    }

    @Test
    fun `hauteur nulle renvoie 0 sans planter`() {
        assertEquals(0, brightnessFromOffset(pointerY = 10f, gaugeHeightPx = 0f))
    }
}
