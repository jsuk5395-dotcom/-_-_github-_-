package com.example.pnu_me.data.source

import com.example.pnu_me.data.model.DataQuality
import com.example.pnu_me.data.model.TelemetryOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScenarioCsvParserTest {
    @Test
    fun blankSpeed_isKeptAsMissingInsteadOfInventingAValue() {
        val samples = ScenarioCsvParser.parse(
            csv = """
                timestamp_ms,speed_mps,steering_deg,quality
                0,12.0,,VALID
                1000,,,MISSING
            """.trimIndent(),
            origin = TelemetryOrigin.MOCK,
        )

        assertNull(samples[1].speedMps)
        assertEquals(DataQuality.MISSING, samples[1].quality)
        assertEquals(TelemetryOrigin.MOCK, samples[1].origin)
    }

    @Test
    fun emptyCsv_isReturnedAsAnEmptyDataset() {
        assertEquals(
            emptyList<Any>(),
            ScenarioCsvParser.parse("", TelemetryOrigin.MOCK),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHeader_isRejected() {
        ScenarioCsvParser.parse(
            csv = "time,speed\n0,10",
            origin = TelemetryOrigin.MOCK,
        )
    }
}
