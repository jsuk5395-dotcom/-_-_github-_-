package com.example.pnu_me.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.hazard.PedestrianAlertInbox
import com.example.pnu_me.ui.model.NearbyPedestrianResult
import com.example.pnu_me.ui.model.pedestrianOutcomeText

/** 중계 서버 전송 결과 한 줄. 차량·보행자 패널에서 함께 쓴다. */
@Composable
internal fun RelayStatusText(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun PedestrianAlertPanel(
    canPublish: Boolean,
    channelLabel: String,
    results: List<NearbyPedestrianResult>?,
    onPublish: () -> Unit,
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
                text = "보행자 모바일 안전 알림 · $channelLabel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "실제 문자·FCM 전송이 아닌 시연입니다. STRONG 단서와 유효 위치가 있을 때만 " +
                    "1km 이내에서 참여한 모바일 사용자에게 90초짜리 중립 알림을 가정합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "차량·번호판·신고자·정확한 좌표는 포함하지 않습니다. " +
                    "실서비스는 위치기반서비스 검토, 모바일 동의, 신뢰 서버와 App Check가 필요합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                (!canPublish) && (results == null) -> Text(
                    text = "STRONG 수준 복수 단서와 현재 유효한 위치가 없어 공개 범위 알림을 만들지 않습니다.",
                    color = MaterialTheme.colorScheme.error,
                )

                results == null -> Button(
                    onClick = onPublish,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Text("가상 모바일 수신 결과 보기 (MOCK)")
                }

                else -> results.forEach { result ->
                    PedestrianResultRow(result)
                }
            }
            relayStatus?.let { RelayStatusText(it) }
        }
    }
}

@Composable
private fun PedestrianResultRow(result: NearbyPedestrianResult) {
    val shown = result.outcome is PedestrianAlertInbox.Outcome.Show
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (shown) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (shown) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${result.pedestrian.displayName} · ${result.pedestrian.description}",
                fontWeight = FontWeight.Bold,
            )
            Text(pedestrianOutcomeText(result.outcome))
        }
    }
}
