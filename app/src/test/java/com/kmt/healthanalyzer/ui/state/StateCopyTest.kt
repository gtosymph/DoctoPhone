package com.kmt.healthanalyzer.ui.state

import com.kmt.healthanalyzer.data.llm.LlmError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les trois règles du § 8 des specs, vérifiées sur les textes eux-mêmes.
 *
 * Ces phrases sont la seule chose que l'utilisateur lit quand rien ne va. Les laisser
 * naître au fil des écrans donne neuf tons différents et, surtout, des messages qui
 * décrivent la panne sans dire comment en sortir. Elles vivent donc en un seul endroit,
 * et ce test les tient.
 */
class StateCopyTest {

    @Test
    fun `les neuf situations du tableau ont chacune un texte`() {
        val textes = listOf(
            StateCopy.NO_DATA,
            StateCopy.syncing("septembre 2026"),
            StateCopy.buildingReport(294),
            StateCopy.shortBaseline(required = 21, measured = 12),
            StateCopy.sparseWeek(required = 5, measured = 3),
            StateCopy.HISTORY_PERMISSION_MISSING,
            StateCopy.MISSING_API_KEY,
            StateCopy.ANALYZING,
            StateCopy.forFailure(LlmError.Network(IOException("boom"))).message,
        )

        assertEquals("neuf situations, neuf textes", 9, textes.size)
        textes.forEach { texte ->
            assertTrue("un texte d'état est vide", texte.isNotBlank())
            // Une phrase finie, ou une attente qui porte ses points de suspension puis ce
            // qu'elle traite : « Récupération de l'historique… septembre 2026 ».
            assertTrue(
                "« $texte » n'est ni une phrase finie ni une attente",
                texte.last() in charArrayOf('.', '…', '!', '?') || texte.contains('…'),
            )
        }
    }

    @Test
    fun `une attente dit toujours sur quoi elle porte`() {
        // « Chargement… » ne dit rien. Un mois, un nombre de jours, une étape : l'attente
        // doit nommer ce qu'elle traite, sinon elle ne se distingue pas d'un blocage.
        assertTrue(StateCopy.syncing("septembre 2026").contains("septembre 2026"))
        assertTrue(StateCopy.buildingReport(294).contains("294"))
        assertTrue("une attente se termine par des points de suspension", StateCopy.ANALYZING.endsWith("…"))
    }

    @Test
    fun `un etat vide dit toujours pourquoi, avec ses deux nombres`() {
        // « Aucune dérive » et « pas assez de données » se ressemblent à l'écran et ne
        // veulent pas dire la même chose. Les deux nombres lèvent l'ambiguïté : combien
        // il en faut, combien on en a.
        val baseline = StateCopy.shortBaseline(required = 21, measured = 12)
        assertTrue(baseline.contains("21"))
        assertTrue(baseline.contains("12"))

        val semaine = StateCopy.sparseWeek(required = 5, measured = 3)
        assertTrue(semaine.contains("5"))
        assertTrue(semaine.contains("3"))
    }

    @Test
    fun `un echec dit toujours quoi faire ensuite, et offre le geste`() {
        val echecs = listOf(
            LlmError.MissingApiKey(),
            LlmError.InvalidApiKey(),
            LlmError.RateLimited(retryAfterSeconds = 30),
            LlmError.Network(IOException("boom")),
            LlmError.Server(statusCode = 503, body = ""),
            LlmError.Malformed(IllegalStateException("boom")),
            IOException("une cause quelconque"),
        )

        echecs.forEach { cause ->
            val copy = StateCopy.forFailure(cause)
            assertNotEquals(
                "l'échec « ${copy.message} » n'offre aucun geste",
                StateAction.NONE,
                copy.action,
            )
            // Le geste peut être dans la phrase (« puis réessayez ») ou porté par le
            // bouton (« Ouvrir les réglages »). Ce qui est interdit, c'est de laisser le
            // lecteur devant un constat sans suite.
            assertTrue(
                "l'échec « ${copy.message} » ne dit pas quoi faire ensuite, et son geste " +
                    "« ${copy.action.label} » ne le dit pas non plus",
                ACTION_VERBS.any {
                    copy.message.contains(it, ignoreCase = true) ||
                        copy.action.label.contains(it, ignoreCase = true)
                },
            )
        }
    }

