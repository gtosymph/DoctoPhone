package com.kmt.healthanalyzer.domain.usecase

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HealthRangeRequest.parse] lit le bloc `healthrange` écrit par le modèle — exact
 * symétrique de `chart-spec.js` pour `healthchart` (voir CLAUDE.md, esprit indulgent :
 * corrige ce qui se corrige, refuse le reste avec un message lisible).
 */
class HealthRangeRequestTest {

    private val overview = HistoryOverview(
        earliest = "2024-06-01",
        latest = "2025-08-31",
        months = listOf(
            MonthCoverage("2024-06", 20),
            MonthCoverage("2024-07", 25),
            // Août 2024 : aucune mesure, un trou dans l'historique.
            MonthCoverage("2024-09", 10),
            MonthCoverage("2025-01", 28),
            MonthCoverage("2025-08", 5),
        ),
    )

    @Test
    fun `sans bloc healthrange, rien a faire`() {
        val outcome = HealthRangeRequest.parse("Votre sommeil s'améliore, sans plus de détail.", overview)

        assertEquals(HealthRangeOutcome.NotFound, outcome)
    }

    @Test
    fun `une fenetre valide et dans l'historique est acceptee telle quelle`() {
        val outcome = HealthRangeRequest.parse(
            reply = """Je vais regarder. ```healthrange
                |{ "from": "2025-01-01", "to": "2025-01-31", "why": "tendance de janvier" }
                |```
            """.trimMargin(),
            overview = overview,
        )

        val accepted = outcome as HealthRangeOutcome.Accepted
        assertEquals(LocalDate.of(2025, 1, 1), accepted.from)
        assertEquals(LocalDate.of(2025, 1, 31), accepted.to)
        assertNull(accepted.note)
    }

    @Test
    fun `des bornes inversees sont remises dans l'ordre, avec une note`() {
        val outcome = HealthRangeRequest.parse(
            "```healthrange\n{ \"from\": \"2025-01-31\", \"to\": \"2025-01-01\" }\n```",
            overview,
        )

        val accepted = outcome as HealthRangeOutcome.Accepted
        assertEquals(LocalDate.of(2025, 1, 1), accepted.from)
        assertEquals(LocalDate.of(2025, 1, 31), accepted.to)
        assertTrue(accepted.note!!.contains("inversées"))
    }

    @Test
    fun `une date au format francais est toleree et normalisee`() {
        val outcome = HealthRangeRequest.parse(
            "```healthrange\n{ \"from\": \"01/01/2025\", \"to\": \"31/01/2025\" }\n```",
            overview,
        )

        val accepted = outcome as HealthRangeOutcome.Accepted
        assertEquals(LocalDate.of(2025, 1, 1), accepted.from)
        assertEquals(LocalDate.of(2025, 1, 31), accepted.to)
    }

    @Test
    fun `un JSON illisible est refuse`() {
        val outcome = HealthRangeRequest.parse("```healthrange\n{ pas du json\n```", overview)

        assertTrue((outcome as HealthRangeOutcome.Rejected).reason.contains("JSON"))
    }

    @Test
    fun `des champs from ou to manquants sont refuses`() {
        val outcome = HealthRangeRequest.parse("```healthrange\n{ \"from\": \"2025-01-01\" }\n```", overview)

        assertTrue((outcome as HealthRangeOutcome.Rejected).reason.contains("obligatoires"))
    }

    @Test
    fun `une date illisible est refusee avec un message que le modele peut lire`() {
        val outcome = HealthRangeRequest.parse(
            "```healthrange\n{ \"from\": \"hier\", \"to\": \"2025-01-31\" }\n```",
            overview,
        )

        val rejected = outcome as HealthRangeOutcome.Rejected
        assertTrue(rejected.reason.contains("hier"))
        assertTrue(rejected.reason.contains("AAAA-MM-JJ"))
    }

    @Test
    fun `une periode entierement hors historique est refusee`() {
        val outcome = HealthRangeRequest.parse(
            "```healthrange\n{ \"from\": \"2019-01-01\", \"to\": \"2019-12-31\" }\n```",
            overview,
        )

        val rejected = outcome as HealthRangeOutcome.Rejected
        assertTrue(rejected.reason.contains("2024-06-01"))
        assertTrue(rejected.reason.contains("2025-08-31"))
    }

    @Test
    fun `une periode partiellement hors historique est ramenee a l'historique disponible`() {
        val outcome = HealthRangeRequest.parse(
            "```healthrange\n{ \"from\": \"2025-08-01\", \"to\": \"2025-12-31\" }\n```",
            overview,
        )

        val accepted = outcome as HealthRangeOutcome.Accepted
        assertEquals(LocalDate.of(2025, 8, 1), accepted.from)
        assertEquals(LocalDate.of(2025, 8, 31), accepted.to) // ramenée à `overview.latest`
        assertTrue(accepted.note!!.contains("ramenée"))
    }

    @Test
    fun `une periode vide, sans aucune mesure dans les mois couverts, est refusee`() {
        // Août 2024 est un trou dans l'historique de test (voir `overview`).
        val outcome = HealthRangeRequest.parse(
            "```healthrange\n{ \"from\": \"2024-08-01\", \"to\": \"2024-08-31\" }\n```",
            overview,
        )

        assertTrue((outcome as HealthRangeOutcome.Rejected).reason.contains("aucune mesure"))
    }

    @Test
    fun `sans aucune donnee importee, toute demande est refusee`() {
        val emptyOverview = HistoryOverview(earliest = null, latest = null, months = emptyList())

        val outcome = HealthRangeRequest.parse(
            "```healthrange\n{ \"from\": \"2025-01-01\", \"to\": \"2025-01-31\" }\n```",
            emptyOverview,
        )

        assertTrue((outcome as HealthRangeOutcome.Rejected).reason.contains("aucune donnée"))
    }
}
