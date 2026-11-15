package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.risk.RiskState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitMapUiModelTest {
    @Test
    fun normalState_keepsTheMapExplicitlyMock() {
        val model = cockpitMapUiModel(RiskState.NORMAL)

        assertTrue("주행 환경" in model.title)
        assertTrue("MOCK" in model.sourceNotice)
        assertTrue("실제 지도 아님" in model.sourceNotice)
        assertFalse("실시간" in model.sourceNotice)
        assertFalse(model.needsAttention)
    }

    @Test
    fun stopRecommendedState_surfacesTheSafeStopMessage() {
        val model = cockpitMapUiModel(RiskState.STOP_RECOMMENDED)

        assertTrue("안전 정차" in model.title)
        assertTrue(model.needsAttention)
    }

    @Test
    fun unavailableState_doesNotPresentAWorkingRoute() {
        val model = cockpitMapUiModel(RiskState.UNAVAILABLE)

        assertTrue("확인 불가" in model.title)
        assertTrue("경로 없음" in model.status)
        assertTrue("실제 지도 아님" in model.sourceNotice)
        assertTrue(model.needsAttention)
    }
}
