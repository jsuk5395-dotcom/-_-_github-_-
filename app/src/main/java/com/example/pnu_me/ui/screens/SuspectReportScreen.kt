package com.example.pnu_me.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.data.source.AndroidReporterLocationSource
import com.example.pnu_me.domain.hazard.HazardAlertFactory
import com.example.pnu_me.domain.hazard.HazardRelayClient
import com.example.pnu_me.domain.hazard.displayText
import com.example.pnu_me.domain.hazard.InMemoryHazardChannel
import com.example.pnu_me.domain.hazard.InMemoryPedestrianAlertChannel
import com.example.pnu_me.domain.hazard.PedestrianSafetyAlertFactory
import com.example.pnu_me.domain.report.DrivingState
import com.example.pnu_me.domain.report.LocationFetchResult
import com.example.pnu_me.domain.report.LocationStatus
import com.example.pnu_me.domain.report.PoliceReportDraft
import com.example.pnu_me.domain.report.PoliceReportFlow
import com.example.pnu_me.domain.report.ReportChannelLauncher
import com.example.pnu_me.domain.report.ReportFlowConfig
import com.example.pnu_me.domain.report.ReportFlowState
import com.example.pnu_me.domain.report.ReportReadinessProvider
import com.example.pnu_me.domain.report.ReportVoiceGuide
import com.example.pnu_me.domain.report.ReporterLocationSource
import com.example.pnu_me.domain.report.ReporterLocationValidator
import com.example.pnu_me.domain.report.SuspicionEvaluator
import com.example.pnu_me.domain.report.SuspicionLevel
import com.example.pnu_me.domain.report.SuspicionResult
import com.example.pnu_me.domain.report.VoiceGuideResult
import com.example.pnu_me.domain.report.WitnessDetails
import com.example.pnu_me.ui.model.MockCuePreset
import com.example.pnu_me.ui.model.NearbyPedestrianResult
import com.example.pnu_me.ui.model.NearbyResult
import com.example.pnu_me.ui.model.consentRemainingSeconds
import com.example.pnu_me.ui.model.draftLocationText
import com.example.pnu_me.ui.model.draftRetentionRemainingSeconds
import com.example.pnu_me.ui.model.handoffMessage
import com.example.pnu_me.ui.model.locationFetchMessage
import com.example.pnu_me.ui.model.runNearbyPedestrianSimulation
import com.example.pnu_me.ui.model.runNearbySimulation
import com.example.pnu_me.ui.model.suspicionLevelLabel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 이상 주행 차량 신고 시연 화면.
 * 실제 신고·음주 판정·CarUxRestrictions 연동을 주장하지 않는다.
 */
