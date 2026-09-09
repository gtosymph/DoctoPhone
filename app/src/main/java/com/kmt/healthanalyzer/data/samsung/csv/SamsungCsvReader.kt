package com.kmt.healthanalyzer.data.samsung.csv

import java.io.BufferedReader
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Signale un fichier CSV Samsung Health que le lecteur ne sait pas interpréter. */
class SamsungCsvException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Descripteur d'un export Samsung Health, lu sur la première ligne du fichier.
 * Exemple : `com.samsung.shealth.sleep,7006011,11`.
 */
data class SamsungCsvDescriptor(
    val dataType: String,
    val appVersion: String,
    val schemaVersion: String,
)

/** Un fichier CSV Samsung Health entièrement chargé en mémoire. */
data class SamsungCsvDocument(
    val descriptor: SamsungCsvDescriptor,
    val columns: List<String>,
    val rows: List<SamsungCsvRow>,
) {
    val dataType: String get() = descriptor.dataType
    val appVersion: String get() = descriptor.appVersion
    val schemaVersion: String get() = descriptor.schemaVersion
}

/**
 * Une ligne de données, accessible par nom de colonne.
 *
 * Toutes les lectures renvoient `null` quand la cellule est absente, vide ou illisible.
 * Un export Samsung Health contient beaucoup de colonnes vides. Le lecteur ne lève
 * donc jamais d'exception sur une cellule : il rend `null` et laisse le mappeur décider.
 */
class SamsungCsvRow internal constructor(
    private val index: Map<String, Int>,
    private val cells: List<String?>,
) {
    val size: Int get() = index.size

    fun string(column: String): String? {
        val position = index[column] ?: return null
        return cells.getOrNull(position)
    }

    fun double(column: String): Double? = string(column)?.toDoubleOrNull()

    fun float(column: String): Float? = string(column)?.toFloatOrNull()

    fun int(column: String): Int? = double(column)?.toInt()

    fun long(column: String): Long? = double(column)?.toLong()

    fun boolean(column: String): Boolean? = when (string(column)) {
        null -> null
        "0", "false", "FALSE" -> false
        else -> true
    }

    /** Décalage horaire de la ligne, au format Samsung `UTC+0200`. */
    fun zoneOffset(column: String): ZoneOffset? {
        val raw = string(column) ?: return null
        val sign = raw.getOrNull(UTC_PREFIX.length) ?: return null
        if (!raw.startsWith(UTC_PREFIX) || (sign != '+' && sign != '-')) return null
        val digits = raw.substring(UTC_PREFIX.length + 1)
        if (digits.length != 4) return null
        val hours = digits.substring(0, 2).toIntOrNull() ?: return null
        val minutes = digits.substring(2, 4).toIntOrNull() ?: return null
        val total = hours * 3600 + minutes * 60
        return runCatching {
            ZoneOffset.ofTotalSeconds(if (sign == '-') -total else total)
        }.getOrNull()
    }

    /** Convertit un horodatage local Samsung en instant absolu. */
    fun instant(column: String, offsetColumn: String = TIME_OFFSET_COLUMN): Instant? {
        val local = localDateTime(column) ?: return null
        return local.toInstant(zoneOffset(offsetColumn) ?: ZoneOffset.UTC)
    }

    fun localDateTime(column: String): LocalDateTime? {
        val raw = string(column) ?: return null
        return try {
            LocalDateTime.parse(raw, TIMESTAMP_FORMAT)
        } catch (parseError: DateTimeParseException) {
            null
        }
    }

    fun localDate(column: String, offsetColumn: String = TIME_OFFSET_COLUMN): LocalDate? =
        localDateTime(column)?.toLocalDate() ?: instant(column, offsetColumn)?.let {
            // `LocalDate.ofInstant` n'arrive qu'avec Android 14 ; ce chemin marche depuis Android 8.
            it.atOffset(zoneOffset(offsetColumn) ?: ZoneOffset.UTC).toLocalDate()
        }

    private companion object {
        const val UTC_PREFIX = "UTC"
        const val TIME_OFFSET_COLUMN = "time_offset"
        val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]")
    }
}

