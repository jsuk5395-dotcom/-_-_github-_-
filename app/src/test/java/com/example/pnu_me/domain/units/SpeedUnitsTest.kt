package com.example.pnu_me.domain.units

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedUnitsTest {
    @Test
    fun metersPerSecond_isConvertedToKilometersPerHour() {
        assertEquals(
            0.0,
            SpeedUnits.metersPerSecondToKilometersPerHour(0.0),
            0.0001,
        )
        assertEquals(
            36.0,
            SpeedUnits.metersPerSecondToKilometersPerHour(10.0),
            0.0001,
        )
    }
}
