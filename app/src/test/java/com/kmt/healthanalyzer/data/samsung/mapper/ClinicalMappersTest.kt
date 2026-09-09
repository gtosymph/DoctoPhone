package com.kmt.healthanalyzer.data.samsung.mapper

import com.kmt.healthanalyzer.data.samsung.csv.SamsungCsvReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Fixtures synthétiques : aucune valeur ne provient d'un export réel. */
class ClinicalMappersTest {

    private val reader = SamsungCsvReader()

    private fun rowsOf(dataType: String, header: String, vararg lines: String) =
        reader.read(
            (listOf("$dataType,7006011,1", header) + lines).joinToString("\n").byteInputStream()
        ).rows

    // --- Tension artérielle -------------------------------------------------

    @Test
    fun `maps a blood pressure reading with its prefixed columns`() {
        val prefix = "com.samsung.health.blood_pressure."
        val header = "${prefix}systolic,${prefix}diastolic,${prefix}pulse,${prefix}mean," +
            "${prefix}start_time,${prefix}time_offset,${prefix}datauuid,"
        val row = rowsOf(
            "com.samsung.shealth.blood_pressure", header,
            "128,82,70,97,2026-03-01 08:15:00.000,UTC+0100,bp-1,",
        ).single()

        val reading = BloodPressureMapper.map(row)!!

        assertEquals("bp-1", reading.id)
        assertEquals(128, reading.systolic)
        assertEquals(82, reading.diastolic)
        assertEquals(70, reading.pulse)
        assertEquals(97, reading.mean)
    }

    @Test
    fun `rejects an implausible systolic reading`() {
        val prefix = "com.samsung.health.blood_pressure."
        val header = "${prefix}systolic,${prefix}diastolic,${prefix}start_time," +
            "${prefix}time_offset,${prefix}datauuid,"
        val row = rowsOf(
            "com.samsung.shealth.blood_pressure", header,
            "300,82,2026-03-01 08:15:00.000,UTC+0100,bp-2,",
        ).single()

        assertNull(BloodPressureMapper.map(row))
    }

    @Test
    fun `rejects an implausible diastolic reading`() {
        val prefix = "com.samsung.health.blood_pressure."
        val header = "${prefix}systolic,${prefix}diastolic,${prefix}start_time," +
            "${prefix}time_offset,${prefix}datauuid,"
        val row = rowsOf(
            "com.samsung.shealth.blood_pressure", header,
            "120,20,2026-03-01 08:15:00.000,UTC+0100,bp-3,",
        ).single()

        assertNull(BloodPressureMapper.map(row))
    }

    @Test
    fun `discards a blood pressure row without an id`() {
        val prefix = "com.samsung.health.blood_pressure."
        val header = "${prefix}systolic,${prefix}diastolic,${prefix}start_time,${prefix}time_offset,"
        val row = rowsOf(
            "com.samsung.shealth.blood_pressure", header,
            "120,80,2026-03-01 08:15:00.000,UTC+0100,",
        ).single()

        assertNull(BloodPressureMapper.map(row))
    }

    // --- ECG -----------------------------------------------------------------

    @Test
    fun `maps an ecg record with unprefixed columns`() {
        val header = "start_time,mean_heart_rate,min_heart_rate,max_heart_rate,classification," +
            "symptoms,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.health.ecg", header,
            "2026-03-01 08:00:00.000,82.0,78.0,88.0,1,0,UTC+0100,ecg-1,",
        ).single()

        val record = EcgMapper.map(row)!!

