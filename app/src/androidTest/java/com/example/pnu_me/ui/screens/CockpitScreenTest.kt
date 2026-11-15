package com.example.pnu_me.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pnu_me.domain.cockpit.CockpitState
import com.example.pnu_me.domain.risk.DataQualityReport
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.domain.risk.RiskState
import com.example.pnu_me.domain.risk.ValidationIssue
import com.example.pnu_me.ui.theme.Pnu_meTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CockpitScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun brandTitle_usesAtchaName() {
        showCockpit()

        composeRule.onNodeWithText("앗차!", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("PNU ME", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun climateDestination_isNotOffered() {
        showCockpit()

        composeRule.onNodeWithText("공조", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun temperatureControls_areNotOffered() {
        showCockpit()

        composeRule
            .onNodeWithContentDescription("온도 내림", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription("온도 올림", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun mediaDestination_isNotOffered() {
        showCockpit()

        composeRule.onNodeWithText("미디어", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun mediaVolumeControls_areNotOffered() {
        showCockpit()

        composeRule
            .onNodeWithContentDescription("음량 내림", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription("음량 올림", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun chargingDestination_isNotOffered() {
        showCockpit()

        composeRule.onNodeWithText("충전", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun chargingPortControls_areNotOffered() {
        showCockpit()

        composeRule.onNodeWithText("충전구", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun retainedDestinations_areOffered() {
        showCockpit()

        listOf("차량", "내비", "보안", "안전").forEach { label ->
            composeRule.onNodeWithText(label, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun automotiveDevice_blocksComplexControlsUntilPolicyIsIntegrated() {
        showCockpit(isAutomotiveDevice = true)

        composeRule
            .onNodeWithText("Automotive 기기에서 복잡한 조작을 제공하지 않습니다.", substring = true)
            .assertExists()
        composeRule.onNodeWithText("차량", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun showCockpit(isAutomotiveDevice: Boolean = false) {
        composeRule.setContent {
            Pnu_meTheme {
                CockpitScreen(
                    state = CockpitState(),
                    notice = null,
                    assessment = normalAssessment,
                    isAutomotiveDevice = isAutomotiveDevice,
                    onAction = {},
                    onShowSuspectReport = {},
                    safetyContent = {},
                )
            }
        }
    }

    private companion object {
        val normalAssessment = RiskAssessment(
            state = RiskState.NORMAL,
            origin = null,
            title = "정상",
            guidance = "현재 상태를 유지하세요.",
            quality = DataQualityReport(
                isUsable = true,
                issue = ValidationIssue.NONE,
                message = "정상",
                validSampleCount = 0,
                totalSampleCount = 0,
            ),
            speedPoints = emptyList(),
            events = emptyList(),
            averageSpeedMps = null,
            maximumAbsoluteAccelerationMps2 = null,
        )
    }
}
