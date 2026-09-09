package com.kmt.healthanalyzer.data.repository

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val SYNC_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

/** Fenêtre de lecture Health Connect sans permission d'historique, en jours. */
const val HEALTH_CONNECT_HISTORY_WINDOW_DAYS = 30L

/**
 * Vrai si [from] demande des données plus anciennes que la fenêtre de 30 jours que Health
 * Connect rend accessibles sans
 * [com.kmt.healthanalyzer.data.healthconnect.HealthConnectPermissions.READ_HEALTH_DATA_HISTORY].
 *
 * La fenêtre réelle part de la date d'octroi de la permission, pas d'aujourd'hui : l'app ne
 * conserve pas cette date, donc [now] (l'instant présent par défaut) sert d'approximation. Un
 * peu optimiste juste après un octroi récent, elle devient exacte au fil du temps.
 */
fun isHistoryPermissionMissing(hasHistoryPermission: Boolean, from: Instant, now: Instant = Instant.now()): Boolean =
    !hasHistoryPermission && from.isBefore(now.minus(HEALTH_CONNECT_HISTORY_WINDOW_DAYS, ChronoUnit.DAYS))

/**
 * Résultat d'une synchronisation Health Connect ([HealthRepository.syncFromHealthConnect]).
 *
 * Une lecture Health Connect qui échoue est avalée en liste vide par [com.kmt.healthanalyzer.data.healthconnect.HealthConnectReader] :
 * ce résultat porte donc, en plus des dates effectivement reçues, ce qu'une liste vide ne dit
 * pas, pour distinguer trois situations qui se ressemblent toutes à l'écran : aucune permission
 * accordée, historique au-delà de 30 jours inaccessible faute de permission dédiée, ou données
 * réellement absentes sur la période demandée.
 */
data class HealthConnectSyncResult(
    /** Faux si l'utilisateur n'a accordé aucune permission de lecture de donnée de santé. */
    val hasAnyDataPermission: Boolean,
    /** Permissions de type de donnée demandées par l'app mais refusées par l'utilisateur. */
    val missingDataPermissions: Set<String>,
    /**
     * Vrai si la synchronisation demandait des données antérieures à la fenêtre de 30 jours et
     * que la permission d'historique n'est pas accordée : ces données anciennes n'ont pas pu
     * être lues, même si les permissions de type de donnée sont, elles, présentes.
     */
    val historyPermissionMissing: Boolean,
    /** Date la plus ancienne effectivement reçue de Health Connect lors de cette synchronisation. */
    val earliestDate: LocalDate?,
    /** Date la plus récente effectivement reçue de Health Connect lors de cette synchronisation. */
    val latestDate: LocalDate?,
) {
    /** Vrai si l'un des trois problèmes ci-dessus mérite d'attirer l'œil de l'utilisateur. */
    val hasIssue: Boolean
        get() = !hasAnyDataPermission || historyPermissionMissing || missingDataPermissions.isNotEmpty()

    /** Message destiné à l'utilisateur : quelle situation s'applique, et quoi faire. */
    fun toUserMessage(): String {
        if (!hasAnyDataPermission) {
            return "Aucune permission Health Connect n'est accordée. Appuyez sur « Autoriser " +
                "l'accès » puis choisissez au moins une donnée à synchroniser."
        }

        val rangeMessage = if (earliestDate == null || latestDate == null) {
            "Health Connect n'a rendu aucune donnée sur la période demandée."
        } else {
            "Données disponibles du ${earliestDate.format(SYNC_DATE_FORMAT)} au " +
                "${latestDate.format(SYNC_DATE_FORMAT)}."
        }

        val historyWarning = if (historyPermissionMissing) {
            " L'historique au-delà de 30 jours n'est pas accessible : la permission d'historique " +
                "Health Connect manque. Réautorisez l'app en cochant toutes les permissions demandées."
        } else {
            ""
        }

        val missingTypesWarning = if (missingDataPermissions.isNotEmpty()) {
            " Certaines permissions de type de donnée n'ont pas été accordées."
        } else {
            ""
        }

        return rangeMessage + historyWarning + missingTypesWarning
    }
}
