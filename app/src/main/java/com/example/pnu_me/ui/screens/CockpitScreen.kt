package com.example.pnu_me.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.cockpit.CockpitAction
import com.example.pnu_me.domain.cockpit.CockpitEstimates
import com.example.pnu_me.domain.cockpit.CockpitInteractionPolicy
import com.example.pnu_me.domain.cockpit.CockpitState
import com.example.pnu_me.domain.cockpit.Gear
import com.example.pnu_me.domain.cockpit.MockCatalog
import com.example.pnu_me.domain.cockpit.ServiceMode
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.ui.components.CockpitMapPanel
import com.example.pnu_me.ui.components.VehicleTopView
import com.example.pnu_me.ui.model.CockpitLayoutMode
import com.example.pnu_me.ui.model.cockpitLayoutMode
import com.example.pnu_me.ui.theme.CockpitBlack
import com.example.pnu_me.ui.theme.CockpitBlue
import com.example.pnu_me.ui.theme.CockpitWhite
import kotlin.math.roundToInt

internal enum class CockpitApp(val label: String, val glyph: String) {
    HOME("홈", "⌂"),
    VEHICLE("차량", "◇"),
    NAVIGATION("내비", "↗"),
    SECURITY("보안", "●"),
    SAFETY("안전", "!"),
    MORE("더보기", "•••"),
}

@Composable
internal fun CockpitScreen(
    state: CockpitState,
    notice: String?,
    assessment: RiskAssessment,
    isAutomotiveDevice: Boolean,
    onAction: (CockpitAction) -> Unit,
    onShowSuspectReport: () -> Unit,
    safetyContent: @Composable () -> Unit,
) {
    var selectedAppName by rememberSaveable { mutableStateOf(CockpitApp.HOME.name) }
    val selectedApp = CockpitApp.entries.firstOrNull { it.name == selectedAppName } ?: CockpitApp.HOME

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            val layoutMode = cockpitLayoutMode(
                widthDp = maxWidth.value.roundToInt(),
                heightDp = maxHeight.value.roundToInt(),
            )
            val dualPanel = layoutMode == CockpitLayoutMode.DUAL_PANEL

            Column(modifier = Modifier.fillMaxSize()) {
                StatusStrip(state = state, notice = notice, onAction = onAction)

                if (
                    CockpitInteractionPolicy.allowsComplexControls(
                        state = state,
                        isAutomotiveDevice = isAutomotiveDevice,
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = if (dualPanel) 16.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (dualPanel) {
                            VehiclePane(
                                state = state,
                                assessment = assessment,
                                onAction = onAction,
                                modifier = Modifier
                                    .weight(0.38f)
                                    .fillMaxHeight(),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(if (dualPanel) 0.62f else 1f)
                                .fillMaxHeight(),
                        ) {
                            when (selectedApp) {
                                CockpitApp.HOME -> {
                                    if (dualPanel) {
                                        CockpitMapPanel(
                                            assessment = assessment,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        SinglePanelHome(state = state, assessment = assessment)
                                    }
                                }

                                CockpitApp.VEHICLE -> VehiclePanel(
                                    state = state,
                                    onAction = onAction,
                                    showTopView = !dualPanel,
                                )
                                CockpitApp.NAVIGATION -> NavigationPanel(state, assessment, onAction)
                                CockpitApp.SECURITY -> SecurityPanel(
                                    state,
                                    onAction,
                                    onShowSuspectReport,
                                )
                                CockpitApp.SAFETY -> safetyContent()
                                CockpitApp.MORE -> MorePanel(state, onAction)
                            }
                        }
                    }

                    Dock(
                        selectedApp = selectedApp,
                        onSelectApp = { selectedAppName = it.name },
                    )
                } else {
                    DrivingRestrictionPanel(
                        isAutomotiveDevice = isAutomotiveDevice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusStrip(
    state: CockpitState,
    notice: String?,
    onAction: (CockpitAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Text(
                text = "MOCK · 실제 차량 미연결 · 안전 기능이 아닌 시연 화면",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.widthIn(min = 120.dp)) {
                Text(
                    text = "앗차!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "SAFETY DRIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Gear.entries.forEach { gear ->
                val selected = gear == state.gear
                Surface(
                    onClick = { onAction(CockpitAction.ShiftGear(gear)) },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "가상 기어 ${gear.label}" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) CockpitBlack else Color.Transparent,
                    contentColor = if (selected) CockpitWhite else MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = gear.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = "가상 기어",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${state.battery.percent}%  ·  ${
                    CockpitEstimates.rangeKm(state.battery.percent)
                } km",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (state.serviceMode != ServiceMode.NONE) {
                Text(
                    text = state.serviceMode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (notice != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = notice,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SinglePanelHome(
    state: CockpitState,
    assessment: RiskAssessment,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxHeight >= 700.dp) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VehicleTopView(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f),
                )
                CockpitMapPanel(
                    assessment = assessment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                )
            }
        } else {
            VehicleTopView(state = state, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DrivingRestrictionPanel(
    isAutomotiveDevice: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "주행 중 복잡한 조작 제한",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isAutomotiveDevice) {
                        "공식 CarUxRestrictions 연동 전에는 Automotive 기기에서 복잡한 조작을 제공하지 않습니다."
                    } else {
                        "가상 기어를 P로 바꾼 뒤 정차 상태에서 시연하세요."
                    },
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .widthIn(max = 640.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VehiclePane(
    state: CockpitState,
    assessment: RiskAssessment,
    onAction: (CockpitAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = state.body
    val destination = MockCatalog.destination(state.navigation.destinationId)

    Column(
        modifier = modifier.padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VehicleTopView(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickVehicleAction(
                label = "잠금",
                value = if (body.locked) "잠김" else "열림",
                active = !body.locked,
                onClick = { onAction(CockpitAction.SetLocked(!body.locked)) },
                modifier = Modifier.weight(1f),
            )
            QuickVehicleAction(
                label = "앞 적재함",
                value = if (body.frontStorageOpen) "열림" else "닫힘",
                active = body.frontStorageOpen,
                onClick = {
                    onAction(CockpitAction.SetFrontStorageOpen(!body.frontStorageOpen))
                },
                modifier = Modifier.weight(1f),
            )
            QuickVehicleAction(
                label = "트렁크",
                value = if (body.trunkOpen) "열림" else "닫힘",
                active = body.trunkOpen,
                onClick = { onAction(CockpitAction.SetTrunkOpen(!body.trunkOpen)) },
                modifier = Modifier.weight(1f),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = assessment.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (assessment.events.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (destination != null) {
                        "${destination.name} · ${destination.etaMinutes}분 · 가상 경로"
                    } else {
                        "목적지 없음 · 정차 상태"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickVehicleAction(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (active) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Dock(
    selectedApp: CockpitApp,
    onSelectApp: (CockpitApp) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        color = CockpitBlack,
        contentColor = CockpitWhite,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CockpitApp.entries.forEach { app ->
                val selected = app == selectedApp
                Surface(
                    onClick = { onSelectApp(app) },
                    modifier = Modifier
                        .heightIn(min = 58.dp)
                        .widthIn(min = 78.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) CockpitBlue else Color.White.copy(alpha = 0.06f),
                    contentColor = CockpitWhite,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = app.glyph,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }

        }
    }
}
