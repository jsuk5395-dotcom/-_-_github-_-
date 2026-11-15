package com.example.pnu_me.domain.risk

import com.example.pnu_me.data.model.TelemetrySample
import kotlin.math.abs

data class SpeedPoint(
    val timestampMillis: Long,
    val speedMps: Double,
)

enum class RiskEventType {
    ABRUPT_ACCELERATION,
    ABRUPT_BRAKING,

    /** 짧은 시간 안 조향 변화 방향이 반복해서 바뀜 (SAFE DRIVE 불규칙 조향 규칙) */
    STEERING_REVERSAL,
}

/**
 * @param accelerationMps2 급가감속 이벤트의 가속도. 조향 이벤트는 속도 변화와 무관하므로 0.0
 */
data class RiskEvent(
    val timestampMillis: Long,
    val accelerationMps2: Double,
    val type: RiskEventType,
) {
    val isSpeedChange: Boolean
        get() = type == RiskEventType.ABRUPT_ACCELERATION || type == RiskEventType.ABRUPT_BRAKING
}

data class DrivingFeatures(
    val speedPoints: List<SpeedPoint>,
    val events: List<RiskEvent>,
    val averageSpeedMps: Double,
    val maximumAbsoluteAccelerationMps2: Double,
)

class FeatureExtractor(
    private val config: DemoRiskConfig = DemoRiskConfig(),
) {
    fun extract(samples: List<TelemetrySample>): DrivingFeatures {
        val speedPoints = samples.map {
            SpeedPoint(
                timestampMillis = it.timestampMillis,
                speedMps = requireNotNull(it.speedMps),
            )
        }

        var lastEventTimestamp: Long? = null
        var maximumAbsoluteAcceleration = 0.0
        val events = buildList {
            speedPoints.zipWithNext().forEach { (previous, current) ->
                val deltaSeconds =
                    (current.timestampMillis - previous.timestampMillis) / 1_000.0
                val acceleration = (current.speedMps - previous.speedMps) / deltaSeconds
                maximumAbsoluteAcceleration =
                    maxOf(maximumAbsoluteAcceleration, abs(acceleration))

                val isSeparated = lastEventTimestamp == null ||
                    current.timestampMillis - requireNotNull(lastEventTimestamp) >=
                    config.minimumEventSeparationMillis
                if (
                    abs(acceleration) >= config.abruptAccelerationThresholdMps2 &&
                    isSeparated
                ) {
                    add(
                        RiskEvent(
                            timestampMillis = current.timestampMillis,
                            accelerationMps2 = acceleration,
                            type = if (acceleration >= 0.0) {
                                RiskEventType.ABRUPT_ACCELERATION
                            } else {
                                RiskEventType.ABRUPT_BRAKING
                            },
                        ),
                    )
                    lastEventTimestamp = current.timestampMillis
                }
            }
        }

        return DrivingFeatures(
            speedPoints = speedPoints,
            events = (events + extractSteeringReversals(samples))
                .sortedBy { it.timestampMillis },
            averageSpeedMps = speedPoints.map { it.speedMps }.average(),
            maximumAbsoluteAccelerationMps2 = maximumAbsoluteAcceleration,
        )
    }

    /**
     * SAFE DRIVE `DrivingMonitorService.analyzeServiceSteering`을 순수 함수로 옮긴 규칙.
     * 조향각이 없는 표본(MOCK CSV 등)에서는 이벤트를 만들지 않는다.
     */
    private fun extractSteeringReversals(samples: List<TelemetrySample>): List<RiskEvent> {
        var previousAngle: Double? = null
        var lastDirection = 0
        var reversals = 0
        var windowStart: Long? = null
        var lastEventTimestamp: Long? = null

        return buildList {
            samples.forEach { sample ->
                val speed = sample.speedMps
                val angle = sample.steeringDegrees?.takeIf(Double::isFinite)
                if (
                    speed == null ||
                    speed < config.steeringAnalysisMinimumSpeedMps ||
                    angle == null
                ) {
                    previousAngle = null
                    lastDirection = 0
                    reversals = 0
                    windowStart = null
                    return@forEach
                }

                val previous = previousAngle
                previousAngle = angle
                if (previous == null) return@forEach

                val difference = angle - previous
                if (abs(difference) < config.steeringMinimumChangeDegrees) return@forEach
                val direction = if (difference > 0.0) 1 else -1
                val now = sample.timestampMillis

                val start = windowStart
                if (start == null || now - start > config.steeringReversalWindowMillis) {
                    windowStart = now
                    reversals = 0
                    lastDirection = direction
                    return@forEach
                }

                if (lastDirection != 0 && direction != lastDirection) {
                    reversals++
                    val lastEvent = lastEventTimestamp
                    if (
                        reversals >= config.steeringReversalCount &&
                        (lastEvent == null || now - lastEvent >= config.steeringEventCooldownMillis)
                    ) {
                        add(
                            RiskEvent(
                                timestampMillis = now,
                                accelerationMps2 = 0.0,
                                type = RiskEventType.STEERING_REVERSAL,
                            ),
                        )
                        lastEventTimestamp = now
                        windowStart = now
                        reversals = 0
                    }
                }
                lastDirection = direction
            }
        }
    }
}
