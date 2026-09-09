package com.kmt.healthanalyzer.data.db

import androidx.room.TypeConverter
import com.kmt.healthanalyzer.data.db.entity.ChatRole
import com.kmt.healthanalyzer.domain.model.DataOrigin
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.SleepStage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Convertisseurs de type Room pour la persistance des modèles de domaine.
 *
 * Règles de stockage :
 * - [Instant] est stocké en `Long` (epoch millis).
 * - [LocalDate] est stocké en `String` au format ISO-8601 (ex. "2026-03-01").
 * - [LocalTime] est stocké en `String` au format ISO-8601 (ex. "23:15:00").
 * - Les énumérations sont stockées en `String`, avec le nom de la constante (`name`).
 */
class Converters {

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToIsoString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun isoStringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToIsoString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun isoStringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun dataOriginToName(value: DataOrigin?): String? = value?.name

    @TypeConverter
    fun nameToDataOrigin(value: String?): DataOrigin? = value?.let(DataOrigin::valueOf)

    @TypeConverter
    fun sleepStageToName(value: SleepStage?): String? = value?.name

    @TypeConverter
    fun nameToSleepStage(value: String?): SleepStage? = value?.let(SleepStage::valueOf)

    @TypeConverter
    fun exerciseKindToName(value: ExerciseKind?): String? = value?.name

    @TypeConverter
    fun nameToExerciseKind(value: String?): ExerciseKind? = value?.let(ExerciseKind::valueOf)

    @TypeConverter
    fun chatRoleToName(value: ChatRole?): String? = value?.name

    @TypeConverter
    fun nameToChatRole(value: String?): ChatRole? = value?.let(ChatRole::valueOf)
}
