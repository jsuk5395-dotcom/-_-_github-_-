package com.example.pnu_me.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.ui.model.cockpitMapUiModel

@Composable
internal fun CockpitMapPanel(
    assessment: RiskAssessment,
    modifier: Modifier = Modifier,
) {
    val model = cockpitMapUiModel(assessment.state)
    val mapBackground = MaterialTheme.colorScheme.surfaceVariant
    val blockColor = MaterialTheme.colorScheme.surface
    val minorRoadColor = MaterialTheme.colorScheme.outlineVariant
    val routeColor = MaterialTheme.colorScheme.tertiary
    val vehicleColor = if (model.needsAttention) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = mapBackground),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription =
                            "실제 지도가 아닌 모의 주행 경로 화면. 현재 차량 위치는 시연용입니다."
                    },
            ) {
                drawRect(mapBackground)

                val blocks = listOf(
                    floatArrayOf(0.05f, 0.10f, 0.18f, 0.18f),
                    floatArrayOf(0.28f, 0.06f, 0.17f, 0.22f),
                    floatArrayOf(0.54f, 0.08f, 0.20f, 0.18f),
                    floatArrayOf(0.80f, 0.05f, 0.15f, 0.24f),
                    floatArrayOf(0.07f, 0.40f, 0.20f, 0.20f),
                    floatArrayOf(0.35f, 0.37f, 0.17f, 0.19f),
                    floatArrayOf(0.65f, 0.38f, 0.25f, 0.21f),
                    floatArrayOf(0.10f, 0.72f, 0.18f, 0.19f),
                    floatArrayOf(0.42f, 0.70f, 0.20f, 0.20f),
                    floatArrayOf(0.73f, 0.72f, 0.18f, 0.17f),
                )
                blocks.forEach { block ->
                    drawRoundRect(
                        color = blockColor.copy(alpha = 0.72f),
                        topLeft = Offset(size.width * block[0], size.height * block[1]),
                        size = Size(size.width * block[2], size.height * block[3]),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                }

                listOf(0.31f, 0.63f).forEach { x ->
                    drawLine(
                        color = minorRoadColor,
                        start = Offset(size.width * x, 0f),
                        end = Offset(size.width * (x - 0.05f), size.height),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                listOf(0.33f, 0.66f).forEach { y ->
                    drawLine(
                        color = minorRoadColor,
                        start = Offset(0f, size.height * y),
                        end = Offset(size.width, size.height * (y + 0.04f)),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                val route = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.82f)
                    cubicTo(
                        size.width * 0.24f,
                        size.height * 0.68f,
                        size.width * 0.26f,
                        size.height * 0.50f,
                        size.width * 0.43f,
                        size.height * 0.52f,
                    )
                    cubicTo(
                        size.width * 0.60f,
                        size.height * 0.54f,
                        size.width * 0.61f,
                        size.height * 0.25f,
                        size.width * 0.85f,
                        size.height * 0.20f,
                    )
                }
                drawPath(
                    path = route,
                    color = Color.White.copy(alpha = 0.92f),
                    style = Stroke(
                        width = 16.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
                drawPath(
                    path = route,
                    color = routeColor,
                    style = Stroke(
                        width = 8.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )

                val marker = Offset(size.width * 0.43f, size.height * 0.52f)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.18f),
                    radius = 23.dp.toPx(),
                    center = marker + Offset(2.dp.toPx(), 4.dp.toPx()),
                )
                drawCircle(
                    color = vehicleColor,
                    radius = 21.dp.toPx(),
                    center = marker,
                )
                val direction = Path().apply {
                    moveTo(marker.x, marker.y - 12.dp.toPx())
                    lineTo(marker.x - 8.dp.toPx(), marker.y + 9.dp.toPx())
                    lineTo(marker.x, marker.y + 5.dp.toPx())
                    lineTo(marker.x + 8.dp.toPx(), marker.y + 9.dp.toPx())
                    close()
                }
                drawPath(direction, color = Color.White)
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = model.status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                Text(
                    text = model.sourceNotice,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "P",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "정차 상태에서 시연하세요",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
