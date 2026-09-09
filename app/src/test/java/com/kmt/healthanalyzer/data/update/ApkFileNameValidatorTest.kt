package com.kmt.healthanalyzer.data.update

import com.kmt.healthanalyzer.data.llm.assertFailsWithType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le champ `apk` de `release.json` est une donnée renvoyée par un tiers, pas une consigne :
 * ces tests vérifient qu'[ApkFileNameValidator] ne laisse jamais passer un nom qui écrirait
 * hors du dossier de cache dédié à la mise à jour.
 */
class ApkFileNameValidatorTest {

    @Test
    fun `un nom de fichier simple est conserve tel quel`() = runTest {
        val fileName = ApkFileNameValidator.requireSafeApkFileName("DoctoPhone-0.2.123.apk")

        assertEquals("DoctoPhone-0.2.123.apk", fileName)
    }

    @Test
    fun `une traversee de repertoire vers un apk est reduite a son dernier segment`() = runTest {
        // Le chemin annoncé traverse des répertoires, mais son dernier segment reste un nom
        // de fichier .apk propre : on l'isole plutôt que de refuser, et il ne peut plus,
        // seul, faire sortir l'écriture du dossier de cache dédié.
        val fileName = ApkFileNameValidator.requireSafeApkFileName("../../evil.apk")

        assertEquals("evil.apk", fileName)
    }

    @Test
    fun `une traversee de repertoire avec antislash est reduite a son dernier segment`() = runTest {
        val fileName = ApkFileNameValidator.requireSafeApkFileName("..\\..\\evil.apk")

        assertEquals("evil.apk", fileName)
    }

    @Test
    fun `une traversee vers un fichier hors extension apk est refusee`() = runTest {
        // Le scénario concret que ce contrôle empêche : atteindre la base de données de santé.
        assertFailsWithType<UpdateError.Malformed> {
            ApkFileNameValidator.requireSafeApkFileName("../../databases/health.db")
        }
    }

    @Test
    fun `une chaine vide est refusee`() = runTest {
        assertFailsWithType<UpdateError.Malformed> {
            ApkFileNameValidator.requireSafeApkFileName("")
        }
    }

    @Test
    fun `un nom reduit a un point ou deux points est refuse`() = runTest {
        assertFailsWithType<UpdateError.Malformed> {
            ApkFileNameValidator.requireSafeApkFileName("..")
        }
        assertFailsWithType<UpdateError.Malformed> {
            ApkFileNameValidator.requireSafeApkFileName(".")
        }
    }
}
