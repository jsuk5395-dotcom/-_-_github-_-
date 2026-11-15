package com.example.pnu_me.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.cockpit.CockpitAction
import com.example.pnu_me.domain.cockpit.CockpitEstimates
import com.example.pnu_me.domain.cockpit.CockpitReducer
import com.example.pnu_me.domain.cockpit.CockpitState
import com.example.pnu_me.domain.cockpit.CollisionWarningTiming
import com.example.pnu_me.domain.cockpit.DisplayTheme
import com.example.pnu_me.domain.cockpit.DriveFeel
import com.example.pnu_me.domain.cockpit.LightMode
import com.example.pnu_me.domain.cockpit.MockCatalog
import com.example.pnu_me.domain.cockpit.ServiceMode
import com.example.pnu_me.domain.cockpit.SteeringFeel
import com.example.pnu_me.domain.cockpit.StopMode
import com.example.pnu_me.domain.cockpit.TripId
import com.example.pnu_me.domain.cockpit.WiperMode
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.ui.components.ChoiceRow
import com.example.pnu_me.ui.components.CockpitMapPanel
import com.example.pnu_me.ui.components.MockNote
import com.example.pnu_me.ui.components.PanelSection
import com.example.pnu_me.ui.components.StepperRow
import com.example.pnu_me.ui.components.SwitchRow
import com.example.pnu_me.ui.components.TileGrid
import com.example.pnu_me.ui.components.TileSpec
import com.example.pnu_me.ui.components.VehicleTopView
import java.util.Locale

@Composable
private fun PanelColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

private fun onOff(on: Boolean): String = if (on) "켜짐" else "꺼짐"

private fun openClosed(open: Boolean): String = if (open) "열림" else "닫힘"

private fun formatKm(km: Double): String = String.format(Locale.KOREA, "%.1f km", km)

