package com.example.pnu_me.domain.hazard

import com.example.pnu_me.domain.report.SuspicionLevel
import java.util.Locale
import kotlin.math.floor

/**
 * 로컬 중계 서버(backend/hazard-relay)와 주고받는 메시지 규칙.
 * 서버의 필드 화이트리스트·격자 규칙과 반드시 같아야 한다.
 */
object RelayCells {
    /** 0.01도 격자 ≈ 1.1km. 수신자는 정밀 위치 대신 이 격자만 서버에 보낸다. */
    private const val SCALE = 100.0

    fun cellOf(latitude: Double, longitude: Double): String =
        "${floor(latitude * SCALE).toLong()}:${floor(longitude * SCALE).toLong()}"

    /** 1km 반경을 덮도록 자기 격자와 주변 8개 격자를 조회한다. */
    fun neighborhood(latitude: Double, longitude: Double): List<String> {
        val baseLat = floor(latitude * SCALE).toLong()
        val baseLon = floor(longitude * SCALE).toLong()
        return buildList {
            for (dLat in -1L..1L) {
                for (dLon in -1L..1L) add("${baseLat + dLat}:${baseLon + dLon}")
            }
        }
    }
}

object RelayCodec {
    fun encodeVehicle(alert: HazardAlert): String = MiniJson.write(
        linkedMapOf(
            "alertId" to alert.alertId,
            "latitude" to alert.latitude,
            "longitude" to alert.longitude,
            "headingDegrees" to alert.headingDegrees,
            "level" to alert.level.name,
            "isDemo" to alert.isDemo,
        ),
    )

    fun encodePedestrian(alert: PedestrianSafetyAlert): String = MiniJson.write(
        linkedMapOf(
            "alertId" to alert.alertId,
            "latitude" to alert.latitude,
            "longitude" to alert.longitude,
            "isDemo" to alert.isDemo,
        ),
    )

    /**
     * 서버는 절대 시각 대신 ageMillis/remainingMillis 를 준다.
     * 수신 기기 시계([nowMillis]) 기준으로 발행·만료 시각을 복원해 기기 간 시계 차이를 피한다.
     * 형식이 잘못된 항목은 버린다(값을 지어내지 않는다).
     */
    fun decodeVehicleList(json: String, nowMillis: Long): List<HazardAlert> =
        alertObjects(json).mapNotNull { obj ->
            val times = relativeTimes(obj, nowMillis) ?: return@mapNotNull null
            val level = (obj["level"] as? String)?.let { name ->
                SuspicionLevel.entries.firstOrNull { it.name == name }
            } ?: return@mapNotNull null
            val rawHeading = obj["headingDegrees"]
            if (rawHeading != null && rawHeading !is Double) return@mapNotNull null
            val heading = rawHeading as? Double
            HazardAlert(
                alertId = obj["alertId"] as? String ?: return@mapNotNull null,
                latitude = obj["latitude"] as? Double ?: return@mapNotNull null,
                longitude = obj["longitude"] as? Double ?: return@mapNotNull null,
                headingDegrees = heading,
                level = level,
                issuedAtMillis = times.first,
                expiresAtMillis = times.second,
                isDemo = obj["isDemo"] as? Boolean ?: return@mapNotNull null,
            )
        }

    fun decodePedestrianList(json: String, nowMillis: Long): List<PedestrianSafetyAlert> =
        alertObjects(json).mapNotNull { obj ->
            val times = relativeTimes(obj, nowMillis) ?: return@mapNotNull null
            PedestrianSafetyAlert(
                alertId = obj["alertId"] as? String ?: return@mapNotNull null,
                latitude = obj["latitude"] as? Double ?: return@mapNotNull null,
                longitude = obj["longitude"] as? Double ?: return@mapNotNull null,
                issuedAtMillis = times.first,
                expiresAtMillis = times.second,
                isDemo = obj["isDemo"] as? Boolean ?: return@mapNotNull null,
            )
        }

    private fun alertObjects(json: String): List<Map<String, Any?>> {
        val root = MiniJson.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("root must be object")
        val alerts = root["alerts"] as? List<*> ?: throw IllegalArgumentException("alerts missing")
        return alerts.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            item as? Map<String, Any?>
        }
    }

    private fun relativeTimes(obj: Map<String, Any?>, nowMillis: Long): Pair<Long, Long>? {
        val age = (obj["ageMillis"] as? Double)?.toLong() ?: return null
        val remaining = (obj["remainingMillis"] as? Double)?.toLong() ?: return null
        if (age < 0L || remaining <= 0L || nowMillis < age) return null
        return (nowMillis - age) to (nowMillis + remaining)
    }
}

/** 의존성 없는 최소 JSON 파서/작성기. 숫자는 모두 Double 로 읽는다. */
object MiniJson {
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipWhitespace()
        require(parser.atEnd()) { "trailing data" }
        return value
    }

    fun write(map: Map<String, Any?>): String =
        map.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
            quote(key) + ":" + writeValue(value)
        }

    private fun writeValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Int, is Long -> value.toString()
        is Double -> {
            require(value.isFinite()) { "non-finite number" }
            String.format(Locale.ROOT, "%.6f", value)
        }
        else -> throw IllegalArgumentException("unsupported type")
    }

    fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append(String.format(Locale.ROOT, "\\u%04x", c.code)) else append(c)
            }
        }
        append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipWhitespace()
            require(i < s.length) { "eof" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw IllegalArgumentException("unexpected $c")
            }
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                i++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = string()
                skipWhitespace()
                expect(':')
                map[key] = value()
                skipWhitespace()
                when (next()) {
                    '}' -> return map
                    ',' -> continue
                    else -> throw IllegalArgumentException("expected , or }")
                }
            }
        }

        private fun array(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                i++
                return list
            }
            while (true) {
                list += value()
                skipWhitespace()
                when (next()) {
                    ']' -> return list
                    ',' -> continue
                    else -> throw IllegalArgumentException("expected , or ]")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = next()
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = next()) {
                        '"', '\\', '/' -> sb.append(e)
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            require(i + 4 <= s.length) { "bad unicode escape" }
                            sb.append(s.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> throw IllegalArgumentException("bad escape")
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && s[i] in "+-0123456789.eE") i++
            return s.substring(start, i).toDouble()
        }

        private fun literal(word: String, result: Any?): Any? {
            require(s.startsWith(word, i)) { "bad literal" }
            i += word.length
            return result
        }

        private fun peek(): Char {
            require(i < s.length) { "eof" }
            return s[i]
        }

        private fun next(): Char = peek().also { i++ }

        private fun expect(c: Char) {
            require(next() == c) { "expected $c" }
        }
    }
}