@Composable
internal fun SuspectReportScreen(
    flow: PoliceReportFlow,
    config: ReportFlowConfig,
    locationSource: ReporterLocationSource,
    readinessProvider: ReportReadinessProvider,
    channelLauncher: ReportChannelLauncher,
    voiceGuide: ReportVoiceGuide,
    drivingState: DrivingState,
    drivingStateSourceLabel: String,
    onBack: () -> Unit,
    /** 중계 서버 클라이언트. 중계를 끄면 null — 앱 내부 시연만 한다. */
    relayClient: HazardRelayClient? = null,
    /** 자기 알림을 수신 배너로 다시 보여 주지 않도록 발행 전에 호출한다. */
    onRelayPublishing: (String) -> Unit = {},
) {
    val evaluator = remember { SuspicionEvaluator() }
    var flowState by remember { mutableStateOf(flow.state) }
    var lastSuspicion by remember { mutableStateOf<SuspicionResult?>(null) }
    var suppressedByCooldown by remember { mutableStateOf(false) }
    var lastLocationResult by remember { mutableStateOf<LocationFetchResult?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var readiness by remember { mutableStateOf(readinessProvider.current()) }
    var handoffStatus by remember { mutableStateOf<String?>(null) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val hazardChannel = remember { InMemoryHazardChannel() }
    var nearbyResults by remember { mutableStateOf<List<NearbyResult>?>(null) }
    val pedestrianChannel = remember { InMemoryPedestrianAlertChannel() }
    var pedestrianResults by remember { mutableStateOf<List<NearbyPedestrianResult>?>(null) }
    var vehicleRelayStatus by remember { mutableStateOf<String?>(null) }
    var pedestrianRelayStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        readiness = readinessProvider.current()
    }

    DisposableEffect(locationSource, voiceGuide) {
        onDispose {
            locationSource.cancel()
            voiceGuide.stop()
        }
    }

    LaunchedEffect(flowState) {
        while (
            flow.state is ReportFlowState.AwaitingConsent ||
            flow.state is ReportFlowState.DeferredUntilParked ||
            flow.state is ReportFlowState.DraftReady
        ) {
            delay(250L)
            nowMillis = System.currentTimeMillis()
            flowState = flow.tick(nowMillis)
        }
    }

    LaunchedEffect(drivingState) {
        if (drivingState == DrivingState.PARKED) {
            nowMillis = System.currentTimeMillis()
            flowState = flow.onParked(nowMillis)
        }
    }

    fun closeFlow() {
        locationSource.cancel()
        voiceGuide.stop()
        flowState = flow.finish(System.currentTimeMillis())
        lastLocationResult = null
        nearbyResults = null
        pedestrianResults = null
        vehicleRelayStatus = null
        pedestrianRelayStatus = null
        handoffStatus = null
        voiceStatus = null
    }

    fun approve() {
        val deferUntilParked = drivingState != DrivingState.PARKED
        flowState = flow.onOccupantApproved(
            nowMillis = System.currentTimeMillis(),
            deferUntilParked = deferUntilParked,
        )
        if (flowState is ReportFlowState.Locating) {
            locationSource.requestCurrentLocation { result ->
                lastLocationResult = result
                flowState = flow.onLocationResult(result, System.currentTimeMillis())
            }
        }
    }

    fun leave() {
        locationSource.cancel()
        voiceGuide.stop()
        if (flow.state !is ReportFlowState.DeferredUntilParked) {
            closeFlow()
        }
        onBack()
    }

    BackHandler(onBack = ::leave)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TextButton(onClick = ::leave) {
                Text("← 대시보드로 돌아가기")
            }
            Text(
                text = "이상 주행 차량 112 신고 보조 · MOCK",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            ReportSafetyNotice()
            ReportReadinessCard(
                readiness = readiness,
                locationSourceLabel = locationSource.sourceLabel,
                drivingStateLabel = drivingStateSourceLabel,
                onRequestLocationPermission = {
                    permissionLauncher.launch(AndroidReporterLocationSource.REQUIRED_PERMISSIONS)
                },
                onRefresh = {
                    readiness = readinessProvider.current()
                },
            )

            when (val state = flowState) {
                ReportFlowState.Idle -> PresetPanel(
                    lastSuspicion = lastSuspicion,
                    suppressedByCooldown = suppressedByCooldown,
                    onSelect = { preset ->
                        val now = System.currentTimeMillis()
                        val result = evaluator.evaluate(preset.observations(now), now)
                        lastSuspicion = result
                        flowState = flow.onSuspicion(result, drivingState, now)
                        suppressedByCooldown = result.shouldAskOccupant &&
                            flowState is ReportFlowState.Idle
                    },
                )

                is ReportFlowState.AwaitingConsent -> ConsentCard(
                    state = state,
                    remainingSeconds = consentRemainingSeconds(
                        state.askedAtMillis,
                        nowMillis,
                        config.consentTimeoutMillis,
                    ),
                    onApprove = ::approve,
                    onDecline = {
                        flowState = flow.onOccupantDeclined(System.currentTimeMillis())
                    },
                )

                is ReportFlowState.Locating -> MessageCard(
                    title = "내 차량 위치 확인 중…",
                    body = "의심 차량이 아닌 신고자 위치를 한 번만 조회합니다. 최대 10초.",
                    actionLabel = "신고 취소",
                    onAction = ::closeFlow,
                )

                is ReportFlowState.DeferredUntilParked -> MessageCard(
                    title = "목격 시각과 위치를 임시 보관했습니다",
                    body = "MOCK 주행 상태에서는 상세 입력과 신고 채널을 열지 않습니다. " +
                        "P로 전환한 뒤 다시 들어오세요. " +
                        "자동 삭제까지 ${draftRetentionRemainingSeconds(state.discardAtMillis, nowMillis)}초.",
                    actionLabel = "임시 정보 즉시 삭제",
                    onAction = ::closeFlow,
                )

                is ReportFlowState.DraftReady -> {
                    var details by remember(state.draft.createdAtMillis) {
                        mutableStateOf(
                            state.draft.witnessDetails ?: WitnessDetails(
                                observedAtMillis = state.draft.createdAtMillis,
                            ),
                        )
                    }

                    Text(
                        text = "자동 삭제까지 " +
                            "${draftRetentionRemainingSeconds(state.discardAtMillis, nowMillis)}초",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    WitnessDetailsPanel(
                        details = details,
                        onDetailsChange = { details = it },
                        onSave = {
                            flowState = flow.updateWitnessDetails(details)
                        },
                    )
                    DraftCard(
                        draft = state.draft,
                        locationMessage = locationFetchMessage(lastLocationResult),
                    )
                    ReportActionPanel(
                        readiness = readiness,
                        isDemo = state.draft.isDemo,
                        handoffStatus = handoffStatus,
                        voiceStatus = voiceStatus,
                        onOpenChannel = { channel ->
                            val result = channelLauncher.open(channel, state.draft.toSmsBody())
                            handoffStatus = handoffMessage(result)
                        },
                        onSpeak = {
                            voiceStatus = when (val result = voiceGuide.speak(state.draft.toSafeVoiceSummary())) {
                                VoiceGuideResult.Started -> "정차 전용 안전 요약을 읽고 있습니다."
                                VoiceGuideResult.NotReady -> "음성 엔진을 준비 중입니다. 잠시 후 다시 시도하세요."
                                is VoiceGuideResult.Unavailable -> result.reason
                            }
                            readiness = readinessProvider.current()
                        },
                        onDelete = ::closeFlow,
                    )
                    HazardBroadcastPanel(
                        canBroadcast = state.draft.hasUsableLocation &&
                            state.draft.suspicion.level == SuspicionLevel.STRONG &&
                            ReporterLocationValidator.validate(state.draft.location, nowMillis) == LocationStatus.VALID &&
                            (state.draft.location?.accuracyMeters ?: Double.POSITIVE_INFINITY) <=
                            HazardAlertFactory.MAX_SOURCE_ACCURACY_METERS,
                        channelLabel = hazardChannel.sourceLabel,
                        results = nearbyResults,
                        headingAvailable = state.draft.location?.headingDegrees != null,
                        onBroadcast = {
                            val now = System.currentTimeMillis()
                            HazardAlertFactory.fromDraft(
                                draft = state.draft,
                                alertId = UUID.randomUUID().toString(),
                                nowMillis = now,
                            )?.let { alert ->
                                nearbyResults = runNearbySimulation(alert, hazardChannel, now)
                                relayClient?.let { client ->
                                    onRelayPublishing(alert.alertId)
                                    vehicleRelayStatus = "중계 서버로 전송 중…"
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { client.publishVehicle(alert) }
                                        vehicleRelayStatus = "실제 중계: " + result.displayText()
                                    }
                                }
                            }
                        },
                        relayStatus = vehicleRelayStatus,
                    )
                    PedestrianAlertPanel(
                        canPublish = state.draft.hasUsableLocation &&
                            state.draft.suspicion.level == SuspicionLevel.STRONG,
                        channelLabel = pedestrianChannel.sourceLabel,
                        results = pedestrianResults,
                        onPublish = {
                            val now = System.currentTimeMillis()
                            PedestrianSafetyAlertFactory.fromDraft(
                                draft = state.draft,
                                alertId = UUID.randomUUID().toString(),
                                nowMillis = now,
                            )?.let { alert ->
                                pedestrianResults = runNearbyPedestrianSimulation(
                                    alert = alert,
                                    channel = pedestrianChannel,
                                    nowMillis = now,
                                )
                                relayClient?.let { client ->
                                    onRelayPublishing(alert.alertId)
                                    pedestrianRelayStatus = "중계 서버로 전송 중…"
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { client.publishPedestrian(alert) }
                                        pedestrianRelayStatus = "실제 중계: " + result.displayText()
                                    }
                                }
                            }
                        },
                        relayStatus = pedestrianRelayStatus,
                    )
                }

                is ReportFlowState.DraftDiscarded -> MessageCard(
                    title = "신고 초안을 자동 삭제했습니다",
                    body = "개인정보 보호를 위해 위치와 목격 정보를 메모리에서 폐기했습니다.",
                    actionLabel = "확인",
                    onAction = ::closeFlow,
                )

                is ReportFlowState.Declined -> MessageCard(
                    title = "신고하지 않았습니다",
                    body = "5분 동안 같은 질문을 다시 띄우지 않습니다.",
                    actionLabel = "확인",
                    onAction = ::closeFlow,
                )

                is ReportFlowState.Expired -> MessageCard(
                    title = "응답이 없어 신고하지 않았습니다",
                    body = "15초 안에 응답이 없으면 자동으로 닫히며 신고되지 않습니다.",
                    actionLabel = "확인",
                    onAction = ::closeFlow,
                )
            }
        }
    }
}

