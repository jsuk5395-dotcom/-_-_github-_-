package com.example.pnu_me.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample
import com.example.pnu_me.data.source.AndroidReportChannelLauncher
import com.example.pnu_me.data.source.AndroidReporterLocationSource
import com.example.pnu_me.data.source.AndroidReportReadinessProvider
import com.example.pnu_me.data.source.AndroidReportVoiceGuide
import com.example.pnu_me.data.source.AssetScenarioReader
import com.example.pnu_me.data.source.DrivingScenario
import com.example.pnu_me.data.source.MockStopAdvisor
import com.example.pnu_me.data.source.MockTelemetrySource
import com.example.pnu_me.domain.cockpit.CockpitAction
import com.example.pnu_me.domain.cockpit.CockpitReducer
import com.example.pnu_me.domain.cockpit.CockpitState
import com.example.pnu_me.domain.cockpit.CockpitStatePersistence
import com.example.pnu_me.domain.cockpit.DisplayTheme
import com.example.pnu_me.domain.report.DrivingState
import com.example.pnu_me.domain.report.PoliceReportFlow
import com.example.pnu_me.domain.report.ReportFlowConfig
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.domain.risk.RiskState
import com.example.pnu_me.domain.risk.RuleBasedRiskEngine
import com.example.pnu_me.domain.units.SpeedUnits
import com.example.pnu_me.platform.vehicle.VehicleTelemetryHub
import com.example.pnu_me.data.source.HttpUrlRelayTransport
import com.example.pnu_me.domain.hazard.HazardRelayClient
import com.example.pnu_me.domain.hazard.RelayReceiverSession
import com.example.pnu_me.domain.hazard.RelayRole
import com.example.pnu_me.ui.components.CockpitMapPanel
import com.example.pnu_me.ui.components.RelayReceiverHost
import com.example.pnu_me.ui.components.DrivingSceneCard
import com.example.pnu_me.ui.components.TelemetryChart
import com.example.pnu_me.ui.model.LIVE_VEHICLE_LABEL
import com.example.pnu_me.ui.model.analyzeLiveVehicle
import com.example.pnu_me.ui.model.analyzeScenario
import com.example.pnu_me.ui.screens.CockpitScreen
import com.example.pnu_me.ui.screens.StopSupportScreen
import com.example.pnu_me.ui.screens.SuspectReportScreen
import com.example.pnu_me.ui.theme.Pnu_meTheme
import java.util.Locale
import kotlinx.coroutines.delay

private val CockpitStateSaver = Saver<CockpitState, Any>(
    save = { CockpitStatePersistence.encode(it) },
    restore = { CockpitStatePersistence.decode(it) },
)

