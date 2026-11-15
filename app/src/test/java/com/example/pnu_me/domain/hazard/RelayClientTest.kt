package com.example.pnu_me.domain.hazard

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.domain.report.ReporterLocation
import com.example.pnu_me.domain.report.SuspicionLevel
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayClientTest {
    private val now = 1_000_000L

    private val vehicleAlert = HazardAlert(
        alertId = "alert-0001-abcd",
        latitude = 35.232,
        longitude = 129.083,
        headingDegrees = 90.0,
        level = SuspicionLevel.STRONG,
        issuedAtMillis = now,
        expiresAtMillis = now + 90_000L,
        isDemo = true,
    )

    private class FakeTransport(var response: RelayHttpResponse? = null, var error: IOException? = null) :
        RelayTransport {
        val calls = mutableListOf<Triple<String, String, String?>>()

        override fun execute(method: String, path: String, body: String?): RelayHttpResponse {
            calls += Triple(method, path, body)
            error?.let { throw it }
            return response ?: RelayHttpResponse(500, "")
        }
    }

    private fun vehicleListJson(id: String = "alert-0002-abcd", lat: Double = 35.232, age: Long = 1_000L) =
        """{"alerts":[{"alertId":"$id","latitude":$lat,"longitude":129.083,"headingDegrees":null,""" +
            """"level":"STRONG","isDemo":true,"ageMillis":$age,"remainingMillis":${90_000L - age}}]}"""

    // ---------- MiniJson / codec ----------

    @Test
    fun miniJson_parsesNestedStructuresAndEscapes() {
        val parsed = MiniJson.parse("""{"a":[1,2.5,-3e2],"b":"x\"yA","c":null,"d":true}""") as Map<*, *>
        assertEquals(listOf(1.0, 2.5, -300.0), parsed["a"])
        assertEquals("x\"yA", parsed["b"])
        assertNull(parsed["c"])
        assertEquals(true, parsed["d"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun miniJson_rejectsTrailingData() {
        MiniJson.parse("""{"a":1} extra""")
    }

    @Test
    fun encodeVehicle_containsOnlyWhitelistedFields() {
        val parsed = MiniJson.parse(RelayCodec.encodeVehicle(vehicleAlert)) as Map<*, *>
        assertEquals(setOf("alertId", "latitude", "longitude", "headingDegrees", "level", "isDemo"), parsed.keys)
        assertEquals(35.232, parsed["latitude"] as Double, 1e-9)
    }

    @Test
    fun decodeVehicle_rebuildsTimesFromReceiverClock() {
        val alerts = RelayCodec.decodeVehicleList(vehicleListJson(age = 10_000L), now)
        assertEquals(1, alerts.size)
        assertEquals(now - 10_000L, alerts[0].issuedAtMillis)
        assertEquals(now + 80_000L, alerts[0].expiresAtMillis)
        assertNull(alerts[0].headingDegrees)
    }

    @Test
    fun decodeVehicle_dropsMalformedEntries() {
        val json = """{"alerts":[{"alertId":"x","latitude":"bad","longitude":1.0,"level":"STRONG",""" +
            """"isDemo":true,"ageMillis":0,"remainingMillis":1000},""" +
            """{"alertId":"y","latitude":1.0,"longitude":1.0,"level":"NOPE","isDemo":true,"ageMillis":0,"remainingMillis":1}]}"""
        assertTrue(RelayCodec.decodeVehicleList(json, now).isEmpty())
    }

    @Test
    fun cells_matchServerRuleAndCoverNeighbors() {
        assertEquals("3523:12908", RelayCells.cellOf(35.232, 129.083))
        assertEquals("-3524:-12909", RelayCells.cellOf(-35.232, -129.083))
        val cells = RelayCells.neighborhood(35.232, 129.083)
        assertEquals(9, cells.size)
        assertTrue("3522:12907" in cells && "3524:12909" in cells)
    }

    // ---------- client ----------

    @Test
    fun publish_mapsStatusCodes() {
        val transport = FakeTransport()
        val client = HazardRelayClient(transport)
        transport.response = RelayHttpResponse(201, "")
        assertEquals(RelayPublishResult.Accepted, client.publishVehicle(vehicleAlert))
        transport.response = RelayHttpResponse(429, "")
        assertEquals(RelayPublishResult.RateLimited, client.publishVehicle(vehicleAlert))
        transport.response = RelayHttpResponse(409, "")
        assertEquals(RelayPublishResult.Duplicate, client.publishVehicle(vehicleAlert))
        transport.response = RelayHttpResponse(400, "")
        assertEquals(RelayPublishResult.Rejected(400), client.publishVehicle(vehicleAlert))
        transport.error = IOException("refused")
        assertTrue(client.publishVehicle(vehicleAlert) is RelayPublishResult.Unreachable)
        assertEquals("POST", transport.calls.first().first)
        assertEquals("/v1/vehicle-alerts", transport.calls.first().second)
    }

    @Test
    fun fetch_sendsOnlyCoarseCells_neverPreciseLocation() {
        val transport = FakeTransport(RelayHttpResponse(200, """{"alerts":[]}"""))
        HazardRelayClient(transport).fetchVehicle(35.2316, 129.0825, now)
        val path = transport.calls.single().second
        assertTrue(path.startsWith("/v1/vehicle-alerts?cells="))
        assertFalse("35.2316" in path)
        assertEquals(9, path.substringAfter("cells=").split(",").size)
    }

    @Test
    fun fetch_malformedBodyIsRejected_notCrash() {
        val transport = FakeTransport(RelayHttpResponse(200, "not json"))
        assertEquals(
            RelayFetchResult.Rejected(200),
            HazardRelayClient(transport).fetchPedestrian(35.0, 129.0, now),
        )
    }

    // ---------- receiver session ----------

    private fun location(lat: Double = 35.2316, lon: Double = 129.0825) =
        ReporterLocation(lat, lon, 5.0, now - 1_000L, headingDegrees = null, origin = TelemetryOrigin.VEHICLE)

    @Test
    fun receiver_showsBannerForNearbyAlert_onlyOnce() {
        val transport = FakeTransport(RelayHttpResponse(200, vehicleListJson()))
        val session = RelayReceiverSession(HazardRelayClient(transport), isDemo = true)
        val first = session.poll(RelayRole.VEHICLE, location(), now) as RelayPollResult.Checked
        assertEquals(1, first.received)
        assertTrue(first.banner!!.text.contains("이상 주행 차량"))
        assertFalse(first.banner!!.text.contains("음주"))
        val second = session.poll(RelayRole.VEHICLE, location(), now + 5_000L) as RelayPollResult.Checked
        assertNull(second.banner)
    }

    @Test
    fun receiver_skipsOwnAlert() {
        val transport = FakeTransport(RelayHttpResponse(200, vehicleListJson(id = "own-alert-0001")))
        val session = RelayReceiverSession(HazardRelayClient(transport), isDemo = true)
        session.markOwn("own-alert-0001")
        assertNull((session.poll(RelayRole.VEHICLE, location(), now) as RelayPollResult.Checked).banner)
    }

    @Test
    fun receiver_farAlertIsNotShown() {
        val transport = FakeTransport(RelayHttpResponse(200, vehicleListJson(lat = 35.250)))
        val session = RelayReceiverSession(HazardRelayClient(transport), isDemo = true)
        assertNull((session.poll(RelayRole.VEHICLE, location(), now) as RelayPollResult.Checked).banner)
    }

    @Test
    fun receiver_pedestrianModeShowsNeutralText() {
        val json = """{"alerts":[{"alertId":"ped-alert-0001","latitude":35.232,"longitude":129.083,""" +
            """"isDemo":true,"ageMillis":0,"remainingMillis":90000}]}"""
        val session = RelayReceiverSession(HazardRelayClient(FakeTransport(RelayHttpResponse(200, json))), true)
        val banner = (session.poll(RelayRole.PEDESTRIAN, location(), now) as RelayPollResult.Checked).banner!!
        assertTrue(banner.text.contains("보행 안전 주의"))
    }

    @Test
    fun receiver_offOrNoLocationDoesNotCallServer() {
        val transport = FakeTransport(RelayHttpResponse(200, vehicleListJson()))
        val session = RelayReceiverSession(HazardRelayClient(transport), isDemo = true)
        assertEquals(RelayPollResult.Checked(0, null), session.poll(RelayRole.OFF, location(), now))
        assertEquals(RelayPollResult.NoLocation, session.poll(RelayRole.VEHICLE, null, now))
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun receiver_serverDownIsReported() {
        val session = RelayReceiverSession(
            HazardRelayClient(FakeTransport(error = IOException("refused"))),
            isDemo = true,
        )
        assertTrue(session.poll(RelayRole.VEHICLE, location(), now) is RelayPollResult.Unreachable)
    }

    @Test
    fun publishText_neverClaimsDelivery() {
        assertFalse(RelayPublishResult.Accepted.displayText().contains("전달 완료"))
        assertTrue(RelayPublishResult.Unreachable("x").displayText().contains("연결하지 못했습니다"))
    }
}
