package com.kmt.healthanalyzer.data.work

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde contre un plantage réel, vécu sur l'appareil de l'utilisateur.
 *
 * La synchronisation de nuit demandait à la fois le mode « appareil au repos »
 * (`setRequiresDeviceIdle(true)`) et une politique de reprise (`setBackoffCriteria`). Android
 * interdit cette combinaison, mais il ne le dit ni à la compilation ni aux tests : il la refuse
 * au moment de mettre la tâche en file.
 *
 *     java.lang.IllegalArgumentException: Cannot set backoff criteria on an idle mode job
 *
 * Comme la programmation part du démarrage de l'app, l'exception fermait l'app à l'ouverture,
 * avant tout écran. Aucun des 448 tests d'alors ne l'a vue, et pour une raison qui mérite
 * d'être écrite : ils ne lancent pas l'app. Une suite verte et une compilation réussie ne
 * disent rien du démarrage.
 *
 * Ce test ne remplace pas un lancement réel — voir `CLAUDE.md`, qui l'exige désormais avant
 * toute publication. Il empêche seulement la contrainte fautive de revenir par distraction.
 */
class SyncConstraintsTest {

    @Test
    fun `la synchronisation ne demande pas le mode repos, incompatible avec la reprise`() {
        val constraints = HealthAnalyzerWorkScheduler(mockk<Context>(relaxed = true)).syncConstraints()

        assertFalse(
            "requiresDeviceIdle est de retour sur la synchronisation. Combiné à " +
                "setBackoffCriteria, Android refuse la tâche à la mise en file avec " +
                "« Cannot set backoff criteria on an idle mode job », et l'app se ferme à " +
                "l'ouverture puisque la programmation part du démarrage.",
            constraints.requiresDeviceIdle(),
        )
    }

    @Test
    fun `elle garde la contrainte de batterie, qui n'a jamais posé de problème`() {
        val constraints = HealthAnalyzerWorkScheduler(mockk<Context>(relaxed = true)).syncConstraints()

        assertTrue(
            "La contrainte de batterie faible protège l'utilisateur d'une synchronisation " +
                "au pire moment ; elle est compatible avec la reprise et doit rester.",
            constraints.requiresBatteryNotLow(),
        )
    }
}
