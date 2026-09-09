package com.kmt.healthanalyzer.data.samsung

import java.time.LocalDate

/** Bilan d'un import d'archive Samsung Health. */
data class ImportSummary(
    val recordsByType: Map<String, Int>,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
    val warnings: List<String>,
) {
    val totalRecords: Int get() = recordsByType.values.sum()
}

/** Avancement de l'import, publié au fil de la lecture de l'archive. */
sealed interface ImportProgress {
    /** Le fichier [fileName] est en cours de lecture. */
    data class Reading(
        val fileName: String,
        val filesDone: Int,
        val filesTotal: Int,
    ) : ImportProgress {
        /** Avancement entre 0 et 1, ou `null` si le total est inconnu. */
        val fraction: Float? get() = if (filesTotal > 0) filesDone.toFloat() / filesTotal else null
    }

    /** L'import est terminé. */
    data class Finished(val summary: ImportSummary) : ImportProgress
}
