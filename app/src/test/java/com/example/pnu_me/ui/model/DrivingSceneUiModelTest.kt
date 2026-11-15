package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.risk.RiskState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingSceneUiModelTest {
    @Test
    fun normalState_isPresentedAsMockDriving() {
        val model = drivingSceneUiModel(RiskState.NORMAL)

        assertTrue("모의 운행 중" in model.statusLabel)
        assertTrue("MOCK" in model.sourceNotice)
        assertFalse("실제 차량 연결됨" in model.sourceNotice)
        assertFalse(model.needsAttention)
    }

    @Test
    fun stopRecommendedState_keepsTheSafetyActionVisible() {
        val model = drivingSceneUiModel(RiskState.STOP_RECOMMENDED)

        assertTrue("안전 정차 권고" in model.statusLabel)
        assertTrue(model.needsAttention)
    }

    @Test
    fun unavailableState_doesNotClaimLiveVehicleData() {
        val model = drivingSceneUiModel(RiskState.UNAVAILABLE)

        assertTrue("확인 불가" in model.statusLabel)
        assertTrue("실제 차량 연결 아님" in model.sourceNotice)
        assertTrue(model.needsAttention)
    }
}
