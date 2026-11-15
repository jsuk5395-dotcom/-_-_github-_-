package com.example.pnu_me.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pnu_me.R
import com.example.pnu_me.domain.cockpit.CockpitState
import com.example.pnu_me.domain.cockpit.LightMode
import com.example.pnu_me.ui.model.suvBodyWidthFraction
import com.example.pnu_me.ui.model.vehicleSceneGeometry

/**
 * 도로 위 차량을 표현하는 독자적인 MOCK 장면.
 * 특정 제조사의 로고·차종·에셋을 복제하지 않는다.
 */
@Composable
internal fun VehicleTopView(
    state: CockpitState,
    modifier: Modifier = Modifier,
) {
    val skyTop = MaterialTheme.colorScheme.surfaceContainerHighest
    val skyBottom = MaterialTheme.colorScheme.surfaceContainer
    val accent = MaterialTheme.colorScheme.tertiary
    val alert = MaterialTheme.colorScheme.error
    val buildingColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val vehiclePainter = painterResource(R.drawable.vehicle_suv_top)
    val lightsOn = state.body.lights == LightMode.ON ||
        state.body.lights == LightMode.PARKING
    val summary = buildList {
        add(if (state.body.locked) "잠김" else "잠금 해제")
        if (state.body.frontStorageOpen) add("앞 적재함 열림")
        if (state.body.trunkOpen) add("트렁크 열림")
        if (state.body.windowsVented) add("창문 환기")
        if (state.body.mirrorsFolded) add("미러 접힘")
    }.joinToString(" · ")

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = skyBottom),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription =
                            "도로 위 차량 MOCK 장면. " + summary + ". 실제 차량 연결 아님."
                    },
            ) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(skyTop, skyBottom),
                    ),
                )

                val horizon = size.height * 0.14f
                val roadShoulder = Path().apply {
                    moveTo(size.width * 0.34f, horizon)
                    lineTo(size.width * 0.66f, horizon)
                    lineTo(size.width * 1.04f, size.height)
                    lineTo(size.width * -0.04f, size.height)
                    close()
                }
                drawPath(roadShoulder, color = Color(0xFF757B80))

                val road = Path().apply {
                    moveTo(size.width * 0.38f, horizon)
                    lineTo(size.width * 0.62f, horizon)
                    lineTo(size.width * 0.94f, size.height)
                    lineTo(size.width * 0.06f, size.height)
                    close()
                }
                drawPath(
                    path = road,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF464A4D), Color(0xFF24272A)),
                        startY = horizon,
                        endY = size.height,
                    ),
                )

                drawLine(
                    color = Color.White.copy(alpha = 0.86f),
                    start = Offset(size.width * 0.38f, horizon),
                    end = Offset(size.width * 0.06f, size.height),
                    strokeWidth = 3.dp.toPx(),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.86f),
                    start = Offset(size.width * 0.62f, horizon),
                    end = Offset(size.width * 0.94f, size.height),
                    strokeWidth = 3.dp.toPx(),
                )

                repeat(6) { index ->
                    val startFraction = index / 6f
                    val endFraction = (index + 0.42f) / 6f
                    val y1 = horizon + (size.height - horizon) * startFraction
                    val y2 = horizon + (size.height - horizon) * endFraction
                    val widthAtY1 = 2.dp.toPx() + 5.dp.toPx() * startFraction
                    drawLine(
                        color = Color.White.copy(alpha = 0.82f),
                        start = Offset(size.width * 0.5f, y1),
                        end = Offset(size.width * 0.5f, y2),
                        strokeWidth = widthAtY1,
                        cap = StrokeCap.Round,
                    )
                }

                listOf(0.12f, 0.86f).forEachIndexed { index, x ->
                    val buildingWidth = size.width * 0.12f
                    val buildingHeight = size.height * (if (index == 0) 0.18f else 0.24f)
                    drawRoundRect(
                        color = buildingColor,
                        topLeft = Offset(
                            size.width * x - buildingWidth / 2f,
                            horizon - buildingHeight,
                        ),
                        size = Size(buildingWidth, buildingHeight),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                }

                val geometry = vehicleSceneGeometry(size.width, size.height)
                val carWidth = geometry.carWidth
                val carHeight = geometry.carHeight
                val carLeft = geometry.carLeft
                val carTop = geometry.carTop

                val showVectorFallback =
                    vehiclePainter.intrinsicSize == Size.Unspecified
                if (showVectorFallback) {
                    val carCenterX = carLeft + carWidth / 2f
                    fun bodyLeftAt(yFraction: Float): Float =
                    carCenterX - carWidth * suvBodyWidthFraction(yFraction) / 2f
                fun bodyRightAt(yFraction: Float): Float =
                    carCenterX + carWidth * suvBodyWidthFraction(yFraction) / 2f

                fun vehiclePath(dx: Float = 0f, dy: Float = 0f): Path = Path().apply {
                    moveTo(bodyLeftAt(0.06f) + dx, carTop + dy)
                    lineTo(bodyRightAt(0.06f) + dx, carTop + dy)
                    quadraticTo(
                        bodyRightAt(0.02f) + dx,
                        carTop + dy,
                        bodyRightAt(0.10f) + dx,
                        carTop + carHeight * 0.055f + dy,
                    )
                    lineTo(
                        bodyRightAt(0.22f) + dx,
                        carTop + carHeight * 0.17f + dy,
                    )
                    lineTo(
                        bodyRightAt(0.82f) + dx,
                        carTop + carHeight * 0.82f + dy,
                    )
                    lineTo(
                        bodyRightAt(0.96f) + dx,
                        carTop + carHeight * 0.95f + dy,
                    )
                    quadraticTo(
                        bodyRightAt(1f) + dx,
                        carTop + carHeight + dy,
                        bodyRightAt(0.94f) + dx,
                        carTop + carHeight + dy,
                    )
                    lineTo(bodyLeftAt(0.94f) + dx, carTop + carHeight + dy)
                    quadraticTo(
                        bodyLeftAt(1f) + dx,
                        carTop + carHeight + dy,
                        bodyLeftAt(0.96f) + dx,
                        carTop + carHeight * 0.95f + dy,
                    )
                    lineTo(
                        bodyLeftAt(0.82f) + dx,
                        carTop + carHeight * 0.82f + dy,
                    )
                    lineTo(
                        bodyLeftAt(0.22f) + dx,
                        carTop + carHeight * 0.17f + dy,
                    )
                    lineTo(
                        bodyLeftAt(0.10f) + dx,
                        carTop + carHeight * 0.055f + dy,
                    )
                    quadraticTo(
                        bodyLeftAt(0.02f) + dx,
                        carTop + dy,
                        bodyLeftAt(0.06f) + dx,
                        carTop + dy,
                    )
                    close()
                }

                drawPath(
                    path = vehiclePath(3.dp.toPx(), 8.dp.toPx()),
                    color = Color.Black.copy(alpha = 0.30f),
                )

                val mirrorWidth = carWidth * if (state.body.mirrorsFolded) 0.055f else 0.11f
                listOf(carLeft - mirrorWidth * 0.62f, carLeft + carWidth - mirrorWidth * 0.38f)
                    .forEach { x ->
                        drawRoundRect(
                            color = Color(0xFF9CA5AC),
                            topLeft = Offset(x, carTop + carHeight * 0.25f),
                            size = Size(mirrorWidth, carHeight * 0.055f),
                            cornerRadius = CornerRadius(carHeight * 0.025f),
                        )
                    }

                val wheelSize = Size(carWidth * 0.16f, carHeight * 0.17f)
                listOf(0.19f, 0.68f).forEach { y ->
                    listOf(-0.055f, 0.895f).forEach { x ->
                        drawRoundRect(
                            color = Color(0xFF111315),
                            topLeft = Offset(
                                carLeft + carWidth * x,
                                carTop + carHeight * y,
                            ),
                            size = wheelSize,
                            cornerRadius = CornerRadius(wheelSize.width * 0.38f),
                        )
                    }
                }

                drawPath(
                    path = vehiclePath(),
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF858F97),
                            Color(0xFFF3F5F6),
                            Color(0xFFB2BBC2),
                        ),
                        startX = carLeft,
                        endX = carLeft + carWidth,
                    ),
                )
                drawPath(
                    path = vehiclePath(),
                    color = Color.White.copy(alpha = 0.50f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                val windshield = Path().apply {
                    moveTo(carLeft + carWidth * 0.18f, carTop + carHeight * 0.23f)
                    lineTo(carLeft + carWidth * 0.82f, carTop + carHeight * 0.23f)
                    lineTo(carLeft + carWidth * 0.75f, carTop + carHeight * 0.42f)
                    lineTo(carLeft + carWidth * 0.25f, carTop + carHeight * 0.42f)
                    close()
                }
                drawPath(
                    path = windshield,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF526C7C), Color(0xFF15222B)),
                        startY = carTop + carHeight * 0.23f,
                        endY = carTop + carHeight * 0.42f,
                    ),
                )

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF263943), Color(0xFF101A20)),
                    ),
                    topLeft = Offset(
                        carLeft + carWidth * 0.19f,
                        carTop + carHeight * 0.43f,
                    ),
                    size = Size(carWidth * 0.62f, carHeight * 0.34f),
                    cornerRadius = CornerRadius(carWidth * 0.055f),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.24f),
                    start = Offset(
                        carLeft + carWidth * 0.26f,
                        carTop + carHeight * 0.47f,
                    ),
                    end = Offset(
                        carLeft + carWidth * 0.72f,
                        carTop + carHeight * 0.72f,
                    ),
                    strokeWidth = 2.dp.toPx(),
                )

                listOf(0.14f, 0.86f).forEach { x ->
                    drawLine(
                        color = Color(0xFF465158),
                        start = Offset(
                            carLeft + carWidth * x,
                            carTop + carHeight * 0.30f,
                        ),
                        end = Offset(
                            carLeft + carWidth * x,
                            carTop + carHeight * 0.82f,
                        ),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.42f),
                        start = Offset(
                            carLeft + carWidth * (if (x < 0.5f) x + 0.025f else x - 0.025f),
                            carTop + carHeight * 0.32f,
                        ),
                        end = Offset(
                            carLeft + carWidth * (if (x < 0.5f) x + 0.025f else x - 0.025f),
                            carTop + carHeight * 0.80f,
                        ),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                listOf(0.28f, 0.72f).forEach { x ->
                    drawLine(
                        color = Color.White.copy(alpha = 0.38f),
                        start = Offset(
                            carLeft + carWidth * x,
                            carTop + carHeight * 0.05f,
                        ),
                        end = Offset(
                            carLeft + carWidth * (0.5f + (x - 0.5f) * 0.72f),
                            carTop + carHeight * 0.20f,
                        ),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }

                drawLine(
                    color = Color(0xFF30363A),
                    start = Offset(
                        carLeft + carWidth * 0.12f,
                        carTop + carHeight * 0.925f,
                    ),
                    end = Offset(
                        carLeft + carWidth * 0.88f,
                        carTop + carHeight * 0.925f,
                    ),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Square,
                )
                drawLine(
                    color = Color(0xFFCB3541),
                    start = Offset(
                        carLeft + carWidth * 0.18f,
                        carTop + carHeight * 0.885f,
                    ),
                    end = Offset(
                        carLeft + carWidth * 0.82f,
                        carTop + carHeight * 0.885f,
                    ),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Square,
                )

                listOf(0.15f, 0.65f).forEach { x ->
                    drawRoundRect(
                        color = if (lightsOn) Color(0xFFFFF2B8) else Color(0xFFDDE6EB),
                        topLeft = Offset(
                            carLeft + carWidth * x,
                            carTop + carHeight * 0.075f,
                        ),
                        size = Size(carWidth * 0.20f, carHeight * 0.035f),
                        cornerRadius = CornerRadius(carHeight * 0.018f),
                    )
                }
                drawLine(
                    color = Color(0xFFE5EAED).copy(alpha = 0.82f),
                    start = Offset(
                        carLeft + carWidth * 0.20f,
                        carTop + carHeight * 0.13f,
                    ),
                    end = Offset(
                        carLeft + carWidth * 0.80f,
                        carTop + carHeight * 0.13f,
                    ),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                }

                withTransform({
                    translate(
                        left = carLeft - carWidth * 0.10f,
                        top = carTop - carHeight * 0.03f,
                    )
                }) {
                    with(vehiclePainter) {
                        draw(
                            size = Size(
                                width = carWidth * 1.20f,
                                height = carHeight * 1.06f,
                            ),
                        )
                    }
                }

                if (lightsOn) {
                    val beam = Path().apply {
                        moveTo(carLeft + carWidth * 0.20f, carTop + carHeight * 0.08f)
                        lineTo(carLeft - carWidth * 0.20f, carTop - carHeight * 0.20f)
                        lineTo(carLeft + carWidth * 1.20f, carTop - carHeight * 0.20f)
                        lineTo(carLeft + carWidth * 0.80f, carTop + carHeight * 0.08f)
                        close()
                    }
                    drawPath(beam, color = Color(0xFFFFE27A).copy(alpha = 0.16f))
                }

                if (state.body.frontStorageOpen) {
                    drawLine(
                        color = alert,
                        start = Offset(
                            carLeft + carWidth * 0.20f,
                            carTop + carHeight * 0.18f,
                        ),
                        end = Offset(
                            carLeft + carWidth * 0.80f,
                            carTop + carHeight * 0.18f,
                        ),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                if (state.body.trunkOpen) {
                    drawLine(
                        color = alert,
                        start = Offset(
                            carLeft + carWidth * 0.22f,
                            carTop + carHeight * 0.88f,
                        ),
                        end = Offset(
                            carLeft + carWidth * 0.78f,
                            carTop + carHeight * 0.88f,
                        ),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "MOCK",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "  ·  주행 장면",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xDD151719),
                contentColor = Color.White,
            ) {
                Text(
                    text = state.gear.label,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "시각화용 장면 · 실제 차량 위치 아님",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
