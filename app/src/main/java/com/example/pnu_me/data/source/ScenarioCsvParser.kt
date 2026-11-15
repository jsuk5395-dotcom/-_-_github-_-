package com.example.pnu_me.data.source

import com.example.pnu_me.data.model.DataQuality
import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample

object ScenarioCsvParser {
    private const val HEADER = "timestamp_ms,speed_mps,steering_deg,quality"

    fun parse(csv: String, origin: TelemetryOrigin): List<TelemetrySample> {
        val rows = csv.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (rows.isEmpty()) return emptyList()

        require(rows.first() == HEADER) {
            "시나리오 CSV 헤더가 올바르지 않습니다."
        }

        return rows.drop(1).mapIndexed { index, row ->
            val lineNumber = index + 2
            val columns = row.split(",", limit = 4)
            require(columns.size == 4) {
                "시나리오 CSV " + lineNumber + "행의 열 개수가 올바르지 않습니다."
            }

            val timestampMillis = columns[0].trim().toLongOrNull()
                ?: error("시나리오 CSV " + lineNumber + "행의 시각이 올바르지 않습니다.")
            val speedText = columns[1].trim()
            val speedMps = speedText.takeIf(String::isNotEmpty)?.toDoubleOrNull()
                ?: if (speedText.isEmpty()) null else error(
                    "시나리오 CSV " + lineNumber + "행의 속도가 올바르지 않습니다.",
                )
            val steeringText = columns[2].trim()
            val steeringDegrees = steeringText.takeIf(String::isNotEmpty)?.toDoubleOrNull()
                ?: if (steeringText.isEmpty()) null else error(
                    "시나리오 CSV " + lineNumber + "행의 조향각이 올바르지 않습니다.",
                )
            val qualityText = columns[3].trim()
            val quality = if (qualityText.isEmpty()) {
                if (speedMps == null) DataQuality.MISSING else DataQuality.VALID
            } else {
                runCatching { DataQuality.valueOf(qualityText) }
                    .getOrElse {
                        error(
                            "시나리오 CSV " + lineNumber + "행의 품질 값이 올바르지 않습니다.",
                        )
                    }
            }

            TelemetrySample(
                timestampMillis = timestampMillis,
                speedMps = speedMps,
                steeringDegrees = steeringDegrees,
                origin = origin,
                quality = quality,
            )
        }
    }
}
