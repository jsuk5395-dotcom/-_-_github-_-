package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.stop.StopGuidance
import org.junit.Assert.assertEquals
import org.junit.Test

class StopGuidanceUiStateTest {
    @Test
    fun cancelledResult_isTheOnlyCancellationSuccess() {
        val state = stopGuidanceUiState(
            result = Result.success(StopGuidance.Cancelled),
        )

        assertEquals(GuidanceUiStatus.CANCELLED, state.status)
    }

    @Test
    fun failedCancellation_isShownAsFailureAndRemainsRetryable() {
        val state = stopGuidanceUiState(
            result = Result.success(StopGuidance.Failed("취소 실패")),
        )

        assertEquals(GuidanceUiStatus.FAILED, state.status)
        assertEquals("취소 실패", state.message)
        assertEquals(true, state.canRetry)
    }

    @Test
    fun cancellationException_isShownAsFailureAndRemainsRetryable() {
        val state = stopGuidanceUiState(
            result = Result.failure(IllegalStateException("service disconnected")),
        )

        assertEquals(GuidanceUiStatus.FAILED, state.status)
        assertEquals(true, state.canRetry)
    }
}
