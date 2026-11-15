package com.example.pnu_me.domain.risk

import com.example.pnu_me.data.model.DataQuality
import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample

enum class ValidationIssue {
    NONE,
    SOURCE_ERROR,
    EMPTY_DATA,
    INSUFFICIENT_SAMPLES,
    ORIGIN_MISMATCH,
    NEGATIVE_TIMESTAMP,
    NON_MONOTONIC_TIME,
    EXCESSIVE_GAP,
    MISSING_SPEED,
    INVALID_SPEED,
}

data class DataQualityReport(
    val isUsable: Boolean,
    val issue: ValidationIssue,
    val message: String,
    val validSampleCount: Int,
    val totalSampleCount: Int,
)

class DataQualityValidator(
    private val config: DemoRiskConfig = DemoRiskConfig(),
) {
    fun validate(
        samples: List<TelemetrySample>,
        expectedOrigin: TelemetryOrigin? = null,
    ): DataQualityReport {
        fun rejected(issue: ValidationIssue, message: String) = DataQualityReport(
            isUsable = false,
            issue = issue,
            message = message,
            validSampleCount = samples.count {
                it.quality == DataQuality.VALID && it.speedMps != null
            },
            totalSampleCount = samples.size,
        )

        if (samples.size < config.minimumSampleCount) {
            return rejected(
                ValidationIssue.INSUFFICIENT_SAMPLES,
                "분석에 필요한 표본 수가 부족합니다.",
            )
        }

        if (
            samples.map { it.origin }.distinct().size != 1 ||
            expectedOrigin != null && samples.any { it.origin != expectedOrigin }
        ) {
            return rejected(
                ValidationIssue.ORIGIN_MISMATCH,
                "서로 다른 데이터 출처가 한 분석에 섞여 있습니다.",
            )
        }

        if (samples.any { it.timestampMillis < 0L }) {
            return rejected(
                ValidationIssue.NEGATIVE_TIMESTAMP,
                "표본의 상대 시각은 0ms 이상이어야 합니다.",
            )
        }

        samples.zipWithNext().forEach { (previous, current) ->
            if (current.timestampMillis <= previous.timestampMillis) {
                return rejected(
                    ValidationIssue.NON_MONOTONIC_TIME,
                    "표본 시각이 순서대로 증가하지 않습니다.",
                )
            }
            if (
                current.timestampMillis - previous.timestampMillis >
                config.maximumSampleGapMillis
            ) {
                return rejected(
                    ValidationIssue.EXCESSIVE_GAP,
                    "표본 사이의 시간 간격이 너무 큽니다.",
                )
            }
        }

        if (samples.any { it.quality != DataQuality.VALID || it.speedMps == null }) {
            return rejected(
                ValidationIssue.MISSING_SPEED,
                "속도 데이터가 없거나 사용할 수 없습니다.",
            )
        }

        if (samples.any {
                val speed = it.speedMps
                speed == null ||
                    !speed.isFinite() ||
                    speed !in config.minimumSpeedMps..config.maximumSpeedMps
            }
        ) {
            return rejected(
                ValidationIssue.INVALID_SPEED,
                "속도 값이 데모 분석 범위를 벗어났습니다.",
            )
        }

        return DataQualityReport(
            isUsable = true,
            issue = ValidationIssue.NONE,
            message = "데이터 품질이 분석 조건을 충족합니다.",
            validSampleCount = samples.size,
            totalSampleCount = samples.size,
        )
    }
}