        assertEquals("ecg-1", record.id)
        assertEquals(82, record.meanHeartRate)
        assertEquals(78, record.minHeartRate)
        assertEquals(88, record.maxHeartRate)
        assertEquals(1, record.classification)
        assertEquals("0", record.symptoms)
    }

    @Test
    fun `discards an ecg row without a start time`() {
        val header = "mean_heart_rate,time_offset,datauuid,"
        val row = rowsOf("com.samsung.health.ecg", header, "82.0,UTC+0100,ecg-2,").single()

        assertNull(EcgMapper.map(row))
    }

    // --- Ronflement ------------------------------------------------------------

    @Test
    fun `maps a snoring episode from its duration column`() {
        val header = "start_time,end_time,duration,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.shealth.sleep_snoring", header,
            "2026-03-01 01:00:00.000,2026-03-01 01:02:00.000,120000,UTC+0100,sn-1,",
        ).single()

        val episode = SnoringMapper.map(row)!!

        assertEquals("sn-1", episode.id)
        assertEquals(2, episode.durationMinutes)
    }

    @Test
    fun `falls back to start and end when duration is missing`() {
        val header = "start_time,end_time,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.shealth.sleep_snoring", header,
            "2026-03-01 01:00:00.000,2026-03-01 01:05:00.000,UTC+0100,sn-2,",
        ).single()

        val episode = SnoringMapper.map(row)!!

        assertEquals(5, episode.durationMinutes)
    }

    @Test
    fun `discards a snoring row without an end time`() {
        val header = "start_time,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.shealth.sleep_snoring", header,
            "2026-03-01 01:00:00.000,UTC+0100,sn-3,",
        ).single()

        assertNull(SnoringMapper.map(row))
    }

    // --- Fréquence respiratoire --------------------------------------------

    @Test
    fun `maps a respiratory rate sample`() {
        val header = "start_time,average,lower_limit,upper_limit,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.health.respiratory_rate", header,
            "2026-03-01 02:00:00.000,14.5,11.0,18.0,UTC+0100,rr-1,",
        ).single()

        val sample = RespiratoryRateMapper.map(row)!!

        assertEquals(14.5f, sample.breathsPerMinute, 0.01f)
        assertEquals(11.0f, sample.min!!, 0.01f)
        assertEquals(18.0f, sample.max!!, 0.01f)
    }

    @Test
    fun `rejects an implausible respiratory rate`() {
        val header = "start_time,average,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.health.respiratory_rate", header,
            "2026-03-01 02:00:00.000,55.0,UTC+0100,rr-2,",
        ).single()

        assertNull(RespiratoryRateMapper.map(row))
    }

    // --- Température cutanée -------------------------------------------------

    @Test
    fun `maps a skin temperature sample`() {
        val header = "start_time,temperature,baseline,min,max,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.health.skin_temperature", header,
            "2026-03-01 03:00:00.000,33.2,33.0,32.5,33.8,UTC+0100,st-1,",
        ).single()

        val sample = SkinTemperatureMapper.map(row)!!

        assertEquals(33.2f, sample.celsius, 0.01f)
        assertEquals(33.0f, sample.baseline!!, 0.01f)
    }

    @Test
    fun `rejects an implausible skin temperature`() {
        val header = "start_time,temperature,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.health.skin_temperature", header,
            "2026-03-01 03:00:00.000,10.0,UTC+0100,st-2,",
        ).single()

        assertNull(SkinTemperatureMapper.map(row))
    }

    // --- Apnée du sommeil ------------------------------------------------------

    @Test
    fun `maps a sleep apnea result`() {
        val header = "start_time,result,average_bd,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.health.sleep_apnea", header,
            "2026-03-01 00:00:00.000,1,12.5,UTC+0100,ap-1,",
        ).single()

        val result = SleepApneaMapper.map(row)!!

        assertEquals(1, result.result)
        assertEquals(12.5f, result.averageBreathingDisturbance!!, 0.01f)
    }

    @Test
    fun `discards a sleep apnea row without a result`() {
        val header = "start_time,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.health.sleep_apnea", header,
            "2026-03-01 00:00:00.000,UTC+0100,ap-2,",
        ).single()

        assertNull(SleepApneaMapper.map(row))
    }

    // --- Étages ----------------------------------------------------------------

    @Test
    fun `maps a day of floors climbed`() {
        val header = "day_time,floor_count,datauuid,"
        val row = rowsOf(
            "com.samsung.shealth.tracker.floors_day_summary", header,
            "2026-03-01 00:00:00.000,9,fl-1,",
        ).single()

        val floors = DailyFloorsMapper.map(row)!!

        assertEquals(LocalDate.of(2026, 3, 1), floors.date)
        assertEquals(9, floors.floors)
    }

    @Test
    fun `discards a floors row without a floor count`() {
        val header = "day_time,datauuid,"
        val row = rowsOf(
            "com.samsung.shealth.tracker.floors_day_summary", header,
            "2026-03-01 00:00:00.000,fl-2,",
        ).single()

        assertNull(DailyFloorsMapper.map(row))
    }

    // --- Alertes de stress -------------------------------------------------

    @Test
    fun `maps a stress alert`() {
        val header = "start_time,end_time,time_offset,datauuid,"
        val row = rowsOf(
            "com.samsung.shealth.alerted_stress", header,
            "2026-03-01 10:00:00.000,2026-03-01 10:12:00.000,UTC+0100,al-1,",
        ).single()

        val alert = StressAlertMapper.map(row)!!

        assertEquals("al-1", alert.id)
    }

    @Test
    fun `discards a stress alert row without an id`() {
        val header = "start_time,end_time,time_offset,"
        val row = rowsOf(
            "com.samsung.shealth.alerted_stress", header,
            "2026-03-01 10:00:00.000,2026-03-01 10:12:00.000,UTC+0100,",
        ).single()

        assertNull(StressAlertMapper.map(row))
    }
}
