package com.kmt.healthanalyzer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kmt.healthanalyzer.data.db.dao.BloodPressureDao
import com.kmt.healthanalyzer.data.db.dao.BodyCompositionDao
import com.kmt.healthanalyzer.data.db.dao.ChatMessageDao
import com.kmt.healthanalyzer.data.db.dao.DailyActivityDao
import com.kmt.healthanalyzer.data.db.dao.DailyFloorsDao
import com.kmt.healthanalyzer.data.db.dao.DailyStepsDao
import com.kmt.healthanalyzer.data.db.dao.EcgRecordDao
import com.kmt.healthanalyzer.data.db.dao.EnergyScoreDao
import com.kmt.healthanalyzer.data.db.dao.ExerciseSessionDao
import com.kmt.healthanalyzer.data.db.dao.HeartRateSampleDao
import com.kmt.healthanalyzer.data.db.dao.HrvSampleDao
import com.kmt.healthanalyzer.data.db.dao.RespiratoryRateDao
import com.kmt.healthanalyzer.data.db.dao.SkinTemperatureDao
import com.kmt.healthanalyzer.data.db.dao.SleepApneaDao
import com.kmt.healthanalyzer.data.db.dao.SleepNightDao
import com.kmt.healthanalyzer.data.db.dao.SleepStageSegmentDao
import com.kmt.healthanalyzer.data.db.dao.SnoringEpisodeDao
import com.kmt.healthanalyzer.data.db.dao.SpO2SampleDao
import com.kmt.healthanalyzer.data.db.dao.StressAlertDao
import com.kmt.healthanalyzer.data.db.dao.StressSampleDao
import com.kmt.healthanalyzer.data.db.entity.BloodPressureEntity
import com.kmt.healthanalyzer.data.db.entity.BodyCompositionEntity
import com.kmt.healthanalyzer.data.db.entity.ChatMessageEntity
import com.kmt.healthanalyzer.data.db.entity.DailyActivityEntity
import com.kmt.healthanalyzer.data.db.entity.DailyFloorsEntity
import com.kmt.healthanalyzer.data.db.entity.DailyStepsEntity
import com.kmt.healthanalyzer.data.db.entity.EcgRecordEntity
import com.kmt.healthanalyzer.data.db.entity.EnergyScoreEntity
import com.kmt.healthanalyzer.data.db.entity.ExerciseSessionEntity
import com.kmt.healthanalyzer.data.db.entity.HeartRateSampleEntity
import com.kmt.healthanalyzer.data.db.entity.HrvSampleEntity
import com.kmt.healthanalyzer.data.db.entity.RespiratoryRateEntity
import com.kmt.healthanalyzer.data.db.entity.SkinTemperatureEntity
import com.kmt.healthanalyzer.data.db.entity.SleepApneaEntity
import com.kmt.healthanalyzer.data.db.entity.SleepNightEntity
import com.kmt.healthanalyzer.data.db.entity.SleepStageSegmentEntity
import com.kmt.healthanalyzer.data.db.entity.SnoringEpisodeEntity
import com.kmt.healthanalyzer.data.db.entity.SpO2SampleEntity
import com.kmt.healthanalyzer.data.db.entity.StressAlertEntity
import com.kmt.healthanalyzer.data.db.entity.StressSampleEntity

/** Base de données Room de l'application, source de vérité locale pour toutes les données de santé importées. */
@Database(
    entities = [
        SleepNightEntity::class,
        SleepStageSegmentEntity::class,
        HeartRateSampleEntity::class,
        StressSampleEntity::class,
        HrvSampleEntity::class,
        SpO2SampleEntity::class,
        BodyCompositionEntity::class,
        ExerciseSessionEntity::class,
        DailyStepsEntity::class,
        DailyActivityEntity::class,
        EnergyScoreEntity::class,
        BloodPressureEntity::class,
        EcgRecordEntity::class,
        SnoringEpisodeEntity::class,
        RespiratoryRateEntity::class,
        SkinTemperatureEntity::class,
        SleepApneaEntity::class,
        DailyFloorsEntity::class,
        StressAlertEntity::class,
        ChatMessageEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HealthDatabase : RoomDatabase() {

    abstract fun sleepNightDao(): SleepNightDao
    abstract fun sleepStageSegmentDao(): SleepStageSegmentDao
    abstract fun heartRateSampleDao(): HeartRateSampleDao
    abstract fun stressSampleDao(): StressSampleDao
    abstract fun hrvSampleDao(): HrvSampleDao
    abstract fun spO2SampleDao(): SpO2SampleDao
    abstract fun bodyCompositionDao(): BodyCompositionDao
    abstract fun exerciseSessionDao(): ExerciseSessionDao
    abstract fun dailyStepsDao(): DailyStepsDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun energyScoreDao(): EnergyScoreDao
    abstract fun bloodPressureDao(): BloodPressureDao
    abstract fun ecgRecordDao(): EcgRecordDao
    abstract fun snoringEpisodeDao(): SnoringEpisodeDao
    abstract fun respiratoryRateDao(): RespiratoryRateDao
    abstract fun skinTemperatureDao(): SkinTemperatureDao
    abstract fun sleepApneaDao(): SleepApneaDao
    abstract fun dailyFloorsDao(): DailyFloorsDao
    abstract fun stressAlertDao(): StressAlertDao
    abstract fun chatMessageDao(): ChatMessageDao
}
