package com.kmt.healthanalyzer.data.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord

/**
 * Ensemble FIGÉ des permissions de LECTURE Health Connect demandées par l'app.
 *
 * Chaque entrée correspond à une permission `android.permission.health.READ_*` déjà déclarée
 * dans `AndroidManifest.xml` : les deux listes doivent rester cohérentes. L'app est en LECTURE
 * SEULE, aucune permission d'écriture n'est demandée ni ne doit l'être.
 *
 * Tous les types de records ci-dessous sont présents dans `connect-client:1.1.0-alpha08`.
 */
object HealthConnectPermissions {

    val READ_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(SkinTemperatureRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
    )

    /**
     * Permission spéciale qui étend la lecture au-delà des 30 jours précédant l'octroi des
     * permissions. Absente de `connect-client:1.1.0-alpha08` comme constante Kotlin publique
     * (contrairement à [HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND]) : la
     * constante `HealthPermission.PERMISSION_PREFIX` existe mais est `internal` à la librairie,
     * donc inutilisable depuis l'app. La valeur est donc écrite en dur ci-dessous ; elle
     * correspond exactement à la permission `android.permission.health.READ_HEALTH_DATA_HISTORY`
     * déclarée dans `AndroidManifest.xml`. C'est une permission de LECTURE : elle n'élargit rien
     * côté écriture.
     */
    const val READ_HEALTH_DATA_HISTORY: String = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    /** Ensemble complet demandé à l'utilisateur lors de l'octroi : les données, plus l'historique. */
    val REQUESTED_PERMISSIONS: Set<String> = READ_PERMISSIONS + READ_HEALTH_DATA_HISTORY
}
