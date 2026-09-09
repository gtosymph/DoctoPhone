package com.kmt.healthanalyzer.ui.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JsonModelInterceptorTest {

    private val interceptor = JsonModelInterceptor("https://appassets.androidplatform.net/model/conversation.json")

    @Test
    fun `sert le dernier JSON fourni pour l'URL exacte`() {
        interceptor.update("""{"meta":{"days":1}}""")

        assertEquals(
            """{"meta":{"days":1}}""",
            interceptor.bodyFor("https://appassets.androidplatform.net/model/conversation.json"),
        )
    }

    @Test
    fun `ignore le defait-cache de la requete pour comparer l'URL`() {
        interceptor.update("""{"a":1}""")

        assertEquals("""{"a":1}""", interceptor.bodyFor("https://appassets.androidplatform.net/model/conversation.json?v=42"))
    }

    @Test
    fun `rend un objet JSON vide si aucun modele n'a encore ete fourni`() {
        assertEquals("{}", interceptor.bodyFor("https://appassets.androidplatform.net/model/conversation.json"))
    }

    @Test
    fun `rend null pour toute autre URL`() {
        interceptor.update("""{"a":1}""")

        assertNull(interceptor.bodyFor("https://appassets.androidplatform.net/assets/chat/chat.html"))
    }

    @Test
    fun `rend null pour l'URL du modele du rapport`() {
        assertNull(interceptor.bodyFor("https://appassets.androidplatform.net/model/report.json"))
    }

    @Test
    fun `respond rend une reponse pour l'URL du modele`() {
        interceptor.update("""{"a":1}""")

        val response = interceptor.respond("https://appassets.androidplatform.net/model/conversation.json")

        // `WebResourceResponse` ne se relit pas dans un test JVM sans Robolectric (voir
        // `bodyFor`, testé ci-dessus pour le contenu) : seule son existence est vérifiée ici.
        assertNotNull(response)
    }

    @Test
    fun `respond rend null pour toute autre URL`() {
        assertNull(interceptor.respond("https://appassets.androidplatform.net/model/report.json"))
    }
}