@Composable
internal fun VehiclePanel(
    state: CockpitState,
    onAction: (CockpitAction) -> Unit,
    showTopView: Boolean,
) {
    val body = state.body
    PanelColumn {
        if (showTopView) {
            VehicleTopView(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )
        }

        PanelSection("빠른 제어") {
            TileGrid(
                columns = 4,
                tiles = listOf(
                    TileSpec("도어", if (body.locked) "잠김" else "잠금 해제", body.locked) {
                        onAction(CockpitAction.SetLocked(!body.locked))
                    },
                    TileSpec("미러", if (body.mirrorsFolded) "접힘" else "펼침", body.mirrorsFolded) {
                        onAction(CockpitAction.SetMirrorsFolded(!body.mirrorsFolded))
                    },
                    TileSpec("창문 환기", onOff(body.windowsVented), body.windowsVented) {
                        onAction(CockpitAction.SetWindowsVented(!body.windowsVented))
                    },
                    TileSpec("어린이 보호 잠금", onOff(body.childLock), body.childLock) {
                        onAction(CockpitAction.SetChildLock(!body.childLock))
                    },
                    TileSpec("앞 적재함", openClosed(body.frontStorageOpen), body.frontStorageOpen) {
                        onAction(CockpitAction.SetFrontStorageOpen(!body.frontStorageOpen))
                    },
                    TileSpec("트렁크", openClosed(body.trunkOpen), body.trunkOpen) {
                        onAction(CockpitAction.SetTrunkOpen(!body.trunkOpen))
                    },
                    TileSpec("글러브박스", openClosed(body.gloveboxOpen), body.gloveboxOpen) {
                        onAction(CockpitAction.SetGloveboxOpen(!body.gloveboxOpen))
                    },
                ),
            )
            MockNote("모든 조작은 화면 속 MOCK 상태만 바꾸며 실제 차량을 제어하지 않습니다.")
        }

        PanelSection("조명 · 와이퍼") {
            ChoiceRow("전조등", LightMode.entries, body.lights, { it.label }) {
                onAction(CockpitAction.SetLights(it))
            }
            ChoiceRow("와이퍼", WiperMode.entries, body.wipers, { it.label }) {
                onAction(CockpitAction.SetWipers(it))
            }
        }

        PanelSection("주행 감각") {
            ChoiceRow("가속", DriveFeel.entries, state.drive.driveFeel, { it.label }) {
                onAction(CockpitAction.SetDriveFeel(it))
            }
            ChoiceRow("조향", SteeringFeel.entries, state.drive.steeringFeel, { it.label }) {
                onAction(CockpitAction.SetSteeringFeel(it))
            }
            ChoiceRow("정지 모드", StopMode.entries, state.drive.stopMode, { it.label }) {
                onAction(CockpitAction.SetStopMode(it))
            }
        }

        PanelSection("주행 보조 설정 · 표시 전용") {
            MockNote(
                "설정 값만 저장하는 MOCK 화면입니다. 실제 조향·제동·경고 기능은 연결되어 있지 않으며 " +
                    "이 앱은 주행 보조 기능을 제공하지 않습니다.",
            )
            StepperRow(
                label = "앞차 간격",
                valueText = state.assist.followDistance.toString() + "단계",
                onMinus = { onAction(CockpitAction.AdjustFollowDistance(-1)) },
                onPlus = { onAction(CockpitAction.AdjustFollowDistance(1)) },
            )
            ChoiceRow(
                "전방 충돌 경고 시점",
                CollisionWarningTiming.entries,
                state.assist.collisionWarning,
                { it.label },
            ) { onAction(CockpitAction.SetCollisionWarning(it)) }
            SwitchRow("차로 유지 보조", state.assist.laneAssist, {
                onAction(CockpitAction.SetLaneAssist(it))
            })
            SwitchRow(
                "긴급 제동 보조",
                state.assist.emergencyBraking,
                { onAction(CockpitAction.SetEmergencyBraking(it)) },
                supporting = "P(주차) 상태에서만 변경",
            )
            SwitchRow("사각지대 경고", state.assist.blindSpotAlert, {
                onAction(CockpitAction.SetBlindSpotAlert(it))
            })
            SwitchRow("제한 속도 경고", state.assist.speedLimitWarning, {
                onAction(CockpitAction.SetSpeedLimitWarning(it))
            })
        }

        PanelSection("디스플레이 · 프로필") {
            ChoiceRow("화면 테마", DisplayTheme.entries, state.drive.displayTheme, { it.label }) {
                onAction(CockpitAction.SetDisplayTheme(it))
            }
            StepperRow(
                label = "밝기 (값만 저장)",
                valueText = state.drive.brightnessPercent.toString() + "%",
                onMinus = { onAction(CockpitAction.AdjustBrightness(-10)) },
                onPlus = { onAction(CockpitAction.AdjustBrightness(10)) },
            )
            ChoiceRow(
                "운전자 프로필",
                MockCatalog.profiles.indices.toList(),
                state.profileIndex,
                { MockCatalog.profiles[it].name },
            ) { onAction(CockpitAction.SelectProfile(it)) }
        }
    }
}

