package com.example.pnu_me.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.hazard.RelayBanner
import com.example.pnu_me.domain.hazard.RelayPollResult
import com.example.pnu_me.domain.hazard.RelayReceiverSession
import com.example.pnu_me.domain.hazard.RelayRole
import com.example.pnu_me.domain.report.LocationFetchResult
import com.example.pnu_me.domain.report.ReporterLocation
import com.example.pnu_me.domain.report.ReporterLocationSource
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val POLL_INTERVAL_MILLIS = 5_000L
private const val BANNER_VISIBLE_MILLIS = 8_000L

/**
 * 중계 서버에서 주변 알림을 받아 짧은 배너로 보여 준다.
 * - 역할이 OFF 이면 네트워크·위치를 전혀 사용하지 않는다.
 * - 수신 판단(거리·방향·TTL·중복·빈도)은 기기 안에서 하고, 서버에는 약 1km 격자만 보낸다.
 * - 배너는 조작이 필요 없고 8초 뒤 사라진다(주행 중 주의 분산 최소화).
 */
@Composable
fun RelayReceiverHost(
    role: RelayRole,
    canChangeRole: Boolean,
    onRoleChange: (RelayRole) -> Unit,
    session: RelayReceiverSession,
    locationSource: ReporterLocationSource,
    content: @Composable BoxScope.() -> Unit,
) {
    var status by remember { mutableStateOf("주변 알림 중계: 꺼짐") }
    var banner by remember { mutableStateOf<RelayBanner?>(null) }

    LaunchedEffect(role) {
        if (role == RelayRole.OFF) {
            status = "주변 알림 중계: 꺼짐"
            return@LaunchedEffect
        }
        status = "${role.label} · 서버 확인 중"
        while (true) {
            val location = awaitLocation(locationSource)
            val now = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) { session.poll(role, location, now) }
            status = when (result) {
                RelayPollResult.NoLocation -> "${role.label} · 내 위치 확인 불가"
                is RelayPollResult.Unreachable -> "${role.label} · 서버 연결 실패"
                is RelayPollResult.Rejected -> "${role.label} · 서버 거절(${result.status})"
                is RelayPollResult.Checked -> "${role.label} · 연결됨 · 주변 제보 ${result.received}건"
            }
            (result as? RelayPollResult.Checked)?.banner?.let { banner = it }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    LaunchedEffect(banner?.alertId) {
        if (banner != null) {
            delay(BANNER_VISIBLE_MILLIS)
            banner = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        banner?.let { current ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .widthIn(max = 900.dp)
                    .fillMaxWidth(0.8f),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                    Text(
                        text = current.text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "마지막 제보 구역 기준 · 실시간 추적 아님",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clickable {
                    if (canChangeRole) {
                        onRoleChange(RelayRole.entries[(role.ordinal + 1) % RelayRole.entries.size])
                    } else {
                        status = "정차(P) 중에만 중계 설정을 바꿀 수 있습니다"
                    }
                },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(999.dp),
            shadowElevation = 4.dp,
        ) {
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private suspend fun awaitLocation(source: ReporterLocationSource): ReporterLocation? =
    suspendCancellableCoroutine { continuation ->
        source.requestCurrentLocation { result ->
            if (continuation.isActive) {
                continuation.resume((result as? LocationFetchResult.Success)?.location)
            }
        }
        continuation.invokeOnCancellation { source.cancel() }
    }