@Composable
private fun ReportSafetyNotice() {
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
                text = "정차 시연 우선 · 운전자는 조작하지 마세요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "관찰 단서는 음주 판정이 아닙니다. 의심 차량을 추격하지 마세요. " +
                    "앱은 신고자 위치만 사용하며 자동 신고하지 않습니다. 허위 신고는 처벌받을 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PresetPanel(
    lastSuspicion: SuspicionResult?,
    suppressedByCooldown: Boolean,
    onSelect: (MockCuePreset) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("모의 관찰 단서 선택", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "주변 차량 자동 인식은 확인된 공식 API가 없어 시연용 단서로만 재현합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MockCuePreset.entries.forEach { preset ->
                OutlinedButton(onClick = { onSelect(preset) }, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(preset.displayName, fontWeight = FontWeight.Bold)
                        Text(preset.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            lastSuspicion?.let {
                Text(
                    "최근 결과: ${suspicionLevelLabel(it.level)} · ${it.reason}",
                    fontWeight = FontWeight.Bold,
                )
            }
            if (suppressedByCooldown) {
                Text("최근 질문 종료 후 5분 동안 다시 묻지 않습니다.")
            }
        }
    }
}

@Composable
private fun ConsentCard(
    state: ReportFlowState.AwaitingConsent,
    remainingSeconds: Int,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "이상 주행 차량 신고 초안을 준비할까요?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                "동승자가 응답하세요 · ${remainingSeconds}초 후 신고 없이 닫힘",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!state.restrictedUi) {
                Text(state.suspicion.distinctCues.joinToString(", ") { it.label })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(88.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("예", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f).height(88.dp),
                ) {
                    Text("아니오", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: PoliceReportDraft,
    locationMessage: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (draft.isDemo) "신고 초안 (시연용 · 실제 신고 아님)" else "신고 초안",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text("제보자 위치: " + draftLocationText(draft), style = MaterialTheme.typography.titleMedium)
            locationMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    draft.toSmsBody(),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(actionLabel)
            }
        }
    }
}
