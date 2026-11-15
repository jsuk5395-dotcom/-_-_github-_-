package com.example.pnu_me.domain.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitStatePersistenceTest {
    @Test
    fun roundTripPreservesCurrentState() {
        val state = CockpitState(
            gear = Gear.DRIVE,
            body = BodyState(locked = false, trunkOpen = true),
            navigation = NavigationState(destinationId = "home"),
            security = SecurityState(parkingWatch = true, clipsSaved = 3),
            serviceMode = ServiceMode.TOW,
            profileIndex = 1,
        )

        val encoded = CockpitStatePersistence.encode(state)

        assertTrue(encoded != null)
        assertEquals(state, CockpitStatePersistence.decode(requireNotNull(encoded)))
    }

    @Test
    fun decodeRejectsLegacyObjectPayload() {
        assertNull(CockpitStatePersistence.decode(CockpitState()))
    }

    @Test
    fun decodeRejectsCorruptBytes() {
        assertNull(CockpitStatePersistence.decode(byteArrayOf(1, 2, 3)))
    }
}
