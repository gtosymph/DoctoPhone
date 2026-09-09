package com.kmt.healthanalyzer.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 1 -> 2 : ajoute les 8 tables des mesures cliniques ponctuelles
 * (tension, ECG, ronflement, respiration, température cutanée, apnée, étages, stress).
 *
 * L'utilisateur a déjà des dizaines de milliers de mesures importées ; cette migration
 * ne touche à aucune table existante, elle ne fait qu'ajouter les nouvelles.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `blood_pressure_readings` (`id` TEXT NOT NULL, " +
                "`timeEpochMillis` INTEGER NOT NULL, `systolic` INTEGER NOT NULL, " +
                "`diastolic` INTEGER NOT NULL, `pulse` INTEGER, `mean` INTEGER, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_blood_pressure_readings_timeEpochMillis` " +
                "ON `blood_pressure_readings` (`timeEpochMillis`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ecg_records` (`id` TEXT NOT NULL, " +
                "`timeEpochMillis` INTEGER NOT NULL, `meanHeartRate` INTEGER, " +
                "`minHeartRate` INTEGER, `maxHeartRate` INTEGER, `classification` INTEGER, " +
                "`symptoms` TEXT, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ecg_records_timeEpochMillis` " +
                "ON `ecg_records` (`timeEpochMillis`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `snoring_episodes` (`id` TEXT NOT NULL, " +
                "`startEpochMillis` INTEGER NOT NULL, `endEpochMillis` INTEGER NOT NULL, " +
                "`durationMinutes` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_snoring_episodes_startEpochMillis` " +
                "ON `snoring_episodes` (`startEpochMillis`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `respiratory_rate_samples` (`id` TEXT NOT NULL, " +
                "`timeEpochMillis` INTEGER NOT NULL, `breathsPerMinute` REAL NOT NULL, " +
                "`min` REAL, `max` REAL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_respiratory_rate_samples_timeEpochMillis` " +
                "ON `respiratory_rate_samples` (`timeEpochMillis`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `skin_temperature_samples` (`id` TEXT NOT NULL, " +
                "`timeEpochMillis` INTEGER NOT NULL, `celsius` REAL NOT NULL, " +
                "`baseline` REAL, `min` REAL, `max` REAL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_skin_temperature_samples_timeEpochMillis` " +
                "ON `skin_temperature_samples` (`timeEpochMillis`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sleep_apnea_results` (`id` TEXT NOT NULL, " +
                "`timeEpochMillis` INTEGER NOT NULL, `result` INTEGER NOT NULL, " +
                "`averageBreathingDisturbance` REAL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sleep_apnea_results_timeEpochMillis` " +
                "ON `sleep_apnea_results` (`timeEpochMillis`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_floors` (`date` TEXT NOT NULL, " +
                "`floors` INTEGER NOT NULL, PRIMARY KEY(`date`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stress_alerts` (`id` TEXT NOT NULL, " +
                "`startEpochMillis` INTEGER NOT NULL, `endEpochMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stress_alerts_startEpochMillis` " +
                "ON `stress_alerts` (`startEpochMillis`)"
        )
    }
}

/**
 * Migration 2 -> 3 : ajoute la table de la conversation de l'onglet Analyse.
 *
 * Ne touche à aucune table existante ; l'utilisateur a 52 260 mesures en base et cette
 * migration ne fait qu'ajouter la nouvelle table `chat_messages`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`role` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chat_messages_createdAtEpochMillis` " +
                "ON `chat_messages` (`createdAtEpochMillis`)"
        )
    }
}

/**
 * Migration 3 -> 4 : ajoute à `chat_messages` la fenêtre que le modèle examinait quand il
 * a écrit chaque message (voir [com.kmt.healthanalyzer.data.db.entity.ChatMessageEntity]).
 *
 * Deux colonnes nullables, sans valeur par défaut à calculer : les messages déjà en base
 * n'avaient pas de fenêtre associée avant ce mécanisme, `NULL` décrit très exactement leur
 * état — un graphique déjà affiché continue de se résoudre sur tout l'historique, comme
 * avant cette migration. Ne touche à aucune table ni ligne existante.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `rangeFrom` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `rangeTo` TEXT DEFAULT NULL")
    }
}