    @Test
    fun `aucun message ne montre une cause technique brute`() {
        // Une trace d'exception, un nom de classe ou un `null` dans une phrase disent au
        // lecteur que quelque chose a débordé, pas ce qu'il doit faire.
        val causes = listOf(
            LlmError.Network(IOException("java.net.UnknownHostException: api.anthropic.com")),
            LlmError.Malformed(IllegalStateException("Unexpected JSON token at offset 42")),
            IllegalStateException("kotlin.KotlinNullPointerException"),
        )

        causes.forEach { cause ->
            val message = StateCopy.forFailure(cause).message
            INTERDITS.forEach { interdit ->
                assertFalse(
                    "« $message » laisse passer « $interdit »",
                    message.contains(interdit, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun `une panne de reseau renvoie le texte exact du tableau`() {
        val copy = StateCopy.forFailure(LlmError.Network(IOException("boom")))
        assertEquals(
            "La requête n'a pas abouti. Vérifiez votre connexion, puis réessayez.",
            copy.message,
        )
        assertEquals(StateAction.RETRY, copy.action)
    }

    @Test
    fun `une cle absente mene aux reglages et rassure sur l'endroit ou elle vit`() {
        val copy = StateCopy.forFailure(LlmError.MissingApiKey())
        assertEquals(StateAction.OPEN_SETTINGS, copy.action)
        assertTrue(
            "la phrase doit dire que la clé ne quitte pas l'appareil : c'est la question " +
                "que se pose quiconque colle une clé payante dans une app de santé",
            copy.message.contains("appareil"),
        )
    }

    @Test
    fun `une limite de debit annonce l'attente quand le fournisseur la donne`() {
        val avecDelai = StateCopy.forFailure(LlmError.RateLimited(retryAfterSeconds = 30))
        assertTrue("« ${avecDelai.message} » n'annonce pas les 30 secondes", avecDelai.message.contains("30"))

        val sansDelai = StateCopy.forFailure(LlmError.RateLimited(retryAfterSeconds = null))
        assertTrue(sansDelai.message.isNotBlank())
        assertEquals(StateAction.RETRY, sansDelai.action)
    }

    @Test
    fun `chaque geste offert porte un libelle`() {
        StateAction.entries.filter { it != StateAction.NONE }.forEach { action ->
            assertTrue("le geste $action n'a pas de libellé", action.label.isNotBlank())
        }
        assertEquals("le geste « aucun » ne porte volontairement aucun libellé", "", StateAction.NONE.label)
    }

    @Test
    fun `le message d'historique nomme la fenetre de trente jours et le geste`() {
        val texte = StateCopy.HISTORY_PERMISSION_MISSING
        assertTrue(texte.contains("30"))
        assertTrue(
            "le texte doit dire quoi faire, pas seulement constater la limite",
            ACTION_VERBS.any { texte.contains(it, ignoreCase = true) },
        )
    }

    private companion object {
        /** Les verbes qui font d'un constat une consigne. Voir la règle 3 du § 8. */
        val ACTION_VERBS = listOf(
            "Vérifiez", "réessayez", "Importez", "Autorisez", "Ouvrez",
            "Enregistrez", "Attendez", "Connectez", "Choisissez",
            // Les infinitifs des libellés de bouton.
            "Réessayer", "Importer", "Autoriser", "Ouvrir", "Annuler",
        )

        val INTERDITS = listOf(
            "java.", "kotlin.", "Exception", "null", "offset", "JSON token",
        )
    }
}
