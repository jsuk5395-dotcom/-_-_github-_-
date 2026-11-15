package com.example.pnu_me.data.source

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.domain.risk.RuleBasedRiskEngine
import com.example.pnu_me.domain.stop.StopAdvice
import com.example.pnu_me.domain.stop.StopGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockStopAdvisorTest {
    @Test
    fun stopRecommended_returnsOnlyClearlyMarkedDemoCandidates() {
        val advisor = MockStopAdvisor()

        val result = advisor.advise(stopAssessment())

        assertTrue(result is StopAdvice.Available)
        val candidates = (result as StopAdvice.Available).candidates
        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.isDemo })
        assertTrue(candidates.all { "시연용" in it.label })
    }

    @Test
    fun normalAssessment_hasNoStopCandidates() {
        val result = MockStopAdvisor().advise(normalAssessment())

        assertTrue(result is StopAdvice.NoCandidates)
    }

    @Test
    fun configuredFailure_isReturnedWithoutInventingCandidates() {
        val advisor = MockStopAdvisor(mode = MockStopAdvisorMode.FAILURE)

        val result = advisor.advise(stopAssessment())

        assertTrue(result is StopAdvice.Failed)
    }

    @Test
    fun knownCandidate_requiresExplicitGuidanceRequest() {
        val advisor = MockStopAdvisor()
        val advice = advisor.advise(stopAssessment()) as StopAdvice.Available

        val result = advisor.requestGuidance(advice.candidates.first().id)

        assertTrue(result is StopGuidance.Ready)
        assertTrue((result as StopGuidance.Ready).candidate.isDemo)
    }

    @Test
    fun unknownCandidate_returnsFailure() {
        val result = MockStopAdvisor().requestGuidance("unknown")

        assertTrue(result is StopGuidance.Failed)
    }

    @Test
    fun noDataMode_rejectsPreviouslyKnownCandidateId() {
        val result = MockStopAdvisor(mode = MockStopAdvisorMode.NO_DATA)
            .requestGuidance("demo-rest-area")

        assertTrue(result is StopGuidance.Failed)
    }

    @Test
    fun candidateIdWithoutCurrentAdvice_isRejected() {
        val result = MockStopAdvisor().requestGuidance("demo-rest-area")

        assertTrue(result is StopGuidance.Failed)
    }

    @Test
    fun candidateBecomesStaleAfterAdviceChanges() {
        val advisor = MockStopAdvisor()
        val candidate = (
            advisor.advise(stopAssessment()) as StopAdvice.Available
            ).candidates.first()
        advisor.advise(normalAssessment())

        val result = advisor.requestGuidance(candidate.id)

        assertTrue(result is StopGuidance.Failed)
    }

    @Test
    fun cancelWithoutPreparedGuidance_returnsFailure() {
        val result = MockStopAdvisor().cancelGuidance()

        assertTrue(result is StopGuidance.Failed)
    }

    @Test
    fun duplicateGuidanceRequestWhileActive_isRejected() {
        val advisor = MockStopAdvisor()
        val candidate = (
            advisor.advise(stopAssessment()) as StopAdvice.Available
            ).candidates.first()
        advisor.requestGuidance(candidate.id)

        val duplicate = advisor.requestGuidance(candidate.id)

        assertTrue(duplicate is StopGuidance.Failed)
    }

    @Test
    fun cancelledCandidateId_cannotBeReusedWithoutFreshAdvice() {
        val advisor = MockStopAdvisor()
        val candidate = (
            advisor.advise(stopAssessment()) as StopAdvice.Available
            ).candidates.first()
        advisor.requestGuidance(candidate.id)
        advisor.cancelGuidance()

        val staleRequest = advisor.requestGuidance(candidate.id)

        assertTrue(staleRequest is StopGuidance.Failed)
    }

    @Test
    fun preparedGuidanceCanBeCancelledOnlyOnce() {
        val advisor = MockStopAdvisor()
        val candidate = (
            advisor.advise(stopAssessment()) as StopAdvice.Available
            ).candidates.first()
        advisor.requestGuidance(candidate.id)

        assertEquals(StopGuidance.Cancelled, advisor.cancelGuidance())
        assertTrue(advisor.cancelGuidance() is StopGuidance.Failed)
    }

    private fun stopAssessment(): RiskAssessment = RuleBasedRiskEngine().analyze(
        samples = listOf(
            sample(0, 10.0),
            sample(2_000, 16.0),
            sample(4_000, 9.0),
            sample(6_000, 16.5),
            sample(8_000, 9.0),
        ),
        origin = TelemetryOrigin.MOCK,
    )

    private fun normalAssessment(): RiskAssessment = RuleBasedRiskEngine().analyze(
        samples = listOf(
            sample(0, 10.0),
            sample(1_000, 10.2),
            sample(2_000, 10.3),
            sample(3_000, 10.4),
        ),
        origin = TelemetryOrigin.MOCK,
    )

    private fun sample(time: Long, speed: Double) = TelemetrySample(
        timestampMillis = time,
        speedMps = speed,
        origin = TelemetryOrigin.MOCK,
    )
}
