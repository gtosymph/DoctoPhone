package com.kmt.healthanalyzer.ui.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [ReportJsBridge] ne pousse plus le `ReportModel` comme argument d'un script — voir sa
 * documentation pour la mesure qui motive ce choix. Il ne reste que deux signaux courts,
 * jamais de contenu variable à échapper : ces tests le vérifient.
 */
class ReportJsBridgeTest {

    @Test
    fun `buildSetModelReadyScript encadre la version, un entier, sans guillemets`() {
        val script = ReportJsBridge.buildSetModelReadyScript(1234L)

        assertEquals("HA.host.setModelReady(1234);", script)
    }

    @Test
    fun `buildSetModelReadyScript ne porte jamais le JSON du modèle, seulement sa version`() {
        val script = ReportJsBridge.buildSetModelReadyScript(System.currentTimeMillis())

        assertFalse("le modèle doit être servi par shouldInterceptRequest, jamais ici", script.contains("{"))
    }

    @Test
    fun `le thème sombre produit un appel setTheme dark`() {
        assertEquals("HA.host.setTheme(\"dark\");", ReportJsBridge.buildSetThemeScript(dark = true))
    }

    @Test
    fun `le thème clair produit un appel setTheme light`() {
        assertEquals("HA.host.setTheme(\"light\");", ReportJsBridge.buildSetThemeScript(dark = false))
    }
}
