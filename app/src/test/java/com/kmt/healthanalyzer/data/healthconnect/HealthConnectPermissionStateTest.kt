package com.kmt.healthanalyzer.data.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste [HealthConnectPermissionState], qui sert à distinguer un refus de permission d'une
 * simple absence de donnée (voir le CLAUDE.md du projet, section « READ_HEALTH_DATA_HISTORY »).
 */
class HealthConnectPermissionStateTest {

    @Test
    fun `hasAnyDataPermission est vrai des qu'une seule permission de donnee est accordee`() {
        val state = HealthConnectPermissionState(
            grantedDataPermissions = setOf(HealthPermissionForTest.READ_STEPS),
            hasHistoryPermission = false,
        )

        assertTrue(state.hasAnyDataPermission)
    }

    @Test
    fun `hasAnyDataPermission est faux quand aucune permission de donnee n'est accordee`() {
        val state = HealthConnectPermissionState(grantedDataPermissions = emptySet(), hasHistoryPermission = false)

        assertFalse(state.hasAnyDataPermission)
    }

    @Test
    fun `missingDataPermissions rend les permissions demandees mais non accordees`() {
        val granted = HealthConnectPermissions.READ_PERMISSIONS - HealthPermissionForTest.READ_SLEEP
        val state = HealthConnectPermissionState(grantedDataPermissions = granted, hasHistoryPermission = true)

        assertEquals(setOf(HealthPermissionForTest.READ_SLEEP), state.missingDataPermissions)
    }

    @Test
    fun `missingDataPermissions est vide quand tout est accorde`() {
        val state = HealthConnectPermissionState(
            grantedDataPermissions = HealthConnectPermissions.READ_PERMISSIONS,
            hasHistoryPermission = true,
        )

        assertTrue(state.missingDataPermissions.isEmpty())
    }
}

/** Repères de permissions Health Connect utilisés par les tests, sans dépendance Android. */
private object HealthPermissionForTest {
    const val READ_STEPS = "android.permission.health.READ_STEPS"
    const val READ_SLEEP = "android.permission.health.READ_SLEEP"
}
