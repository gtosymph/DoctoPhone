package com.kmt.healthanalyzer.data.healthconnect

/**
 * Photographie des permissions Health Connect réellement accordées, prise juste avant une
 * synchronisation.
 *
 * Les méthodes `read*` de [HealthConnectReader] avalent un refus de permission en rendant une
 * liste vide, pour ne jamais faire échouer une lecture à cause d'un type de donnée non autorisé.
 * Cette photographie est ce qui permet à l'appelant ([com.kmt.healthanalyzer.data.repository.HealthRepository])
 * de distinguer malgré tout un refus d'une simple absence de donnée.
 */
data class HealthConnectPermissionState(
    val grantedDataPermissions: Set<String>,
    val hasHistoryPermission: Boolean,
) {
    /** Vrai si au moins une permission de type de donnée (pas, sommeil, ...) est accordée. */
    val hasAnyDataPermission: Boolean get() = grantedDataPermissions.isNotEmpty()

    /** Permissions de type de donnée demandées par l'app mais refusées par l'utilisateur. */
    val missingDataPermissions: Set<String>
        get() = HealthConnectPermissions.READ_PERMISSIONS - grantedDataPermissions
}
