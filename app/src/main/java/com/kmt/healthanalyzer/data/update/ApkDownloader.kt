package com.kmt.healthanalyzer.data.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

/** Avancement d'un téléchargement d'APK, émis au fil de la lecture puis à la fin. */
sealed class DownloadProgress {
    data class InProgress(val bytesReceived: Long, val totalBytes: Long) : DownloadProgress()
    data class Done(val file: File) : DownloadProgress()
}

/** Sous-dossier du cache exposé par `res/xml/file_paths.xml`, rien d'autre ne l'est. */
private const val DOWNLOAD_DIR_NAME = "mises-a-jour"

/** N'émet l'avancement que tous les 256 Kio reçus, pour ne pas saturer la collecte du flux. */
private const val PROGRESS_STEP_BYTES = 256L * 1024
private const val BUFFER_SIZE_BYTES = 8 * 1024

/**
 * Télécharge l'APK d'une mise à jour vers le cache de l'app, en rapportant l'avancement.
 *
 * [apkDownloadUrl] vient de la réponse GitHub — une donnée, pas une consigne — et passe par
 * [DownloadUrlValidator] avant tout appel réseau, comme [GitHubReleaseClient] le fait déjà
 * pour `release.json`.
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {

    /**
     * @throws UpdateError si l'URL n'est pas fiable, si le réseau échoue, ou si le fournisseur
     *   renvoie une erreur HTTP.
     */
    fun download(apkDownloadUrl: String, fileName: String): Flow<DownloadProgress> = flow {
        val validatedUrl = DownloadUrlValidator.requireTrustedHttpsUrl(apkDownloadUrl)
        // fileName vient du champ `apk` de release.json, une donnée de tiers au même titre que
        // l'URL : sans ce contrôle, un nom comme "../../databases/health.db" écrirait hors de
        // ce sous-dossier de cache. Voir ApkFileNameValidator pour le détail.
        val safeFileName = ApkFileNameValidator.requireSafeApkFileName(fileName)

        // Enlève tout téléchargement précédent : ce sous-dossier n'a pas d'autre usage, et une
        // version périmée ne doit pas s'accumuler dans le cache au fil des vérifications.
        val dir = File(context.cacheDir, DOWNLOAD_DIR_NAME).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, safeFileName)

        val request = Request.Builder().url(validatedUrl).get().build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (networkFailure: IOException) {
            throw UpdateError.Network(networkFailure)
        }

        response.use { httpResponse ->
            if (!httpResponse.isSuccessful) throw UpdateError.Server(httpResponse.code)
            val body = httpResponse.body ?: throw UpdateError.Malformed()
            val totalBytes = body.contentLength()

            try {
                var bytesReceived = 0L
                var bytesSinceLastEmit = 0L
                val buffer = ByteArray(BUFFER_SIZE_BYTES)

                target.outputStream().use { output ->
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesReceived += read
                            bytesSinceLastEmit += read
                            if (bytesSinceLastEmit >= PROGRESS_STEP_BYTES) {
                                emit(DownloadProgress.InProgress(bytesReceived, totalBytes))
                                bytesSinceLastEmit = 0
                            }
                        }
                    }
                }
                emit(DownloadProgress.InProgress(bytesReceived, totalBytes))
            } catch (readFailure: IOException) {
                throw UpdateError.Network(readFailure)
            }
        }

        emit(DownloadProgress.Done(target))
    }.flowOn(Dispatchers.IO)

    /** Efface l'APK téléchargé une fois installé ou abandonné. */
    fun clear() {
        File(context.cacheDir, DOWNLOAD_DIR_NAME).listFiles()?.forEach { it.delete() }
    }
}
