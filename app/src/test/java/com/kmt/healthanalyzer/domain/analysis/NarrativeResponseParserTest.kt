package com.kmt.healthanalyzer.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrativeResponseParserTest {

    private val validJson = """
        {
          "headline": "Sommeil insuffisant, tendance en baisse",
          "verdict": "Le sommeil reste sous la cible sur la période.",
          "sections": {
            "sleep": { "verdict": "Moyenne de 5 h 48.", "points": ["Coucher irrégulier."] }
          },
          "plan": [
            { "title": "Avancer le coucher", "body": "Se coucher 30 minutes plus tôt les jours de travail." }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses a clean json response`() {
        val result = NarrativeResponseParser.parse(validJson)

        assertTrue(result is NarrativeParseResult.Success)
        val narrative = (result as NarrativeParseResult.Success).narrative
        assertEquals("Sommeil insuffisant, tendance en baisse", narrative.headline)
        assertEquals(1, narrative.plan.size)
        assertEquals("Avancer le coucher", narrative.plan.single().title)
        assertEquals(1, narrative.sections.size)
        assertEquals("Moyenne de 5 h 48.", narrative.sections.getValue("sleep").verdict)
    }

    @Test
    fun `extracts the json when the model wraps it in prose`() {
        val wrapped = "Voici le résultat demandé :\n\n$validJson\n\nJ'espère que cela vous aide."

        val result = NarrativeResponseParser.parse(wrapped)

        assertTrue(result is NarrativeParseResult.Success)
    }

    @Test
    fun `extracts the json when the model wraps it in a markdown code fence`() {
        val wrapped = "```json\n$validJson\n```"

        val result = NarrativeResponseParser.parse(wrapped)

        assertTrue(result is NarrativeParseResult.Success)
    }

    @Test
    fun `does not stop at a closing brace found inside a string value`() {
        // Une accolade fermante littérale dans une valeur de chaîne ne doit pas être
        // prise pour la fin de l'objet JSON.
        val json = """{"headline": "Un résumé { entre accolades }", "verdict": "Rien à signaler."}"""

        val result = NarrativeResponseParser.parse(json)

        assertTrue(result is NarrativeParseResult.Success)
        assertEquals("Un résumé { entre accolades }", (result as NarrativeParseResult.Success).narrative.headline)
    }

    @Test
    fun `fails readably instead of throwing when no json object is found`() {
        val result = NarrativeResponseParser.parse("Je ne peux pas répondre à cette demande.")

        assertTrue(result is NarrativeParseResult.Failure)
        assertTrue((result as NarrativeParseResult.Failure).reason.isNotBlank())
    }

    @Test
    fun `fails readably instead of throwing when the json is malformed`() {
        val result = NarrativeResponseParser.parse("""{"headline": "Titre coupé""")

        assertTrue(result is NarrativeParseResult.Failure)
    }

    @Test
    fun `fails readably instead of throwing when a required field is missing`() {
        val result = NarrativeResponseParser.parse("""{"verdict": "Sans titre"}""")

        assertTrue(result is NarrativeParseResult.Failure)
    }
}