/**
 * Lit les fichiers CSV d'un export Samsung Health.
 *
 * Le format n'est pas un CSV standard :
 * - la ligne 1 décrit le type de donnée, pas les colonnes ;
 * - la ligne 2 porte les noms de colonnes, précédés d'un BOM UTF-8 ;
 * - chaque ligne se termine par une virgule, qui crée une colonne vide parasite ;
 * - les champs de texte libre suivent la RFC 4180 (guillemets, virgules internes).
 */
class SamsungCsvReader {

    fun read(input: InputStream): SamsungCsvDocument {
        val rows = mutableListOf<SamsungCsvRow>()
        val document = forEachRow(input) { rows.add(it) }
        return document.copy(rows = rows.toList())
    }

    /**
     * Lit le fichier ligne par ligne sans le charger entièrement.
     * Renvoie le document sans ses lignes ; celles-ci passent par [action].
     */
    fun forEachRow(input: InputStream, action: (SamsungCsvRow) -> Unit): SamsungCsvDocument {
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val descriptor = readDescriptor(reader)
            val columns = readColumns(reader)
            val index = columns.withIndex().associate { (position, name) -> name to position }

            var line = reader.readLine()
            val pending = StringBuilder()
            while (line != null) {
                pending.append(line)
                if (hasBalancedQuotes(pending)) {
                    val record = pending.toString()
                    pending.setLength(0)
                    if (record.isNotBlank()) {
                        action(SamsungCsvRow(index, splitRecord(record, columns.size)))
                    }
                } else {
                    // Un champ entre guillemets contient un retour à la ligne.
                    pending.append('\n')
                }
                line = reader.readLine()
            }
            return SamsungCsvDocument(descriptor, columns, emptyList())
        }
    }

    private fun readDescriptor(reader: BufferedReader): SamsungCsvDescriptor {
        val raw = reader.readLine()
            ?: throw SamsungCsvException("Le fichier est vide.")
        val parts = stripBom(raw).split(FIELD_SEPARATOR)
        if (parts.size < 3 || !parts[0].startsWith(SAMSUNG_PREFIX)) {
            throw SamsungCsvException("Ligne de description Samsung Health invalide : \"$raw\".")
        }
        return SamsungCsvDescriptor(
            dataType = parts[0].trim(),
            appVersion = parts[1].trim(),
            schemaVersion = parts[2].trim(),
        )
    }

    private fun readColumns(reader: BufferedReader): List<String> {
        val raw = reader.readLine()
            ?: throw SamsungCsvException("Le fichier ne contient aucune ligne de colonnes.")
        return stripBom(raw)
            .split(FIELD_SEPARATOR)
            .map { it.trim() }
            .dropLastWhile { it.isEmpty() }
    }

    private fun stripBom(value: String): String = value.removePrefix(BOM)

    /**
     * Découpe une ligne selon la RFC 4180, puis normalise sa longueur sur [columnCount].
     * Les cellules vides deviennent `null`.
     */
    private fun splitRecord(record: String, columnCount: Int): List<String?> {
        val cells = ArrayList<String?>(columnCount)
        val field = StringBuilder()
        var inQuotes = false
        var position = 0

        while (position < record.length) {
            val character = record[position]
            when {
                inQuotes && character == QUOTE && record.getOrNull(position + 1) == QUOTE -> {
                    field.append(QUOTE)
                    position += 1
                }
                character == QUOTE -> inQuotes = !inQuotes
                character == FIELD_SEPARATOR && !inQuotes -> {
                    cells.add(field.toString().takeIf { it.isNotEmpty() })
                    field.setLength(0)
                }
                else -> field.append(character)
            }
            position += 1
        }
        cells.add(field.toString().takeIf { it.isNotEmpty() })

        while (cells.size < columnCount) cells.add(null)
        return cells
    }

    private fun hasBalancedQuotes(record: CharSequence): Boolean =
        record.count { it == QUOTE } % 2 == 0

    private companion object {
        const val BOM = "\uFEFF"
        const val SAMSUNG_PREFIX = "com.samsung"
        const val FIELD_SEPARATOR = ','
        const val QUOTE = '"'
    }
}
