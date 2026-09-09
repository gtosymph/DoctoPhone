package com.kmt.healthanalyzer.data.samsung

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Un fichier de l'export, quelle que soit la façon dont l'export est arrivé sur l'appareil. */
class SamsungExportEntry(
    /** Chemin relatif dans l'export, séparateurs `/`. */
    val path: String,
    private val opener: () -> InputStream,
) {
    fun open(): InputStream = opener()

    val fileName: String get() = path.substringAfterLast('/')
}

/**
 * Source d'un export Samsung Health.
 *
 * Samsung Health écrit un **dossier** sur le téléphone. C'est le format natif.
 * Beaucoup de gens compressent ce dossier pour le transférer vers un autre appareil.
 * L'app accepte donc les deux, derrière cette même interface.
 */
interface SamsungExportSource : AutoCloseable {
    /** Les fichiers de l'export, à plat, chemins relatifs à la racine. */
    fun entries(): List<SamsungExportEntry>

    override fun close() = Unit
}

/**
 * Export lu depuis un dossier du système de fichiers.
 *
 * C'est la forme que Samsung Health produit : un dossier
 * `samsunghealth_<compte>_<horodatage>` contenant les CSV, plus les sous-dossiers
 * `jsons/` et `files/`.
 */
class DirectoryExportSource(private val root: File) : SamsungExportSource {

    override fun entries(): List<SamsungExportEntry> = root
        .walkTopDown()
        .filter { it.isFile }
        .map { file ->
            SamsungExportEntry(
                path = file.relativeTo(root).invariantSeparatorsPath,
                opener = { file.inputStream() },
            )
        }
        .toList()
}

/**
 * Export lu depuis une archive compressée.
 *
 * L'archive est lue avec [ZipFile] et non avec `ZipInputStream` : selon l'outil qui l'a
 * créée, elle peut contenir des entrées non compressées munies d'un descripteur de fin,
 * que la lecture en flux refuse (`only DEFLATED entries can have EXT descriptor`).
 */
class ZipExportSource(archive: File) : SamsungExportSource {

    private val zip = ZipFile(archive)

    override fun entries(): List<SamsungExportEntry> = zip.entries()
        .toList()
        .filterNot { it.isDirectory }
        .map { entry ->
            SamsungExportEntry(
                path = entry.name,
                opener = { zip.getInputStream(entry) },
            )
        }

    override fun close() = zip.close()
}