@Composable
internal fun NavigationPanel(
    state: CockpitState,
    assessment: RiskAssessment,
    onAction: (CockpitAction) -> Unit,
) {
    val current = MockCatalog.destination(state.navigation.destinationId)
    val battery = state.battery.percent
    PanelColumn {
        CockpitMapPanel(
            assessment = assessment,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        )

        if (current != null) {
            PanelSection("가상 안내 중 · " + current.name) {
                Text(
                    text = formatKm(current.distanceKm) + " · 약 " + current.etaMinutes + "분",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = arrivalText(battery, current.distanceKm),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (CockpitEstimates.needsEnergyStop(battery, current.distanceKm)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                OutlinedButton(
                    onClick = { onAction(CockpitAction.CancelGuidance) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("안내 종료", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        PanelSection("목적지 선택 · 가상 목록") {
            MockNote("실제 지도·위치·경로가 아니며 실제 길 안내를 하지 않습니다. 긴 주소 입력은 제공하지 않습니다.")
            MockCatalog.destinations.forEach { destination ->
                FilledTonalButton(
                    onClick = { onAction(CockpitAction.StartGuidance(destination.id)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = destination.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (destination.id == current?.id) {
                                FontWeight.Black
                            } else {
                                FontWeight.Medium
                            },
                        )
                        Text(
                            text = formatKm(destination.distanceKm) + " · " +
                                destination.etaMinutes + "분",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

private fun arrivalText(batteryPercent: Int, distanceKm: Double): String {
    val arrival = CockpitEstimates.arrivalPercent(batteryPercent, distanceKm)
    return when {
        arrival < 0 -> "현재 잔량으로 도달 불가 (가상 추정) · 중간 정차 필요"
        arrival < CockpitEstimates.LOW_ARRIVAL_PERCENT ->
            "도착 시 약 $arrival% (가상 추정) · 중간 정차 권장"
        else -> "도착 시 약 $arrival% (가상 추정)"
    }
}

@Composable
internal fun SecurityPanel(
    state: CockpitState,
    onAction: (CockpitAction) -> Unit,
    onShowSuspectReport: () -> Unit,
) {
    val security = state.security
    var pinEntry by rememberSaveable { mutableStateOf("") }
    PanelColumn {
        PanelSection("음주운전 의심 차량 신고") {
            Text(
                text = "주변 차량의 의심 단서를 관찰했을 때 신고자 위치가 담긴 112 신고 초안을 준비합니다.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onShowSuspectReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            ) {
                Text(
                    text = "음주운전 의심 차량 신고 시연 · MOCK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        PanelSection("주차 감시 · 블랙박스 (MOCK)") {
            TileGrid(
                columns = 3,
                tiles = listOf(
                    TileSpec("주차 감시 모드", onOff(security.parkingWatch), security.parkingWatch) {
                        onAction(CockpitAction.SetParkingWatch(!security.parkingWatch))
                    },
                    TileSpec("대리 주차 모드", onOff(security.valetMode), security.valetMode) {
                        onAction(CockpitAction.SetValetMode(!security.valetMode, pinEntry))
                        pinEntry = ""
                    },
                    TileSpec("속도 제한 모드", onOff(security.speedLimitMode), security.speedLimitMode) {
                        onAction(CockpitAction.SetSpeedLimitMode(!security.speedLimitMode))
                    },
                ),
            )
            StepperRow(
                label = "제한 속도",
                valueText = security.speedLimitKph.toString() + " km/h",
                onMinus = { onAction(CockpitAction.AdjustSpeedLimit(-10)) },
                onPlus = { onAction(CockpitAction.AdjustSpeedLimit(10)) },
            )
            FilledTonalButton(
                onClick = { onAction(CockpitAction.SaveClip) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = "블랙박스 클립 저장 (가상) · 저장 " + security.clipsSaved + "건",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            MockNote("카메라·녹화·경보·속도 제한은 연결되어 있지 않습니다. 저장 건수는 화면 표시용 카운터입니다.")
        }

        PanelSection("주행 PIN (MOCK)") {
            Text(
                text = when {
                    security.pin == null -> "PIN 미설정"
                    security.driveUnlocked -> "PIN 설정됨 · 주행 잠금 해제됨"
                    else -> "PIN 설정됨 · 변속하려면 PIN 입력 필요"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "입력: " + "●".repeat(pinEntry.length) + "○".repeat(CockpitReducer.PIN_LENGTH - pinEntry.length),
                style = MaterialTheme.typography.headlineSmall,
            )
            listOf("123", "456", "789", "0").forEach { rowDigits ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowDigits.forEach { digit ->
                        FilledTonalButton(
                            onClick = {
                                if (pinEntry.length < CockpitReducer.PIN_LENGTH) pinEntry += digit
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp),
                        ) { Text(digit.toString(), style = MaterialTheme.typography.titleLarge) }
                    }
                    if (rowDigits.length == 1) {
                        OutlinedButton(
                            onClick = { pinEntry = "" },
                            modifier = Modifier
                                .weight(2f)
                                .height(60.dp),
                        ) { Text("지우기", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val pinActions = listOf<Pair<String, (String) -> CockpitAction>>(
                    "PIN 설정" to { CockpitAction.SetPin(it) },
                    "잠금 해제" to { CockpitAction.EnterPin(it) },
                    "PIN 삭제" to { CockpitAction.ClearPin(it) },
                )
                pinActions.forEach { (label, action) ->
                    Button(
                        onClick = {
                            onAction(action(pinEntry))
                            pinEntry = ""
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
                }
            }
            MockNote(
                "PIN 설정·삭제는 P(주차) 상태에서만 가능합니다. 시연용이므로 실제 비밀번호를 입력하지 마세요. " +
                    "대리 주차 모드 해제 시에도 위에 PIN을 먼저 입력하세요.",
            )
        }
    }
}

@Composable
internal fun MorePanel(
    state: CockpitState,
    onAction: (CockpitAction) -> Unit,
) {
    val trips = state.trips
    var updateChecked by rememberSaveable { mutableStateOf(false) }
    PanelColumn {
        PanelSection("주행 기록 (가상)") {
            InfoRow("누적 주행 거리", formatKm(trips.odometerKm))
            HorizontalDivider()
            TripRow(TripId.A, trips.tripAKm, trips.tripAWhPerKm, onAction)
            TripRow(TripId.B, trips.tripBKm, trips.tripBWhPerKm, onAction)
        }

        PanelSection("정비 · 서비스") {
            ChoiceRow("서비스 모드", ServiceMode.entries, state.serviceMode, { it.label }) {
                onAction(CockpitAction.SetServiceMode(it))
            }
            InfoRow("타이어 공기압", "차량 미연결 · 확인 불가")
            InfoRow("정비 알림", "차량 미연결 · 확인 불가")
            MockNote("서비스 모드는 P(주차) 상태에서만 바뀌는 MOCK 표시이며 실제 차량 설정을 바꾸지 않습니다.")
        }

        PanelSection("소프트웨어") {
            InfoRow("앱 버전", "앗차! 1.0 · MOCK 전용")
            InfoRow("차량 연결", "없음 (Pleos Vehicle SDK 미연동)")
            OutlinedButton(
                onClick = { updateChecked = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = if (updateChecked) "네트워크 미사용 · 업데이트 확인 기능 없음" else "업데이트 확인",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        PanelSection("엔터테인먼트 · 스케치패드") {
            if (state.isParked) {
                Sketchpad()
            } else {
                Text(
                    text = "P(주차) 상태에서만 사용할 수 있습니다.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            MockNote("운전자 주의 분산 방지를 위해 주차 중에만 열립니다. 게임·영상 스트리밍은 포함하지 않았습니다.")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TripRow(
    trip: TripId,
    distanceKm: Double,
    whPerKm: Int,
    onAction: (CockpitAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trip.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatKm(distanceKm) + " · " + whPerKm + " Wh/km",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        OutlinedButton(
            onClick = { onAction(CockpitAction.ResetTrip(trip)) },
            modifier = Modifier.height(56.dp),
        ) { Text("초기화", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun Sketchpad() {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val inkColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = "손가락으로 그리는 스케치패드" }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start -> current = listOf(start) },
                    onDrag = { change, _ -> current = current + change.position },
                    onDragEnd = {
                        strokes.add(current)
                        current = emptyList()
                    },
                    onDragCancel = { current = emptyList() },
                )
            },
    ) {
        (strokes + listOf(current)).forEach { points ->
            for (index in 1 until points.size) {
                drawLine(
                    color = inkColor,
                    start = points[index - 1],
                    end = points[index],
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
    OutlinedButton(
        onClick = { strokes.clear() },
        modifier = Modifier.height(56.dp),
    ) { Text("지우기", style = MaterialTheme.typography.titleMedium) }
}
