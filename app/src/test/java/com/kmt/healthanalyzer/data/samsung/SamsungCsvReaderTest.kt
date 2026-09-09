package com.kmt.healthanalyzer.data.samsung

import com.kmt.healthanalyzer.data.samsung.csv.SamsungCsvException
import com.kmt.healthanalyzer.data.samsung.csv.SamsungCsvReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SamsungCsvReaderTest {

    private val reader = SamsungCsvReader()

    private fun source(vararg lines: String) = lines.joinToString("\n").byteInputStream()

    @Test
    fun `reads the samsung metadata line as the document descriptor`() {
        val input = source(
            "﻿com.samsung.shealth.sleep,7006011,11",
            "sleep_score,sleep_duration,",
            "71,401,",
        )

        val doc = reader.read(input)

        assertEquals("com.samsung.shealth.sleep", doc.dataType)
        assertEquals("7006011", doc.appVersion)
        assertEquals("11", doc.schemaVersion)
    }

    @Test
    fun `strips the utf8 byte order mark from the first column name`() {
        val input = source(
            "﻿com.samsung.health.weight,7006011,25",
            "﻿weight,height,",
            "91.0,169.0,",
        )

        val rows = reader.read(input).rows

        assertEquals(91.0, rows.single().double("weight")!!, 0.001)
    }

    @Test
    fun `ignores the trailing empty column produced by the trailing comma`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "a,b,",
            "1,2,",
        )

        val doc = reader.read(input)

        assertEquals(listOf("a", "b"), doc.columns)
        assertEquals(2, doc.rows.single().size)
    }

    @Test
    fun `parses quoted fields that contain commas`() {
        val input = source(
            "com.samsung.shealth.insight_message,7006011,17",
            "id,message,",
            "1,\"Vous dormez davantage, c'est bien !\",",
        )

        val row = reader.read(input).rows.single()

        assertEquals("Vous dormez davantage, c'est bien !", row.string("message"))
    }

    @Test
    fun `parses escaped double quotes inside a quoted field`() {
        val input = source(
            "com.samsung.shealth.insight_message,7006011,17",
            "id,message,",
            "1,\"He said \"\"hello\"\" twice\",",
        )

        assertEquals("He said \"hello\" twice", reader.read(input).rows.single().string("message"))
    }

    @Test
    fun `returns null for empty cells instead of blank strings`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "sleep_score,mental_recovery,",
            ",82.0,",
        )

        val row = reader.read(input).rows.single()

        assertNull(row.string("sleep_score"))
        assertNull(row.double("sleep_score"))
        assertEquals(82.0, row.double("mental_recovery")!!, 0.001)
    }

    @Test
    fun `returns null when a numeric cell is not a number`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "sleep_score,",
            "n/a,",
        )

        assertNull(reader.read(input).rows.single().double("sleep_score"))
    }

    @Test
    fun `returns null for a column that the document does not declare`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "sleep_score,",
            "71,",
        )

        assertNull(reader.read(input).rows.single().string("vo2_max"))
    }

    @Test
    fun `pads a short row so that missing trailing cells read as null`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "a,b,c,",
            "1,",
        )

        val row = reader.read(input).rows.single()

        assertEquals("1", row.string("a"))
        assertNull(row.string("c"))
    }

    @Test
    fun `parses a samsung local timestamp against the row time offset`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "start_time,time_offset,",
            "2025-07-02 00:45:00.000,UTC+0200,",
        )

        val row = reader.read(input).rows.single()
        val instant = row.instant("start_time", offsetColumn = "time_offset")

        assertEquals("2025-07-01T22:45:00Z", instant.toString())
    }

    @Test
    fun `falls back to utc when the row carries no time offset`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "start_time,",
            "2025-07-02 00:45:00.000,",
        )

        val instant = reader.read(input).rows.single().instant("start_time", offsetColumn = "time_offset")

        assertEquals("2025-07-02T00:45:00Z", instant.toString())
    }

    @Test
    fun `exposes the local date of a timestamp in the row own offset`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "day_time,time_offset,",
            "2026-03-01 00:00:00.000,UTC+0100,",
        )

        val row = reader.read(input).rows.single()

        assertEquals(LocalDate.of(2026, 3, 1), row.localDate("day_time", offsetColumn = "time_offset"))
    }

    @Test
    fun `parses a negative time offset`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "start_time,time_offset,",
            "2025-07-02 00:45:00.000,UTC-0500,",
        )

        val row = reader.read(input).rows.single()

        assertEquals(ZoneOffset.ofHours(-5), row.zoneOffset("time_offset"))
    }

    @Test
    fun `skips blank lines`() {
        val input = source(
            "com.samsung.shealth.sleep,7006011,11",
            "a,",
            "1,",
            "",
            "2,",
        )

        assertEquals(2, reader.read(input).rows.size)
    }

    @Test
    fun `rejects a file whose metadata line is malformed`() {
        val input = source("not-a-samsung-header", "a,", "1,")

        val error = runCatching { reader.read(input) }.exceptionOrNull()

        assertTrue(error is SamsungCsvException)
    }

    @Test
    fun `rejects an empty file`() {
        val error = runCatching { reader.read("".byteInputStream()) }.exceptionOrNull()

        assertTrue(error is SamsungCsvException)
    }

    @Test
    fun `streams rows without materialising the whole file`() {
        val body = (1..500).joinToString("\n") { "$it," }
        val input = source("com.samsung.shealth.sleep,7006011,11", "value,", body)

        var count = 0
        reader.forEachRow(input) { count += 1 }

        assertEquals(500, count)
    }
}
