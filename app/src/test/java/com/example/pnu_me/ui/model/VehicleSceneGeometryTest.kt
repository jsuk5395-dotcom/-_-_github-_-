package com.example.pnu_me.ui.model

import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSceneGeometryTest {
    @Test
    fun landscapeVehiclePane_keepsVehicleInsideVisibleScene() {
        val geometry = vehicleSceneGeometry(widthPx = 486f, heightPx = 500f)

        assertTrue(geometry.carTop >= 0f)
        assertTrue(geometry.carBottom <= 500f * 0.85f)
    }

    @Test
    fun portraitHomeCard_keepsVehicleInsideVisibleScene() {
        val geometry = vehicleSceneGeometry(widthPx = 800f, heightPx = 570f)

        assertTrue(geometry.carTop >= 0f)
        assertTrue(geometry.carBottom <= 570f * 0.85f)
    }

    @Test
    fun suvProfile_keepsWideUprightStanceFromNoseToRear() {
        val noseWidth = suvBodyWidthFraction(verticalFraction = 0.15f)
        val shoulderWidth = suvBodyWidthFraction(verticalFraction = 0.50f)
        val rearWidth = suvBodyWidthFraction(verticalFraction = 0.85f)

        assertTrue(noseWidth >= 0.78f)
        assertTrue(shoulderWidth >= 0.94f)
        assertTrue(rearWidth >= 0.88f)
        assertTrue(shoulderWidth - noseWidth <= 0.16f)
    }
}
