package com.kmt.healthanalyzer.data.samsung

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Export lu depuis un dossier choisi par l'utilisateur dans le sélecteur Android.
 *
 * C'est le chemin normal : Samsung Health écrit un dossier
 * `samsunghealth_<compte>_<horodatage>` dans le stockage du téléphone. L'utilisateur le
 * désigne, et l'app le lit sur place, sans copie ni décompression.
 *
 * Le parcours passe par [DocumentsContract] plutôt que par `DocumentFile` : une seule
 * requête par dossier suffit à obtenir le nom, le type et l'identifiant de tous ses
 * enfants. `DocumentFile` ferait une requête par attribut et par fichier, ce qui se
 * compte en minutes sur un export de plusieurs milliers de fichiers.
 */
class DocumentTreeExportSource(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : SamsungExportSource {

    override fun entries(): List<SamsungExportEntry> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val collected = mutableListOf<SamsungExportEntry>()
        collect(documentId = rootId, prefix = "", into = collected, depth = 0)
        return collected
    }

    private fun collect(
        documentId: String,
        prefix: String,
        into: MutableList<SamsungExportEntry>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

        val cursor = try {
            resolver.query(childrenUri, PROJECTION, null, null, null)
        } catch (unreadable: SecurityException) {
            null
        } ?: return

        cursor.use {
            while (it.moveToNext()) {
                val childId = it.getString(0)
                val name = it.getString(1) ?: continue
                val isDirectory = it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                val path = if (prefix.isEmpty()) name else "$prefix/$name"

                if (isDirectory) {
                    collect(childId, path, into, depth + 1)
                } else {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    into += SamsungExportEntry(path) { openOrFail(fileUri, path) }
                }
            }
        }
    }

    private fun openOrFail(uri: Uri, path: String): InputStream =
        resolver.openInputStream(uri)
            ?: throw FileNotFoundException("Le fichier $path est illisible.")

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        /** L'export Samsung tient en trois niveaux ; la borne protège d'un dossier cyclique. */
        const val MAX_DEPTH = 6
    }
}