@Composable
fun PnuMeApp() {
    val context = LocalContext.current
    val isAutomotiveDevice = remember(context.applicationContext) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }
    val source = remember(context.applicationContext) {
        MockTelemetrySource(AssetScenarioReader(context.applicationContext))
    }
    val engine = remember { RuleBasedRiskEngine() }
    val stopAdvisor = remember { MockStopAdvisor() }
    val reportDemoMode = true
    val reportFlowConfig = remember { ReportFlowConfig() }
    val reportFlow = remember { PoliceReportFlow(config = reportFlowConfig, isDemo = reportDemoMode) }
    val reportLocationSource = remember(context.applicationContext) {
        AndroidReporterLocationSource(context.applicationContext)
    }
    val reportVoiceGuide = remember(context.applicationContext) {
        AndroidReportVoiceGuide(context.applicationContext)
    }
    val reportReadinessProvider = remember(context.applicationContext, reportVoiceGuide) {
        AndroidReportReadinessProvider(
            context = context.applicationContext,
            isDemo = reportDemoMode,
            voiceGuidanceAvailable = { reportVoiceGuide.isReady },
        )
    }
    val reportChannelLauncher = remember(context.applicationContext) {
        AndroidReportChannelLauncher(context.applicationContext)
    }

    DisposableEffect(reportVoiceGuide) {
        onDispose { reportVoiceGuide.release() }
    }

    // 주변 알림 중계 서버(backend/hazard-relay). 기본값 OFF — 켜기 전에는 네트워크를 쓰지 않는다.
    val relayClient = remember { HazardRelayClient(HttpUrlRelayTransport()) }
    val relaySession = remember(relayClient) {
        RelayReceiverSession(client = relayClient, isDemo = reportDemoMode)
    }
    val relayLocationSource = remember(context.applicationContext) {
        AndroidReporterLocationSource(context.applicationContext)
    }
    var relayRoleName by rememberSaveable { mutableStateOf(RelayRole.OFF.name) }
    val relayRole = RelayRole.valueOf(relayRoleName)

    var selectedScenarioId by rememberSaveable {
        mutableStateOf(DrivingScenario.NORMAL.id)
    }
    var analyzedScenarioId by rememberSaveable {
        mutableStateOf(DrivingScenario.NORMAL.id)
    }
    // 실차(VEHICLE) 데이터 선택 여부. MOCK 시나리오와 섞어 분석하지 않는다.
    var selectedLive by rememberSaveable { mutableStateOf(false) }
    var analyzedLive by rememberSaveable { mutableStateOf(false) }
    val liveSamples by VehicleTelemetryHub.samples.collectAsState()
    val liveConnected by VehicleTelemetryHub.connected.collectAsState()
    LaunchedEffect(liveConnected) {
        while (liveConnected) {
            VehicleTelemetryHub.refresh()
            delay(1_000L)
        }
    }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    var showStopSupport by rememberSaveable { mutableStateOf(false) }
    var showSuspectReport by rememberSaveable { mutableStateOf(false) }
    var cockpit by key(CockpitStatePersistence.SAVE_KEY) {
        rememberSaveable(stateSaver = CockpitStateSaver) {
            mutableStateOf(CockpitState())
        }
    }
    var cockpitNotice by remember { mutableStateOf<String?>(null) }
    val onCockpitAction: (CockpitAction) -> Unit = { action ->
        val outcome = CockpitReducer.reduce(cockpit, action)
        cockpit = outcome.state
        cockpitNotice = outcome.rejection
    }

    val selectedScenario = DrivingScenario.fromId(selectedScenarioId)
    val analyzedScenario = DrivingScenario.fromId(analyzedScenarioId)
    val analysisResult = if (analyzedLive) {
        // 최근 60초 실차 표본이 바뀔 때마다(최대 0.5초 간격) 같은 엔진으로 다시 분석
        remember(liveSamples, liveConnected, engine) {
            analyzeLiveVehicle(
                samples = liveSamples,
                connected = liveConnected,
                engine = engine,
            )
        }
    } else {
        remember(analyzedScenarioId, source, engine) {
            analyzeScenario(
                source = source,
                engine = engine,
                scenario = analyzedScenario,
            )
        }
    }
    val assessment = analysisResult.assessment
    val analyzedLabel = if (analyzedLive) LIVE_VEHICLE_LABEL else analyzedScenario.displayName

    BackHandler(enabled = showSummary) {
        showSummary = false
    }

    val darkTheme = when (cockpit.drive.displayTheme) {
        DisplayTheme.AUTO -> isSystemInDarkTheme()
        DisplayTheme.LIGHT -> false
        DisplayTheme.DARK -> true
    }

    Pnu_meTheme(darkTheme = darkTheme) {
    RelayReceiverHost(
        role = relayRole,
        canChangeRole = cockpit.isParked,
        onRoleChange = { relayRoleName = it.name },
        session = relaySession,
        locationSource = relayLocationSource,
    ) {
    if (showSummary) {
        SummaryScreen(
            dataLabel = analyzedLabel,
            assessment = assessment,
            onBack = { showSummary = false },
        )
    } else if (showSuspectReport) {
        SuspectReportScreen(
            flow = reportFlow,
            config = reportFlowConfig,
            locationSource = reportLocationSource,
            readinessProvider = reportReadinessProvider,
            channelLauncher = reportChannelLauncher,
            voiceGuide = reportVoiceGuide,
            drivingState = if (cockpit.isParked) DrivingState.PARKED else DrivingState.DRIVING,
            drivingStateSourceLabel = "MOCK 콕핏 기어 ${cockpit.gear.label}",
            onBack = { showSuspectReport = false },
            relayClient = relayClient.takeIf { relayRole != RelayRole.OFF },
            onRelayPublishing = relaySession::markOwn,
        )
    } else if (showStopSupport) {
        StopSupportScreen(
            advisor = stopAdvisor,
            assessment = assessment,
            onBack = { showStopSupport = false },
            onShowSummary = {
                showStopSupport = false
                showSummary = true
            },
        )
    } else {
        CockpitScreen(
            state = cockpit,
            notice = cockpitNotice,
            assessment = assessment,
            isAutomotiveDevice = isAutomotiveDevice,
            onAction = onCockpitAction,
            onShowSuspectReport = { showSuspectReport = true },
        ) {
            DashboardScreen(
                selectedScenario = selectedScenario,
                selectedLive = selectedLive,
                analyzedLabel = analyzedLabel,
                assessment = assessment,
                sourceName = analysisResult.sourceBadgeText,
                onSelectScenario = {
                    selectedScenarioId = it.id
                    selectedLive = false
                },
                onSelectLive = { selectedLive = true },
                onAnalyze = {
                    analyzedLive = selectedLive
                    analyzedScenarioId = selectedScenarioId
                    showSummary = false
                    showStopSupport = false
                },
                onShowStopSupport = { showStopSupport = true },
                onShowSuspectReport = { showSuspectReport = true },
            )
        }
    }
    }
    }
}

