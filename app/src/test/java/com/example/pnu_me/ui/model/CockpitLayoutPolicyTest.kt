package com.example.pnu_me.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CockpitLayoutPolicyTest {
    @Test
    fun portrait800Width_usesSinglePanel() {
        assertEquals(
            CockpitLayoutMode.SINGLE_PANEL,
            cockpitLayoutMode(widthDp = 800, heightDp = 1280),
        )
    }

    @Test
    fun landscapeExpandedWindow_usesDualPanel() {
        assertEquals(
            CockpitLayoutMode.DUAL_PANEL,
            cockpitLayoutMode(widthDp = 1280, heightDp = 720),
        )
    }

    @Test
    fun exactDualPanelBoundary_isIncluded() {
        assertEquals(
            CockpitLayoutMode.DUAL_PANEL,
            cockpitLayoutMode(widthDp = 1000, heightDp = 600),
        )
    }

    @Test
    fun shortOrInvalidWindow_usesSinglePanel() {
        assertEquals(
            CockpitLayoutMode.SINGLE_PANEL,
            cockpitLayoutMode(widthDp = 1600, heightDp = 480),
        )
        assertEquals(
            CockpitLayoutMode.SINGLE_PANEL,
            cockpitLayoutMode(widthDp = -1, heightDp = 720),
        )
    }
}
