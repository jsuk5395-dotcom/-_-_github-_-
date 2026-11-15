package com.example.pnu_me.domain.risk

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample

interface RiskEngine {
    fun analyze(
        samples: List<TelemetrySample>,
        origin: TelemetryOrigin,
    ): RiskAssessment
}
