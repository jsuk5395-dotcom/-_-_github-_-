package com.example.pnu_me.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.report.CapabilityStatus
import com.example.pnu_me.domain.report.ReportChannel
import com.example.pnu_me.domain.report.ReportReadiness
import com.example.pnu_me.domain.report.WitnessDetails
import com.example.pnu_me.domain.report.WitnessDirection
import com.example.pnu_me.domain.report.WitnessVehicleColor
import com.example.pnu_me.domain.report.WitnessVehicleType
import com.example.pnu_me.ui.model.capabilityStatusText

@Composable
internal fun ReportReadinessCard(
    readiness: ReportReadiness,
    locationSourceLabel: String,
    drivingStateLabel: String,
    onRequestLocationPermission: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("신고 준비 상태", fontWeight = FontWeight.Bold)
            Text("위치 · ${capabilityStatusText(readiness.location)} · $locationSourceLabel")
            Text("112 전화 · ${capabilityStatusText(readiness.dialer)}")
            Text("112 문자 · ${capabilityStatusText(readiness.sms)}")
            Text("음성 읽기 · ${capabilityStatusText(readiness.voiceGuidance)}")
            Text("음성 예/아니오 · ${capabilityStatusText(readiness.voiceResponse)}")
            Text("주행 상태 출처 · $drivingStateLabel")
            if (readiness.isDemo) {
                Text("시연 모드에서는 전화·문자 앱을 열지 않습니다.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (readiness.location == CapabilityStatus.NEEDS_PERMISSION) {
                    Button(onClick = onRequestLocationPermission) {
                        Text("위치 권한 요청")
                    }
                }
                TextButton(onClick = onRefresh) {
                    Text("상태 새로고침")
                }
            }
        }
    }
}

@Composable
internal fun WitnessDetailsPanel(
    details: WitnessDetails,
    onDetailsChange: (WitnessDetails) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("정차 후 목격 정보", fontWeight = FontWeight.Bold)
            Text("직접 확인한 항목만 선택하세요. 입력값은 앱 종료·삭제·자동 만료 시 폐기됩니다.")

            CycleButton(
                label = "차량 종류",
                value = details.vehicleType?.label,
                onClick = {
                    onDetailsChange(details.copy(vehicleType = next(details.vehicleType, WitnessVehicleType.entries)))
                },
            )
            CycleButton(
                label = "차량 색상",
                value = details.vehicleColor?.label,
                onClick = {
                    onDetailsChange(details.copy(vehicleColor = next(details.vehicleColor, WitnessVehicleColor.entries)))
                },
            )
            CycleButton(
                label = "마지막 진행방향",
                value = details.lastSeenDirection?.label,
                onClick = {
                    onDetailsChange(details.copy(lastSeenDirection = next(details.lastSeenDirection, WitnessDirection.entries)))
                },
            )
            CycleButton(
                label = "현재 시야",
                value = when (details.stillVisible) {
                    true -> "보임"
                    false -> "보이지 않음"
                    null -> null
                },
                onClick = {
                    onDetailsChange(
                        details.copy(
                            stillVisible = when (details.stillVisible) {
                                null -> true
                                true -> false
                                false -> null
                            },
                        ),
                    )
                },
            )
            OutlinedTextField(
                value = details.plateFragment.orEmpty(),
                onValueChange = {
                    onDetailsChange(details.copy(plateFragment = it.take(WitnessDetails.MAX_PLATE_FRAGMENT_LENGTH)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("번호판 일부 · 선택 입력") },
                supportingText = { Text("정차 상태에서 직접 확인한 경우에만 입력") },
                singleLine = true,
            )
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("목격 정보 초안에 반영")
            }
        }
    }
}

@Composable
internal fun ReportActionPanel(
    readiness: ReportReadiness,
    isDemo: Boolean,
    handoffStatus: String?,
    voiceStatus: String?,
    onOpenChannel: (ReportChannel) -> Unit,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("신고 인계·개인정보", fontWeight = FontWeight.Bold)
            Text("외부 앱이 열려도 실제 112 접수 완료를 의미하지 않습니다.")
            Button(
                onClick = { onOpenChannel(ReportChannel.DIALER) },
                enabled = !isDemo && readiness.dialer == CapabilityStatus.READY,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("112 전화 앱 열기")
            }
            Button(
                onClick = { onOpenChannel(ReportChannel.SMS) },
                enabled = !isDemo && readiness.sms == CapabilityStatus.READY,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("112 문자 앱 열기")
            }
            OutlinedButton(
                onClick = onSpeak,
                enabled = readiness.voiceGuidance == CapabilityStatus.READY,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("안전 요약 음성으로 듣기 · 정차 전용")
            }
            handoffStatus?.let { Text(it) }
            voiceStatus?.let { Text(it) }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("초안과 위치 정보 즉시 삭제")
            }
        }
    }
}

@Composable
private fun CycleButton(
    label: String,
    value: String?,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text("$label · ${value ?: "선택 안 함"}")
    }
}

private fun <T> next(current: T?, entries: List<T>): T? {
    if (current == null) return entries.firstOrNull()
    val index = entries.indexOf(current)
    return if (index < 0 || index == entries.lastIndex) null else entries[index + 1]
}