@Composable
private fun DashboardScreen(
    selectedScenario: DrivingScenario,
    selectedLive: Boolean,
    analyzedLabel: String,
    assessment: RiskAssessment,
    sourceName: String,
    onSelectScenario: (DrivingScenario) -> Unit,
    onSelectLive: () -> Unit,
    onAnalyze: () -> Unit,
    onShowStopSupport: () -> Unit,
    onShowSuspectReport: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppHeader(sourceName = sourceName)

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 1000.dp
                if (isWide) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(0.38f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                DrivingSceneCard(assessment = assessment)
                                RiskStateCard(
                                    dataLabel = analyzedLabel,
                                    assessment = assessment,
                                )
                            }
                            CockpitMapPanel(
                                assessment = assessment,
                                modifier = Modifier
                                    .weight(0.62f)
                                    .fillMaxSize(),
                            )
                        }

                        SafetyNotice()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            ScenarioPanel(
                                selectedScenario = selectedScenario,
                                selectedLive = selectedLive,
                                onSelectScenario = onSelectScenario,
                                onSelectLive = onSelectLive,
                                onAnalyze = onAnalyze,
                                modifier = Modifier.weight(0.38f),
                            )
                            ResultPanel(
                                analyzedLabel = analyzedLabel,
                                assessment = assessment,
                                onShowStopSupport = onShowStopSupport,
                                showStateCard = false,
                                modifier = Modifier.weight(0.62f),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DrivingSceneCard(assessment = assessment)
                        CockpitMapPanel(
                            assessment = assessment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                        )
                        SafetyNotice()
                        ScenarioPanel(
                            selectedScenario = selectedScenario,
                            selectedLive = selectedLive,
                            onSelectScenario = onSelectScenario,
                            onSelectLive = onSelectLive,
                            onAnalyze = onAnalyze,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ResultPanel(
                            analyzedLabel = analyzedLabel,
                            assessment = assessment,
                            onShowStopSupport = onShowStopSupport,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onShowSuspectReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = "음주운전 의심 차량 신고 시연 · MOCK",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                text = "이 앱은 시뮬레이션 기반 연구용 PoC이며 실제 차량 안전 기능이 아닙니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppHeader(sourceName: String) {
    val titleContent: @Composable () -> Unit = {
        Column {
            Text(
                text = "앗차!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "비정상 주행 패턴 연구용 대시보드",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                titleContent()
                SourceBadge(sourceName)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                titleContent()
                SourceBadge(sourceName)
            }
        }
    }
}

@Composable
private fun SourceBadge(sourceName: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = sourceName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SafetyNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "정차 시연 전용",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "차량 주행 중에는 시나리오를 조작하지 마세요. " +
                    "이 분석은 음주 여부·사고 가능성·운전 가능 여부를 판정하지 않습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ScenarioPanel(
    selectedScenario: DrivingScenario,
    selectedLive: Boolean,
    onSelectScenario: (DrivingScenario) -> Unit,
    onSelectLive: () -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "1. 데이터 선택",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "모의 시나리오는 로컬 CSV이며 같은 입력은 같은 결과를 만듭니다. " +
                    "실차 데이터는 MOCK과 섞지 않고 같은 규칙으로 분석합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))

            // 차량 데이터: Android Car API 속도 (DrivingMonitorService)
            OutlinedButton(
                onClick = onSelectLive,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selectedLive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = LIVE_VEHICLE_LABEL + " · VEHICLE",
                        fontWeight = if (selectedLive) FontWeight.Bold else FontWeight.Medium,
                    )
                    Text(
                        text = "차량 속도를 최근 60초 단위로 자동 분석 (Android Automotive)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            DrivingScenario.entries.forEach { scenario ->
                val selected = !selectedLive && scenario == selectedScenario
                OutlinedButton(
                    onClick = { onSelectScenario(scenario) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = scenario.displayName,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            text = scenario.description,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Button(
                onClick = onAnalyze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = if (selectedLive) "실차 데이터 분석 시작" else "선택한 시나리오 분석",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun ResultPanel(
    analyzedLabel: String,
    assessment: RiskAssessment,
    onShowStopSupport: () -> Unit,
    modifier: Modifier = Modifier,
    showStateCard: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showStateCard) {
            RiskStateCard(
                dataLabel = analyzedLabel,
                assessment = assessment,
            )
        }
        TelemetryChart(
            points = assessment.speedPoints,
            events = assessment.events,
        )
        MetricsCard(assessment)

        if (assessment.state == RiskState.STOP_RECOMMENDED) {
            Button(
                onClick = onShowStopSupport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(
                    text = "시연용 안전 정차 지원 보기",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RiskStateCard(
    dataLabel: String,
    assessment: RiskAssessment,
) {
    val stateColor = stateColor(assessment.state)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = stateColor.copy(alpha = 0.14f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "2. 분석 결과 · " + dataLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = assessment.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = stateColor,
            )
            Text(
                text = assessment.guidance,
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "데이터 품질: " + assessment.quality.message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MetricsCard(assessment: RiskAssessment) {
    val metrics = listOf(
        "표본" to assessment.quality.totalSampleCount.toString() + "개",
        "이벤트" to assessment.events.size.toString() + "회",
        "평균 속도" to (
            assessment.averageSpeedMps?.let {
                formatNumber(SpeedUnits.metersPerSecondToKilometersPerHour(it)) + " km/h"
            } ?: "-"
        ),
        "최대 |가속도|" to (
            assessment.maximumAbsoluteAccelerationMps2?.let {
                formatNumber(it) + " m/s²"
            } ?: "-"
        ),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            if (maxWidth >= 600.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    metrics.forEach { (label, value) ->
                        Metric(label, value, Modifier.weight(1f))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    metrics.chunked(2).forEach { rowMetrics ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowMetrics.forEach { (label, value) ->
                                Metric(label, value, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryScreen(
    dataLabel: String,
    assessment: RiskAssessment,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("← 대시보드로 돌아가기")
            }
            Text(
                text = "정차 후 시연 요약",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            SourceBadge(
                sourceName = when (assessment.origin) {
                    TelemetryOrigin.VEHICLE -> "VEHICLE · 실차 데이터 · 연구용 규칙 분석"
                    TelemetryOrigin.MOCK -> "MOCK · 시뮬레이션 결과"
                    null -> "출처 확인 불가 · 시뮬레이션 결과"
                },
            )
            Text(
                text = "안전한 장소에 완전히 정차한 뒤 확인하는 화면입니다.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = stateColor(assessment.state).copy(alpha = 0.14f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryRow("데이터", dataLabel)
                    SummaryRow(
                        "데이터 출처",
                        assessment.origin?.name ?: "출처 확인 불가",
                    )
                    SummaryRow("분석 상태", assessment.title)
                    SummaryRow("감지 이벤트", assessment.events.size.toString() + "회")
                    SummaryRow(
                        "최대 |가속도|",
                        assessment.maximumAbsoluteAccelerationMps2?.let {
                            formatNumber(it) + " m/s²"
                        } ?: "-",
                    )
                    HorizontalDivider()
                    Text(
                        text = assessment.guidance,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = "이 요약은 운전 가능 여부를 인증하지 않습니다. " +
                        "실제 이상이나 불편이 있으면 운전을 재개하지 말고 적절한 도움을 요청하세요. " +
                        "내비게이션·자동 신고·차량 제어는 연결되어 있지 않습니다.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun stateColor(state: RiskState): Color = when (state) {
    RiskState.NORMAL -> MaterialTheme.colorScheme.primary
    RiskState.REVIEW -> MaterialTheme.colorScheme.tertiary
    RiskState.STOP_RECOMMENDED -> MaterialTheme.colorScheme.error
    RiskState.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatNumber(value: Double): String =
    String.format(Locale.KOREA, "%.1f", value)

private fun previewAssessment(): RiskAssessment {
    val samples = listOf(
        TelemetrySample(0, 10.0, origin = TelemetryOrigin.MOCK),
        TelemetrySample(2_000, 16.0, origin = TelemetryOrigin.MOCK),
        TelemetrySample(4_000, 9.0, origin = TelemetryOrigin.MOCK),
        TelemetrySample(6_000, 16.5, origin = TelemetryOrigin.MOCK),
        TelemetrySample(8_000, 9.0, origin = TelemetryOrigin.MOCK),
    )
    return RuleBasedRiskEngine().analyze(samples, TelemetryOrigin.MOCK)
}

@Preview(
    name = "2560x1440 AAOS",
    widthDp = 1280,
    heightDp = 720,
    showBackground = true,
)
@Preview(
    name = "2560x1440 AAOS dark",
    widthDp = 1280,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun DashboardLandscapePreview() {
    Pnu_meTheme {
        DashboardScreen(
            selectedScenario = DrivingScenario.REPEATED_CHANGES,
            selectedLive = false,
            analyzedLabel = DrivingScenario.REPEATED_CHANGES.displayName,
            assessment = previewAssessment(),
            sourceName = "데이터 출처 · MOCK · 로컬 시나리오",
            onSelectScenario = {},
            onSelectLive = {},
            onAnalyze = {},
            onShowStopSupport = {},
            onShowSuspectReport = {},
        )
    }
}

@Preview(
    name = "800x1280 narrow",
    widthDp = 800,
    heightDp = 1280,
    showBackground = true,
)
@Composable
private fun DashboardPhonePreview() {
    Pnu_meTheme {
        DashboardScreen(
            selectedScenario = DrivingScenario.NORMAL,
            selectedLive = false,
            analyzedLabel = DrivingScenario.REPEATED_CHANGES.displayName,
            assessment = previewAssessment(),
            sourceName = "데이터 출처 · MOCK · 로컬 시나리오",
            onSelectScenario = {},
            onSelectLive = {},
            onAnalyze = {},
            onShowStopSupport = {},
            onShowSuspectReport = {},
        )
    }
}
