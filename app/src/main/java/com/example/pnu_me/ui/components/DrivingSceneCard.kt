package com.example.pnu_me.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.ui.model.drivingSceneUiModel
import kotlin.math.min

@Composable
internal fun DrivingSceneCard(
    assessment: RiskAssessment,
    modifier: Modifier = Modifier,
) {
    val scene = drivingSceneUiModel(assessment.state)
    val skyColor = MaterialTheme.colorScheme.tertiaryContainer
    val roadsideColor = MaterialTheme.colorScheme.primaryContainer
    val carColor = if (scene.needsAttention) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val carWindowColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val statusColor = if (scene.needsAttention) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .semantics {
                        contentDescription =
                            "도로 위를 주행하는 내 자동차 모의 화면. 실제 차량 연결 아님."
                    },
            ) {
                drawRect(color = skyColor)

                val horizonY = size.height * 0.34f
                drawRect(
                    color = roadsideColor,
                    topLeft = Offset(0f, horizonY),
                    size = Size(size.width, size.height - horizonY),
                )

                val road = Path().apply {
                    moveTo(size.width * 0.34f, horizonY)
                    lineTo(size.width * 0.66f, horizonY)
                    lineTo(size.width * 0.94f, size.height)
                    lineTo(size.width * 0.06f, size.height)
                    close()
                }
                drawPath(road, color = Color(0xFF34383F))

                val roadCenterX = size.width / 2f
                repeat(4) { index ->
                    val progress = (index + 0.45f) / 4.5f
                    val segmentY = horizonY + (size.height - horizonY) * progress
                    val segmentHeight = 8f + (progress * 30f)
                    val segmentWidth = 3f + (progress * 8f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.88f),
                        topLeft = Offset(
                            roadCenterX - segmentWidth / 2f,
                            segmentY - segmentHeight / 2f,
                        ),
                        size = Size(segmentWidth, segmentHeight),
                        cornerRadius = CornerRadius(segmentWidth / 2f),
                    )
                }

                val carWidth = min(size.width * 0.10f, 72.dp.toPx())
                val carHeight = carWidth * 1.50f
                val carLeft = roadCenterX - carWidth / 2f
                val carTop = size.height * 0.42f
                val wheelWidth = carWidth * 0.12f
                val wheelHeight = carHeight * 0.20f

                listOf(
                    Offset(carLeft - wheelWidth * 0.45f, carTop + carHeight * 0.18f),
                    Offset(carLeft + carWidth - wheelWidth * 0.55f, carTop + carHeight * 0.18f),
                    Offset(carLeft - wheelWidth * 0.45f, carTop + carHeight * 0.64f),
                    Offset(carLeft + carWidth - wheelWidth * 0.55f, carTop + carHeight * 0.64f),
                ).forEach { wheelOffset ->
                    drawRoundRect(
                        color = Color(0xFF111318),
                        topLeft = wheelOffset,
                        size = Size(wheelWidth, wheelHeight),
                        cornerRadius = CornerRadius(wheelWidth / 2f),
                    )
                }

                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.22f),
                    topLeft = Offset(carLeft + 5f, carTop + 8f),
                    size = Size(carWidth, carHeight),
                    cornerRadius = CornerRadius(carWidth * 0.24f),
                )
                drawRoundRect(
                    color = carColor,
                    topLeft = Offset(carLeft, carTop),
                    size = Size(carWidth, carHeight),
                    cornerRadius = CornerRadius(carWidth * 0.24f),
                )
                drawRoundRect(
                    color = carWindowColor,
                    topLeft = Offset(
                        carLeft + carWidth * 0.18f,
                        carTop + carHeight * 0.20f,
                    ),
                    size = Size(carWidth * 0.64f, carHeight * 0.32f),
                    cornerRadius = CornerRadius(carWidth * 0.10f),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.92f),
                    start = Offset(
                        carLeft + carWidth * 0.22f,
                        carTop + carHeight * 0.08f,
                    ),
                    end = Offset(
                        carLeft + carWidth * 0.78f,
                        carTop + carHeight * 0.08f,
                    ),
                    strokeWidth = carWidth * 0.07f,
                    cap = StrokeCap.Round,
                )
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = scene.statusLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                    Text(
                        text = scene.detailLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "●",
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = scene.sourceNotice,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
