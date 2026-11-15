package com.example.pnu_me.platform.vehicle

import android.os.SystemClock
import com.example.pnu_me.data.model.TelemetrySample
import com.example.pnu_me.data.source.LiveTelemetryWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [DrivingMonitorService]가 받은 실차 데이터(VEHICLE)를 같은 앱 프로세스의 화면·분석 엔진에 전달한다.
 *
 * 위험 판단은 여기서 하지 않는다. 판단은 MOCK과 같은 [com.example.pnu_me.domain.risk.RuleBasedRiskEngine]
 * 하나로만 한다(SAFE DRIVE 서비스 내부 판단 로직은 엔진으로 이식됨).
 */
object VehicleTelemetryHub {
    private val window = LiveTelemetryWindow()
    private val mutableSamples = MutableStateFlow<List<TelemetrySample>>(emptyList())
    private val mutableConnected = MutableStateFlow(false)

    /** 최근 60초 VEHICLE 표본. 0.5초 이상 간격, 시각은 수신 시작 기준 상대값 */
    val samples: StateFlow<List<TelemetrySample>> = mutableSamples.asStateFlow()

    /** 감시 서비스가 차량 속도 수신을 등록했는지 */
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    @Synchronized
    fun onSpeed(speedMps: Double?, nowMillis: Long = SystemClock.elapsedRealtime()) {
        if (window.onSpeed(nowMillis, speedMps)) {
            mutableSamples.value = window.snapshot()
        }
    }

    @Synchronized
    fun onSteering(degrees: Double) {
        window.onSteering(degrees)
    }

    @Synchronized
    fun refresh(nowMillis: Long = SystemClock.elapsedRealtime()) {
        val freshSamples = window.snapshot(nowMillis)
        if (freshSamples != mutableSamples.value) {
            mutableSamples.value = freshSamples
        }
    }

    @Synchronized
    fun onConnectionChanged(isConnected: Boolean) {
        mutableConnected.value = isConnected
        if (!isConnected) {
            window.clear()
            mutableSamples.value = emptyList()
        }
    }
}
