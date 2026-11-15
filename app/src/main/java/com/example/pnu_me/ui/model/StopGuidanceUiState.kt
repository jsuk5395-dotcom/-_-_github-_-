package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.stop.StopGuidance

enum class GuidanceUiStatus {
    READY,
    CANCELLED,
    FAILED,
}

data class StopGuidanceUiState(
    val status: GuidanceUiStatus,
    val message: String,
    val canRetry: Boolean,
)

fun stopGuidanceUiState(result: Result<StopGuidance>): StopGuidanceUiState =
    result.fold(
        onSuccess = { guidance ->
            when (guidance) {
                is StopGuidance.Ready -> StopGuidanceUiState(
                    status = GuidanceUiStatus.READY,
                    message = guidance.message,
                    canRetry = false,
                )
                StopGuidance.Cancelled -> StopGuidanceUiState(
                    status = GuidanceUiStatus.CANCELLED,
                    message = "모의 정차 안내를 취소했습니다.",
                    canRetry = false,
                )
                is StopGuidance.Failed -> StopGuidanceUiState(
                    status = GuidanceUiStatus.FAILED,
                    message = guidance.message,
                    canRetry = true,
                )
            }
        },
        onFailure = {
            StopGuidanceUiState(
                status = GuidanceUiStatus.FAILED,
                message = "모의 정차 안내 요청 중 오류가 발생했습니다.",
                canRetry = true,
            )
        },
    )
