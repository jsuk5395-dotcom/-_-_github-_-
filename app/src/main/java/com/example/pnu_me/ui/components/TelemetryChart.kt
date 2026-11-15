package com.example.pnu_me.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.risk.RiskEvent
import com.example.pnu_me.domain.risk.SpeedPoint
import com.example.pnu_me.domain.units.SpeedUnits
import com.example.pnu_me.ui.model.TelemetryChartScaleFactory
import java.util.Locale

@Composable
internal fun TelemetryChart(
    points: List<SpeedPoint>,
    events: List<RiskEvent>,
    modifier: Modifier = Modifier,
) {
    val scale = remember(points) { TelemetryChartScaleFactory.from(points) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "속도 변화",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "세로축 속도(km/h) · 가로축 경과 시간(초)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (scale == null) {
                Text(
                    text = "표시할 수 있는 속도 데이터가 부족합니다.",
                    modifier = Modifier.padding(vertical = 52.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                val eventColor = MaterialTheme.colorScheme.error
                val gridColor = MaterialTheme.colorScheme.outlineVariant

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = 8.dp, end = 8.dp, bottom = 26.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        scale.yTicksKph.forEach { tick ->
                            Text(
                                text = formatAxis(tick),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .padding(10.dp),
                        ) {
                            val chartWidth = size.width
                            val chartHeight = size.height
                            val durationMillis = scale.durationSeconds * 1_000.0

                            repeat(3) { index ->
                                val fraction = index / 2f
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, chartHeight * fraction),
                                    end = Offset(chartWidth, chartHeight * fraction),
                                    strokeWidth = 2f,
                                )
                                drawLine(
                                    color = gridColor,
                                    start = Offset(chartWidth * fraction, 0f),
                                    end = Offset(chartWidth * fraction, chartHeight),
                                    strokeWidth = 2f,
                                )
                            }

                            fun offsetFor(point: SpeedPoint): Offset {
                                val elapsedMillis =
                                    point.timestampMillis - scale.startTimestampMillis
                                val x = (elapsedMillis / durationMillis).toFloat() * chartWidth
                                val speedKph = SpeedUnits
                                    .metersPerSecondToKilometersPerHour(point.speedMps)
                                    .toFloat()
                                val speedFraction =
                                    speedKph / scale.maximumSpeedKph.toFloat()
                                val y = chartHeight * (1f - speedFraction)
                                return Offset(x, y)
                            }

                            val path = Path()
                            points.forEachIndexed { index, point ->
                                val offset = offsetFor(point)
                                if (index == 0) {
                                    path.moveTo(offset.x, offset.y)
                                } else {
                                    path.lineTo(offset.x, offset.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 5f, cap = StrokeCap.Round),
                            )

                            points.forEach { point ->
                                val pointOffset = offsetFor(point)
                                drawCircle(
                                    color = lineColor,
                                    radius = 5f,
                                    center = pointOffset,
                                )
                                if (events.any { it.timestampMillis == point.timestampMillis }) {
                                    drawCircle(
                                        color = eventColor,
                                        radius = 9f,
                                        center = pointOffset,
                                        style = Stroke(width = 4f),
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            scale.xTicksSeconds.forEach { tick ->
                                Text(
                                    text = formatAxis(tick),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "● 속도 표본",
                        style = MaterialTheme.typography.bodySmall,
                        color = lineColor,
                    )
                    Text(
                        text = "◎ 임계값 초과 이벤트 " + events.size + "회",
                        style = MaterialTheme.typography.bodySmall,
                        color = eventColor,
                    )
                }
            }
        }
    }
}

private fun formatAxis(value: Double): String =
    String.format(Locale.KOREA, "%.0f", value)
