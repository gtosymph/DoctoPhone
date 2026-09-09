package com.kmt.healthanalyzer.data.samsung

import com.kmt.healthanalyzer.domain.model.HrvSample
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Lit un fichier `binning_data.json` de variabilité cardiaque.
 *
 * Le fichier contient les mesures brutes d'une fenêtre d'environ une heure, une toutes
 * les 30 secondes. Le parseur en garde la médiane. La médiane résiste aux artefacts de
 * mouvement, fréquents la nuit, là où la moyenne se laisse tirer par quelques pics.
 */
object HrvBinningParser {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Bin(
        val start_time: Long? = null,
        val end_time: Long? = null,
        val sdnn: Float? = null,
        val rmssd: Float? = null,
    )

    fun parse(id: String, json: String): HrvSample? {
        val bins = this.json.decodeFromString<List<Bin>>(json)
        if (bins.isEmpty()) return null

        val start = bins.mapNotNull { it.start_time }.minOrNull() ?: return null
        val end = bins.mapNotNull { it.end_time }.maxOrNull() ?: start

        return HrvSample(
            id = id,
            time = Instant.ofEpochMilli((start + end) / 2),
            sdnnMillis = median(bins.mapNotNull { it.sdnn }),
            rmssdMillis = median(bins.mapNotNull { it.rmssd }),
        )
    }

    private fun median(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2f
        }
    }
}
