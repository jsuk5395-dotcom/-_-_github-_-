package com.example.pnu_me.domain.risk

import com.example.pnu_me.data.model.TelemetryOrigin

data class RiskAssessment(
    val state: RiskState,
    val origin: TelemetryOrigin?,
    val title: String,
    val guidance: String,
    val quality: DataQualityReport,
    val speedPoints: List<SpeedPoint>,
    val events: List<RiskEvent>,
    val averageSpeedMps: Double?,
    val maximumAbsoluteAccelerationMps2: Double?,
)
