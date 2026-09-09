package com.kmt.healthanalyzer.data.samsung.mapper

import com.kmt.healthanalyzer.data.samsung.csv.SamsungCsvReader
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SamsungMappersTest {

    private val reader = SamsungCsvReader()

    private fun rowsOf(dataType: String, header: String, vararg lines: String) =
        reader.read(
            (listOf("$dataType,7006011,1", header) + lines).joinToString("\n").byteInputStream()
        ).rows

    // --- Sommeil ---------------------------------------------------------

    @Test
    fun `maps a sleep session into a sleep night`() {
        val rows = rowsOf(
            "com.samsung.shealth.sleep",
            "sleep_score,sleep_duration,efficiency,sleep_latency,physical_recovery,mental_recovery," +
                "total_rem_duration,total_light_duration,movement_awakening," +
                "com.samsung.health.sleep.start_time,com.samsung.health.sleep.end_time," +
                "com.samsung.health.sleep.time_offset,com.samsung.health.sleep.datauuid,",
            "71,401,86.0,1740000,93.0,82.0,77,168,45.0," +
                "2025-07-02 00:45:00.000,2025-07-02 07:26:00.000,UTC+0200,uuid-1,",
        )

        val night = SleepMapper.map(rows.single())!!

        assertEquals("uuid-1", night.id)
        assertEquals(LocalDate.of(2025, 7, 2), night.date)
        assertEquals(401, night.durationMinutes)
        assertEquals(71, night.score)
        assertEquals(86.0f, night.efficiencyPercent!!, 0.01f)
        assertEquals(29, night.latencyMinutes)
        assertEquals(93, night.physicalRecovery)
        assertEquals(82, night.mentalRecovery)
        assertEquals(77, night.remMinutes)
        assertEquals(168, night.lightMinutes)
    }

    @Test
    fun `attributes a night that starts after midnight to the wake up day`() {
        val night = SleepMapper.map(
            rowsOf(
                "com.samsung.shealth.sleep",
                "sleep_duration,com.samsung.health.sleep.start_time,com.samsung.health.sleep.end_time," +
                    "com.samsung.health.sleep.time_offset,com.samsung.health.sleep.datauuid,",
                "300,2026-03-10 01:30:00.000,2026-03-10 06:30:00.000,UTC+0100,uuid-2,",
            ).single()
        )!!

        assertEquals(LocalDate.of(2026, 3, 10), night.date)
    }

    @Test
    fun `attributes a night that starts before midnight to the next day`() {
        val night = SleepMapper.map(
            rowsOf(
                "com.samsung.shealth.sleep",
                "sleep_duration,com.samsung.health.sleep.start_time,com.samsung.health.sleep.end_time," +
                    "com.samsung.health.sleep.time_offset,com.samsung.health.sleep.datauuid,",
                "420,2026-03-09 23:10:00.000,2026-03-10 06:10:00.000,UTC+0100,uuid-3,",
            ).single()
        )!!

        assertEquals(LocalDate.of(2026, 3, 10), night.date)
    }

    @Test
    fun `discards a sleep row that carries no duration`() {
        val row = rowsOf(
            "com.samsung.shealth.sleep",
            "sleep_duration,com.samsung.health.sleep.start_time,com.samsung.health.sleep.end_time," +
                "com.samsung.health.sleep.time_offset,com.samsung.health.sleep.datauuid,",
            ",2026-03-09 23:10:00.000,2026-03-10 06:10:00.000,UTC+0100,uuid-4,",
        ).single()

        assertNull(SleepMapper.map(row))
    }

    // --- Stades de sommeil -----------------------------------------------

    @Test
    fun `decodes the four samsung sleep stage codes`() {
        val header = "start_time,end_time,stage,sleep_id,time_offset,datauuid,"
        val rows = rowsOf(
            "com.samsung.health.sleep_stage", header,
            "2025-07-02 00:45:00.000,2025-07-02 01:03:00.000,40001,night-1,UTC+0200,s1,",
            "2025-07-02 01:03:00.000,2025-07-02 01:14:00.000,40002,night-1,UTC+0200,s2,",
            "2025-07-02 01:14:00.000,2025-07-02 01:40:00.000,40003,night-1,UTC+0200,s3,",
            "2025-07-02 01:40:00.000,2025-07-02 02:10:00.000,40004,night-1,UTC+0200,s4,",
            "2025-07-02 02:10:00.000,2025-07-02 02:20:00.000,99999,night-1,UTC+0200,s5,",
        )

        val stages = rows.mapNotNull { SleepStageMapper.map(it) }

        assertEquals(
            listOf(SleepStage.AWAKE, SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM, SleepStage.UNKNOWN),
            stages.map { it.stage },
        )
        assertEquals(18, stages[0].durationMinutes)
        assertEquals("night-1", stages[0].sleepId)
    }

    // --- Pas ---------------------------------------------------------------

    @Test
    fun `keeps only the aggregated step source to avoid double counting`() {
        val header = "source_type,count,distance,calorie,speed,day_time,datauuid,"
        val rows = rowsOf(
            "com.samsung.shealth.step_daily_trend", header,
            "-2,9715,7099.38,396.27,1.34,2024-11-08 00:00:00.000,d1,",
            "0,3200,2100.00,120.00,1.10,2024-11-08 00:00:00.000,d2,",
            "108,6515,4999.38,276.27,1.40,2024-11-08 00:00:00.000,d3,",
        )

        val days = rows.mapNotNull { DailyStepsMapper.map(it) }

        assertEquals(1, days.size)
        assertEquals(9715, days.single().steps)
        assertEquals(7099.38f, days.single().distanceMeters!!, 0.01f)
        assertEquals(LocalDate.of(2024, 11, 8), days.single().date)
    }

    // --- Fréquence cardiaque ------------------------------------------------

    @Test
    fun `maps a heart rate sample`() {
        val row = rowsOf(
            "com.samsung.shealth.tracker.heart_rate",
            "com.samsung.health.heart_rate.heart_rate,com.samsung.health.heart_rate.min," +
                "com.samsung.health.heart_rate.max,com.samsung.health.heart_rate.start_time," +
                "com.samsung.health.heart_rate.time_offset,com.samsung.health.heart_rate.datauuid,",
            "77.0,,,2025-07-01 20:15:32.766,UTC+0200,hr-1,",
        ).single()

        val sample = HeartRateMapper.map(row)!!

        assertEquals(77, sample.beatsPerMinute)
        assertEquals("2025-07-01T18:15:32.766Z", sample.time.toString())
    }

    @Test
    fun `rejects a heart rate outside the physiological range`() {
        val header = "com.samsung.health.heart_rate.heart_rate," +
            "com.samsung.health.heart_rate.start_time," +
            "com.samsung.health.heart_rate.time_offset,com.samsung.health.heart_rate.datauuid,"

        assertNull(HeartRateMapper.map(rowsOf("com.samsung.shealth.tracker.heart_rate", header, "0.0,2025-07-01 20:15:32.766,UTC+0200,x,").single()))
        assertNull(HeartRateMapper.map(rowsOf("com.samsung.shealth.tracker.heart_rate", header, "320.0,2025-07-01 20:15:32.766,UTC+0200,y,").single()))
    }

    // --- Poids --------------------------------------------------------------

    @Test
    fun `maps a body composition measurement`() {
        val row = rowsOf(
            "com.samsung.health.weight",
            "weight,height,body_fat,body_fat_mass,skeletal_muscle_mass,fat_free_mass," +
                "basal_metabolic_rate,total_body_water,start_time,time_offset,datauuid,",
            "91.0,169.0,30.203556,27.485237,34.400124,63.514763,1741,46.563705," +
                "2025-07-01 20:49:34.277,UTC+0200,w-1,",
        ).single()

        val body = BodyCompositionMapper.map(row)!!

        assertEquals(91.0f, body.weightKg, 0.01f)
        assertEquals(30.2f, body.bodyFatPercent!!, 0.1f)
        assertEquals(1741, body.basalMetabolicRate)
        assertEquals(31.9f, body.bodyMassIndex!!, 0.1f)
    }

    @Test
    fun `computes no body mass index when the height is missing`() {
        val row = rowsOf(
            "com.samsung.health.weight",
            "weight,height,start_time,time_offset,datauuid,",
            "91.0,,2025-07-01 20:49:34.277,UTC+0200,w-2,",
        ).single()

        assertNull(BodyCompositionMapper.map(row)!!.bodyMassIndex)
    }

    // --- Exercice -----------------------------------------------------------

    @Test
    fun `maps an exercise session and decodes its kind`() {
        val row = rowsOf(
            "com.samsung.shealth.exercise",
            "com.samsung.health.exercise.exercise_type,com.samsung.health.exercise.duration," +
                "com.samsung.health.exercise.calorie,com.samsung.health.exercise.distance," +
                "com.samsung.health.exercise.mean_heart_rate,com.samsung.health.exercise.max_heart_rate," +
                "com.samsung.health.exercise.start_time,com.samsung.health.exercise.end_time," +
                "com.samsung.health.exercise.time_offset,com.samsung.health.exercise.datauuid,",
            "1001,642000,40.45,903.73,,,2024-11-08 16:07:18.000,2024-11-08 16:17:18.000,UTC+0100,e-1,",
        ).single()

        val session = ExerciseMapper.map(row)!!

        assertEquals(ExerciseKind.WALKING, session.kind)
        assertEquals(11, session.durationMinutes) // 642000 ms = 10,7 min
        assertEquals(40.45f, session.calories!!, 0.01f)
    }

    @Test
    fun `falls back to an other kind for an unknown exercise code`() {
        val row = rowsOf(
            "com.samsung.shealth.exercise",
            "com.samsung.health.exercise.exercise_type,com.samsung.health.exercise.duration," +
                "com.samsung.health.exercise.start_time,com.samsung.health.exercise.end_time," +
                "com.samsung.health.exercise.time_offset,com.samsung.health.exercise.datauuid,",
            "98765,600000,2024-11-08 16:07:18.000,2024-11-08 16:17:18.000,UTC+0100,e-2,",
        ).single()

        assertEquals(ExerciseKind.OTHER, ExerciseMapper.map(row)!!.kind)
    }

    // --- Stress, SpO2, score d'énergie ---------------------------------------

    @Test
    fun `maps an hourly stress score`() {
        val row = rowsOf(
            "com.samsung.shealth.stress",
            "score,max,min,start_time,end_time,time_offset,datauuid,",
            "19.0,44.0,0.0,2025-07-01 20:00:00.000,2025-07-01 20:59:59.999,UTC+0200,st-1,",
        ).single()

        val sample = StressMapper.map(row)!!

        assertEquals(19, sample.score)
        assertEquals(44, sample.max)
    }

    @Test
    fun `maps a daily energy score with its components`() {
        val row = rowsOf(
            "com.samsung.shealth.vitality_score",
            "total_score,sleep_score,activity_score,shr_value,shrv_value,sleep_duration," +
                "day_time,datauuid,",
            "77.65,72.23,88.10,57.28,66.51,24060000,2025-07-02 00:00:00.000,v-1,",
        ).single()

        val score = EnergyScoreMapper.map(row)!!

        assertEquals(78, score.total)
        assertEquals(72, score.sleep)
        assertEquals(88, score.activity)
        assertEquals(57, score.nightHeartRate)
        assertEquals(67, score.nightHeartRateVariability)
        assertEquals(LocalDate.of(2025, 7, 2), score.date)
    }

    @Test
    fun `maps an oxygen saturation reading`() {
        val row = rowsOf(
            "com.samsung.shealth.tracker.oxygen_saturation",
            "com.samsung.health.oxygen_saturation.spo2,com.samsung.health.oxygen_saturation.start_time," +
                "com.samsung.health.oxygen_saturation.time_offset,com.samsung.health.oxygen_saturation.datauuid,",
            "96.0,2025-07-02 03:00:00.000,UTC+0200,o-1,",
        ).single()

        assertEquals(96.0f, SpO2Mapper.map(row)!!.percent, 0.01f)
    }

    @Test
    fun `rejects an implausible oxygen saturation reading`() {
        val row = rowsOf(
            "com.samsung.shealth.tracker.oxygen_saturation",
            "com.samsung.health.oxygen_saturation.spo2,com.samsung.health.oxygen_saturation.start_time," +
                "com.samsung.health.oxygen_saturation.time_offset,com.samsung.health.oxygen_saturation.datauuid,",
            "12.0,2025-07-02 03:00:00.000,UTC+0200,o-2,",
        ).single()

        assertNull(SpO2Mapper.map(row))
    }

    @Test
    fun `maps a daily activity summary and converts active time to minutes`() {
        val row = rowsOf(
            "com.samsung.shealth.activity.day_summary",
            "step_count,active_time,calorie,distance,floor_count,exercise_time,day_time,datauuid,",
            "9715,5291711,396.27,7099.38,3,600000,2024-11-08 00:00:00.000,a-1,",
        ).single()

        val summary = DailyActivityMapper.map(row)!!

        assertEquals(88, summary.activeMinutes)
        assertEquals(396, summary.activeCalories)
        assertTrue(summary.floors == 3)
    }
}
