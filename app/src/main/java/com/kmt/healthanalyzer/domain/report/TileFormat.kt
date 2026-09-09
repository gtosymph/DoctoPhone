package com.kmt.healthanalyzer.domain.report

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Formate les champs `value`/`sub` des tuiles en français — les seuls endroits du
 * modèle qui portent du texte déjà mis en forme (voir [ReportModel] : partout ailleurs,
 * le modèle garde des nombres bruts et c'est le moteur de dessin qui formate).
 *
 * Rassemblé ici en un seul endroit pour qu'une correction future ne se fasse qu'à un
 * point : virgule décimale, espace fine insécable pour les milliers, heure d'horloge
 * `0h44` (jamais `00:44`), date « 23 juin 2025 » (jamais ISO). Portage fidèle de
 * `TileFormat` côté JavaScript (`web/lib/report-sections/tiles.js`) : le test de parité
 * compare les chaînes produites au caractère près.
 *
 * `String.format`/`DecimalFormat` dépendent de la locale de l'appareil — un téléphone
 * réglé en anglais produirait un point décimal. Toutes les chaînes sont donc composées
 * à la main, sans jamais passer par la locale par défaut.
 */
internal object TileFormat {

    const val NO_DATA = "—"

    /** Espace fine insécable (U+202F) : séparateur de milliers français. */
    private const val THIN_SPACE = ' '

    private val FULL_MONTHS_FR = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre",
    )

    /** Nombre décimal français : virgule, espace fine tous les 3 chiffres. `null` si non fini. */
    fun number(value: Double?, decimals: Int): String? {
        if (value == null || value.isNaN() || value.isInfinite()) return null
        val sign = if (value < 0) "-" else ""
        val scale = pow10(decimals)
        val rounded = (abs(value) * scale).roundToLong()
        val intPart = rounded / scale
        val fracPart = rounded % scale
        val grouped = groupThousands(intPart.toString())
        return if (decimals > 0) {
            "$sign$grouped,${fracPart.toString().padStart(decimals, '0')}"
        } else {
            "$sign$grouped"
        }
    }

    /** Durée : « 5 h 48 ». */
    fun duration(hours: Double): String {
        val totalMinutes = (hours * 60.0).roundToLong()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return "$h h ${m.toString().padStart(2, '0')}"
    }

    /** Heure relative à minuit en heure d'horloge : « 0h44 », « 23h10 » (jamais `00:44`). */
    fun clock(relativeHours: Double): String {
        val clockHours = if (relativeHours < 0) relativeHours + 24.0 else relativeHours
        var h = floor(clockHours).toLong()
        var m = ((clockHours - h) * 60.0).roundToLong()
        if (m == 60L) {
            m = 0
            h += 1
        }
        if (h == 24L) h = 0
        return "${h}h${m.toString().padStart(2, '0')}"
    }

    /** Date `AAAA-MM-JJ` en français lisible : « 23 juin 2025 ». */
    fun date(dateKey: String?): String {
        if (dateKey == null) return NO_DATA
        val (y, m, d) = dateKey.split("-").map { it.toInt() }
        return "$d ${FULL_MONTHS_FR[m - 1]} $y"
    }

    private fun groupThousands(digits: String): String {
        val builder = StringBuilder()
        for ((index, char) in digits.reversed().withIndex()) {
            if (index > 0 && index % 3 == 0) builder.append(THIN_SPACE)
            builder.append(char)
        }
        return builder.reverse().toString()
    }

    private fun pow10(decimals: Int): Long {
        var result = 1L
        repeat(decimals) { result *= 10 }
        return result
    }
}
