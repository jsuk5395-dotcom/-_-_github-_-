package com.example.pnu_me.platform.vehicle

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTelemetryHubTest {
    @After
    fun resetHub() {
        VehicleTelemetryHub.onConnectionChanged(false)
    }

    @Test
    fun refreshAfterMaximumGap_clearsSamplesWhileConnectionRegistrationRemains() {
        VehicleTelemetryHub.onConnectionChanged(true)
        VehicleTelemetryHub.onSpeed(speedMps = 10.0, nowMillis = 1_000L)
        assertTrue(VehicleTelemetryHub.samples.value.isNotEmpty())

        VehicleTelemetryHub.refresh(nowMillis = 4_001L)

        assertTrue(VehicleTelemetryHub.samples.value.isEmpty())
        assertTrue(VehicleTelemetryHub.connected.value)
    }
}
