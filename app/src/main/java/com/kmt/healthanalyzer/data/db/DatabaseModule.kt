package com.kmt.healthanalyzer.data.db

import android.content.Context
import androidx.room.Room
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Nom du fichier de base de données SQLite sur le disque. */
private const val DATABASE_NAME = "health_analyzer.db"

/** Module Hilt fournissant la [HealthDatabase] et ses DAO en singletons applicatifs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHealthDatabase(@ApplicationContext context: Context): HealthDatabase =
        Room.databaseBuilder(context, HealthDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun provideSleepNightDao(database: HealthDatabase): SleepNightDao = database.sleepNightDao()

    @Provides
    fun provideSleepStageSegmentDao(database: HealthDatabase): SleepStageSegmentDao =
        database.sleepStageSegmentDao()

    @Provides
    fun provideHeartRateSampleDao(database: HealthDatabase): HeartRateSampleDao =
        database.heartRateSampleDao()

    @Provides
    fun provideStressSampleDao(database: HealthDatabase): StressSampleDao = database.stressSampleDao()

    @Provides
    fun provideHrvSampleDao(database: HealthDatabase): HrvSampleDao = database.hrvSampleDao()

    @Provides
    fun provideSpO2SampleDao(database: HealthDatabase): SpO2SampleDao = database.spO2SampleDao()

    @Provides
    fun provideBodyCompositionDao(database: HealthDatabase): BodyCompositionDao =
        database.bodyCompositionDao()

    @Provides
    fun provideExerciseSessionDao(database: HealthDatabase): ExerciseSessionDao =
        database.exerciseSessionDao()

    @Provides
    fun provideDailyStepsDao(database: HealthDatabase): DailyStepsDao = database.dailyStepsDao()

    @Provides
    fun provideDailyActivityDao(database: HealthDatabase): DailyActivityDao =
        database.dailyActivityDao()

    @Provides
    fun provideEnergyScoreDao(database: HealthDatabase): EnergyScoreDao = database.energyScoreDao()

    @Provides
    fun provideBloodPressureDao(database: HealthDatabase): BloodPressureDao =
        database.bloodPressureDao()

    @Provides
    fun provideEcgRecordDao(database: HealthDatabase): EcgRecordDao = database.ecgRecordDao()

    @Provides
    fun provideSnoringEpisodeDao(database: HealthDatabase): SnoringEpisodeDao =
        database.snoringEpisodeDao()

    @Provides
    fun provideRespiratoryRateDao(database: HealthDatabase): RespiratoryRateDao =
        database.respiratoryRateDao()

    @Provides
    fun provideSkinTemperatureDao(database: HealthDatabase): SkinTemperatureDao =
        database.skinTemperatureDao()

    @Provides
    fun provideSleepApneaDao(database: HealthDatabase): SleepApneaDao = database.sleepApneaDao()

    @Provides
    fun provideDailyFloorsDao(database: HealthDatabase): DailyFloorsDao =
        database.dailyFloorsDao()

    @Provides
    fun provideStressAlertDao(database: HealthDatabase): StressAlertDao =
        database.stressAlertDao()

    @Provides
    fun provideChatMessageDao(database: HealthDatabase): ChatMessageDao =
        database.chatMessageDao()
}
