package com.example.pnu_me.domain.report

import com.example.pnu_me.domain.report.ImpairedDrivingCue.ACCELERATING_FOR_NO_REASON
import com.example.pnu_me.domain.report.ImpairedDrivingCue.DRIFTING
import com.example.pnu_me.domain.report.ImpairedDrivingCue.SLOW_SPEED
import com.example.pnu_me.domain.report.ImpairedDrivingCue.STOPPING_PROBLEM
import com.example.pnu_me.domain.report.ImpairedDrivingCue.WEAVING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspicionEvaluatorTest {
    private val evaluator = SuspicionEvaluator()
    private val now = 100_000L

    private fun obs(cue: ImpairedDrivingCue, at: Long = now, source: CueSource = CueSource.MOCK_SCENARIO) =
        CueObservation(cue, at, source)

    @Test
    fun noObservations_isNone() {
        val result = evaluator.evaluate(emptyList(), now)
        assertEquals(SuspicionLevel.NONE, result.level)
        assertFalse(result.shouldAskOccupant)
    }

    @Test
    fun weavingPlusOtherCue_isStrong() {
        val result = evaluator.evaluate(listOf(obs(WEAVING), obs(SLOW_SPEED)), now)
        assertEquals(SuspicionLevel.STRONG, result.level)
        assertTrue(result.shouldAskOccupant)
    }

    @Test
    fun weavingAlone_isModerate() {
        assertEquals(SuspicionLevel.MODERATE, evaluator.evaluate(listOf(obs(WEAVING)), now).level)
    }

    @Test
    fun twoDistinctNonWeavingCues_isModerate() {
        val result = evaluator.evaluate(listOf(obs(DRIFTING), obs(STOPPING_PROBLEM)), now)
        assertEquals(SuspicionLevel.MODERATE, result.level)
    }

    @Test
    fun highProbabilitySingleCue_isModerate() {
        assertEquals(
            SuspicionLevel.MODERATE,
            evaluator.evaluate(listOf(obs(ACCELERATING_FOR_NO_REASON)), now).level,
        )
    }

    @Test
    fun singleOrdinaryCue_repeated_isWeakAndDoesNotAsk() {
        val result = evaluator.evaluate(listOf(obs(SLOW_SPEED, now - 10), obs(SLOW_SPEED)), now)
        assertEquals(SuspicionLevel.WEAK, result.level)
        assertFalse(result.shouldAskOccupant)
    }

    @Test
    fun observationsOutsideWindow_areIgnored() {
        val old = now - 60_001L
        val result = evaluator.evaluate(listOf(obs(WEAVING, old), obs(SLOW_SPEED, old)), now)
        assertEquals(SuspicionLevel.NONE, result.level)
    }

    @Test
    fun observationExactlyAtWindowEdge_isIncluded() {
        val result = evaluator.evaluate(listOf(obs(WEAVING, now - 60_000L)), now)
        assertEquals(SuspicionLevel.MODERATE, result.level)
    }

    @Test
    fun futureTimestamp_isIgnored() {
        assertEquals(SuspicionLevel.NONE, evaluator.evaluate(listOf(obs(WEAVING, now + 1)), now).level)
    }

    @Test
    fun mixedSources_isNone() {
        val result = evaluator.evaluate(
            listOf(obs(WEAVING), obs(SLOW_SPEED, source = CueSource.OCCUPANT_OBSERVATION)),
            now,
        )
        assertEquals(SuspicionLevel.NONE, result.level)
    }

    @Test
    fun sameInput_isDeterministic() {
        val input = listOf(obs(WEAVING), obs(DRIFTING))
        assertEquals(evaluator.evaluate(input, now), evaluator.evaluate(input, now))
    }
}
