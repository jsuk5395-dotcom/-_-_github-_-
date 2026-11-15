package com.example.pnu_me.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.hazard.HazardAlertInbox
import com.example.pnu_me.domain.hazard.HazardMapMarker
import com.example.pnu_me.ui.model.NearbyResult
import com.example.pnu_me.ui.model.hazardMapStatusText
import com.example.pnu_me.ui.model.nearbyOutcomeText
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 신고 초안 아래에 표시하는 "주변 차량 주의 알림" 시연 패널.
 * 탑승자가 버튼을 눌러야만 발행한다(자동 발행 없음). 초안 1개당 1회.
 */
@Composable
internal fun HazardBroadcastPanel(
    canBroadcast: Boolean,
    channelLabel: String,
    results: List<NearbyResult>?,
    headingAvailable: Boolean,
    onBroadcast: () -> Unit,
    /** 중계 서버 전송 결과. 중계를 끄면 null */
    relayStatus: String? = null,
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
                text = "주변 차량 주의 알림 · $channelLabel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "차량 번호·신고자 정보 없이 약 100m 단위 마지막 제보 구역과 진행방향만 사용합니다. " +
                    "1km 이내·같은 방향 차량에만 표시하고 90초 후 자동 삭제합니다. 실시간 추적이 아닙니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                !canBroadcast && results == null -> Text(
                    text = "STRONG 단서와 정확도 100m 이내의 최신 위치가 있어야 표시할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                results == null -> Button(
                    onClick = onBroadcast,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Text("주변 차량에 주의 알림 보내기 (시연)", style = MaterialTheme.typography.titleMedium)
                }

                else -> {
                    if (!headingAvailable) {
                        Text(
                            text = "신고 차량 진행방향 정보가 없어 방향 필터 없이 거리만 적용했습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    results.forEach { result -> NearbyResultRow(result) }
                }
            }
            relayStatus?.let { RelayStatusText(it) }
        }
    }
}

@Composable
private fun NearbyResultRow(result: NearbyResult) {
    val shown = result.outcome is HazardAlertInbox.Outcome.Show
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (shown) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (shown) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${result.vehicle.displayName} · ${result.vehicle.description}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = nearbyOutcomeText(result.outcome),
                style = MaterialTheme.typography.bodyLarge,
            )
            result.mapMarker?.let { marker ->
                HazardZoneMapPreview(marker)
            }
        }
    }
}

@Composable
private fun HazardZoneMapPreview(marker: HazardMapMarker) {
    val mapBackground = MaterialTheme.colorScheme.surface
    val roadColor = MaterialTheme.colorScheme.outlineVariant
    val receiverColor = MaterialTheme.colorScheme.primary
    val hazardColor = MaterialTheme.colorScheme.error

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = hazardMapStatusText(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .semantics {
                    contentDescription =
                        "MOCK 수신 차량 지도. 중앙은 수신 차량, 붉은 원은 실시간 위치가 아닌 마지막 제보 구역입니다."
                },
        ) {
            drawRect(mapBackground)
            drawLine(
                color = roadColor,
                start = Offset(size.width * 0.08f, size.height * 0.72f),
                end = Offset(size.width * 0.92f, size.height * 0.28f),
                strokeWidth = 12.dp.toPx(),
            )
            drawLine(
                color = roadColor,
                start = Offset(size.width * 0.20f, 0f),
                end = Offset(size.width * 0.80f, size.height),
                strokeWidth = 8.dp.toPx(),
            )

            val receiver = Offset(size.width / 2f, size.height / 2f)
            val bearingRadians = Math.toRadians(marker.bearingFromReceiverDegrees)
            val usableRadius = min(size.width, size.height) * 0.38f
            val distanceRatio = (marker.distanceMeters / 1_000f).coerceIn(0f, 1f)
            val hazard = Offset(
                x = receiver.x + sin(bearingRadians).toFloat() * usableRadius * distanceRatio,
                y = receiver.y - cos(bearingRadians).toFloat() * usableRadius * distanceRatio,
            )

            drawCircle(color = receiverColor, radius = 8.dp.toPx(), center = receiver)
            drawCircle(color = hazardColor.copy(alpha = 0.22f), radius = 22.dp.toPx(), center = hazard)
            drawCircle(
                color = hazardColor,
                radius = 12.dp.toPx(),
                center = hazard,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
