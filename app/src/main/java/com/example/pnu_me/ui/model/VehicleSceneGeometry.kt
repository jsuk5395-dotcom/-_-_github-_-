package com.example.pnu_me.ui.model

import kotlin.math.min

data class VehicleSceneGeometry(
    val carWidth: Float,
    val carHeight: Float,
    val carLeft: Float,
    val carTop: Float,
) {
    val carBottom: Float get() = carTop + carHeight
}

/**
 * 위에서 본 SUV 차체의 세로 위치별 차폭 비율.
 * 낮고 급격히 좁아지는 세단형 대신 넓은 숄더와 직립한 후면을 유지한다.
 */
fun suvBodyWidthFraction(verticalFraction: Float): Float {
    val y = verticalFraction.coerceIn(0f, 1f)
    return when {
        y <= 0.20f -> 0.78f + (0.88f - 0.78f) * (y / 0.20f)
        y <= 0.50f -> 0.88f + (0.98f - 0.88f) * ((y - 0.20f) / 0.30f)
        y <= 0.82f -> 0.98f + (0.94f - 0.98f) * ((y - 0.50f) / 0.32f)
        else -> 0.94f + (0.88f - 0.94f) * ((y - 0.82f) / 0.18f)
    }
}

/**
 * 차량 본체와 후면 상태 표시가 오버레이 위의 안전 영역 안에 남도록 계산한다.
 */
fun vehicleSceneGeometry(
    widthPx: Float,
    heightPx: Float,
): VehicleSceneGeometry {
    val safeWidth = widthPx.coerceAtLeast(0f)
    val safeHeight = heightPx.coerceAtLeast(0f)
    val carWidth = min(safeWidth * 0.44f, safeHeight * 0.31f)
    val carHeight = carWidth * 1.72f

    return VehicleSceneGeometry(
        carWidth = carWidth,
        carHeight = carHeight,
        carLeft = (safeWidth - carWidth) / 2f,
        carTop = safeHeight * 0.27f,
    )
}
