package com.example.pnu_me.domain.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitReducerTest {
    private fun CockpitState.apply(vararg actions: CockpitAction): CockpitState =
        actions.fold(this) { state, action ->
            val outcome = CockpitReducer.reduce(state, action)
            assertNull("예상치 못한 거절: $action", outcome.rejection)
            outcome.state
        }

    private fun CockpitState.rejected(action: CockpitAction): String {
        val outcome = CockpitReducer.reduce(this, action)
        assertEquals("거절 시 상태가 바뀌면 안 된다", this, outcome.state)
        return requireNotNull(outcome.rejection) { "거절되어야 한다: $action" }
    }

    @Test
    fun trunkAndFrontStorageOpenOnlyWhileParked() {
        val driving = CockpitState().apply(CockpitAction.ShiftGear(Gear.DRIVE))

        assertEquals(
            CockpitReducer.REASON_PARKED_ONLY,
            driving.rejected(CockpitAction.SetTrunkOpen(true)),
        )
        assertEquals(
            CockpitReducer.REASON_PARKED_ONLY,
            driving.rejected(CockpitAction.SetFrontStorageOpen(true)),
        )
        assertTrue(CockpitState().apply(CockpitAction.SetTrunkOpen(true)).body.trunkOpen)
    }

    @Test
    fun cannotShiftWithOpenClosure() {
        val trunkOpen = CockpitState().apply(CockpitAction.SetTrunkOpen(true))
        assertEquals(
            CockpitReducer.REASON_CLOSURE_OPEN,
            trunkOpen.rejected(CockpitAction.ShiftGear(Gear.DRIVE)),
        )

    }

    @Test
    fun shiftingOutOfParkLocksDoorsAndEndsParkedModes() {
        val parked = CockpitState().apply(
            CockpitAction.SetLocked(false),
            CockpitAction.SetParkingWatch(true),
        )

        val driving = parked.apply(CockpitAction.ShiftGear(Gear.DRIVE))

        assertTrue(driving.body.locked)
        assertFalse(driving.security.parkingWatch)
        assertEquals(
            CockpitReducer.REASON_PARKED_ONLY,
            driving.rejected(CockpitAction.SetLocked(false)),
        )
    }

    @Test
    fun pinBlocksDrivingUntilEnteredAndRelocksOnPark() {
        val withPin = CockpitState().apply(CockpitAction.SetPin("1234"))

        assertEquals(
            CockpitReducer.REASON_PIN_REQUIRED,
            withPin.rejected(CockpitAction.ShiftGear(Gear.DRIVE)),
        )
        assertEquals(
            CockpitReducer.REASON_PIN_MISMATCH,
            withPin.rejected(CockpitAction.EnterPin("0000")),
        )

        val parkedAgain = withPin.apply(
            CockpitAction.EnterPin("1234"),
            CockpitAction.ShiftGear(Gear.DRIVE),
            CockpitAction.ShiftGear(Gear.PARK),
        )
        assertFalse(parkedAgain.security.driveUnlocked)
    }

    @Test
    fun pinMustBeFourDigits() {
        listOf("", "123", "12345", "12a4").forEach { pin ->
            assertEquals(
                CockpitReducer.REASON_PIN_FORMAT,
                CockpitState().rejected(CockpitAction.SetPin(pin)),
            )
        }
    }

    @Test
    fun valetModeLocksStorageAndNeedsPinToExit() {
        val valet = CockpitState().apply(
            CockpitAction.SetPin("4321"),
            CockpitAction.SetGloveboxOpen(true),
            CockpitAction.SetValetMode(true),
        )

        assertFalse(valet.body.gloveboxOpen)
        assertEquals(DriveFeel.GENTLE, valet.drive.driveFeel)
        assertTrue(valet.security.speedLimitMode)
        assertEquals(CockpitReducer.REASON_VALET, valet.rejected(CockpitAction.SetGloveboxOpen(true)))
        assertEquals(CockpitReducer.REASON_VALET, valet.rejected(CockpitAction.SetFrontStorageOpen(true)))
        assertEquals(CockpitReducer.REASON_VALET, valet.rejected(CockpitAction.SetDriveFeel(DriveFeel.SPORT)))
        assertEquals(CockpitReducer.REASON_VALET, valet.rejected(CockpitAction.SetSpeedLimitMode(false)))
        assertEquals(
            CockpitReducer.REASON_PIN_MISMATCH,
            valet.rejected(CockpitAction.SetValetMode(false, "0000")),
        )
        assertFalse(valet.apply(CockpitAction.SetValetMode(false, "4321")).security.valetMode)
    }

    @Test
    fun carWashModeClosesUpAndOnlyAllowsNeutral() {
        val wash = CockpitState().apply(
            CockpitAction.SetWindowsVented(true),
            CockpitAction.SetParkingWatch(true),
            CockpitAction.SetServiceMode(ServiceMode.CAR_WASH),
        )

        assertFalse(wash.body.windowsVented)
        assertTrue(wash.body.mirrorsFolded)
        assertEquals(WiperMode.OFF, wash.body.wipers)
        assertFalse(wash.security.parkingWatch)
        assertEquals(
            CockpitReducer.REASON_SERVICE_MODE,
            wash.rejected(CockpitAction.SetWipers(WiperMode.AUTO)),
        )
        assertEquals(
            CockpitReducer.REASON_SERVICE_MODE,
            wash.rejected(CockpitAction.ShiftGear(Gear.DRIVE)),
        )
        assertEquals(Gear.NEUTRAL, wash.apply(CockpitAction.ShiftGear(Gear.NEUTRAL)).gear)
    }

    @Test
    fun guidanceOnlyAcceptsKnownDestinations() {
        assertEquals(
            CockpitReducer.REASON_UNKNOWN_DESTINATION,
            CockpitState().rejected(CockpitAction.StartGuidance("nowhere")),
        )
        val guiding = CockpitState().apply(CockpitAction.StartGuidance("home"))
        assertTrue(guiding.navigation.guiding)
        assertFalse(guiding.apply(CockpitAction.CancelGuidance).navigation.guiding)
    }

    @Test
    fun emergencyBrakingSettingChangesOnlyWhileParked() {
        val driving = CockpitState().apply(CockpitAction.ShiftGear(Gear.DRIVE))
        assertEquals(
            CockpitReducer.REASON_PARKED_ONLY,
            driving.rejected(CockpitAction.SetEmergencyBraking(false)),
        )
    }

    @Test
    fun arrivalEstimateFlagsEnergyStop() {
        assertEquals(324, CockpitEstimates.rangeKm(72))
        assertFalse(CockpitEstimates.needsEnergyStop(72, 12.4))
        assertTrue(CockpitEstimates.needsEnergyStop(72, 380.0))
        assertTrue(CockpitEstimates.arrivalPercent(72, 380.0) < 0)
    }

    @Test
    fun sameInputGivesSameOutcome() {
        val action = CockpitAction.SetServiceMode(ServiceMode.CAR_WASH)
        assertEquals(
            CockpitReducer.reduce(CockpitState(), action),
            CockpitReducer.reduce(CockpitState(), action),
        )
    }
}
