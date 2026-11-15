package com.example.pnu_me.domain.risk

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample
import kotlin.math.abs

/**
 * 입력과 설정이 같으면 항상 같은 결과를 내는 연구용 규칙 엔진.
 *
 * 이 결과는 음주, 졸음, 사고 가능성 또는 운전 가능 여부의 진단이 아니다.
 */
class RuleBasedRiskEngine(
    private val config: DemoRiskConfig = DemoRiskConfig(),
    private val validator: DataQualityValidator = DataQualityValidator(config),
    private val featureExtractor: FeatureExtractor = FeatureExtractor(config),
) : RiskEngine {
    override fun analyze(
        samples: List<TelemetrySample>,
        origin: TelemetryOrigin,
    ): RiskAssessment {
        val observedOrigin = samples.map { it.origin }.distinct().singleOrNull()
        val visiblePoints = samples.mapNotNull { sample ->
            sample.speedMps?.takeIf(Double::isFinite)?.let { speed ->
                SpeedPoint(sample.timestampMillis, speed)
            }
        }
        val quality = validator.validate(samples, expectedOrigin = origin)

        if (!quality.isUsable) {
            return RiskAssessment(
                state = RiskState.UNAVAILABLE,
                origin = observedOrigin,
                title = "측정 불가",
                guidance = quality.message,
                quality = quality,
                speedPoints = if (quality.issue == ValidationIssue.ORIGIN_MISMATCH) {
                    emptyList()
                } else {
                    visiblePoints
                },
                events = emptyList(),
                averageSpeedMps = null,
                maximumAbsoluteAccelerationMps2 = null,
            )
        }

        val features = featureExtractor.extract(samples)
        val steeringEvents = features.events.filter { it.type == RiskEventType.STEERING_REVERSAL }
        val speedEvents = features.events.filter { it.isSpeedChange }
        // SAFE DRIVE 복합 규칙: 불규칙 조향과 급가감속이 짧은 시간 안에 함께 나타나면 정차 권고
        val hasCompositePattern = steeringEvents.any { steering ->
            speedEvents.any { speed ->
                abs(speed.timestampMillis - steering.timestampMillis) <=
                    config.compositeEventWindowMillis
            }
        }
        val state = when {
            hasCompositePattern -> RiskState.STOP_RECOMMENDED
            features.events.size >= config.stopRecommendedEventCount ->
                RiskState.STOP_RECOMMENDED
            features.events.isNotEmpty() -> RiskState.REVIEW
            else -> RiskState.NORMAL
        }
        val (title, guidance) = when (state) {
            RiskState.NORMAL ->
                "정상" to "데모 규칙에서 반복적인 비정상 가감속이 확인되지 않았습니다."
            RiskState.REVIEW ->
                "상태 점검 권고" to if (speedEvents.isEmpty()) {
                    "조향 방향이 짧게 반복해서 바뀌었습니다. 안전한 곳에서 상태를 확인하세요."
                } else {
                    "단발성 큰 속도 변화가 있습니다. 안전한 곳에서 상태를 확인하세요."
                }
            RiskState.STOP_RECOMMENDED ->
                "안전한 정차 권고" to if (hasCompositePattern) {
                    "반복 조향 변화와 큰 속도 변화가 함께 나타났습니다. " +
                        "안전한 장소에 정차한 뒤 상태를 점검하세요."
                } else {
                    "반복적인 큰 속도 변화가 있습니다. 안전한 장소에 정차한 뒤 상태를 점검하세요."
                }
            RiskState.UNAVAILABLE -> error("검증 완료 입력에서는 도달할 수 없습니다.")
        }

        return RiskAssessment(
            state = state,
            origin = requireNotNull(observedOrigin),
            title = title,
            guidance = guidance,
            quality = quality,
            speedPoints = features.speedPoints,
            events = features.events,
            averageSpeedMps = features.averageSpeedMps,
            maximumAbsoluteAccelerationMps2 =
                features.maximumAbsoluteAccelerationMps2,
        )
    }
}
