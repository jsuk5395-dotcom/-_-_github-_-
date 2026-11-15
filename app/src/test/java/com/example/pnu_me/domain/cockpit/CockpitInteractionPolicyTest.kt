package com.example.pnu_me.domain.cockpit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitInteractionPolicyTest {
    @Test
    fun parkedMockGear_allowsComplexDemoControls() {
        assertTrue(
            CockpitInteractionPolicy.allowsComplexControls(
                state = CockpitState(gear = Gear.PARK),
                isAutomotiveDevice = false,
            ),
        )
    }

    @Test
    fun automotiveDevice_blocksComplexControlsUntilRealPolicyIsIntegrated() {
        assertFalse(
            CockpitInteractionPolicy.allowsComplexControls(
                state = CockpitState(gear = Gear.PARK),
                isAutomotiveDevice = true,
            ),
        )
    }

    @Test
    fun nonParkMockGears_blockComplexDemoControls() {
        listOf(Gear.REVERSE, Gear.NEUTRAL, Gear.DRIVE).forEach { gear ->
            assertFalse(
                "가상 기어 $gear 에서는 복잡 조작을 막아야 한다",
                CockpitInteractionPolicy.allowsComplexControls(
                    state = CockpitState(gear = gear),
                    isAutomotiveDevice = false,
                ),
            )
        }
    }
}
