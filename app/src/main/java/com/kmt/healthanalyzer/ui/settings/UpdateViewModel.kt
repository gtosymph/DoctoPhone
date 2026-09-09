package com.kmt.healthanalyzer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmt.healthanalyzer.BuildConfig
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.update.ApkDownloader
import com.kmt.healthanalyzer.data.update.DownloadProgress
import com.kmt.healthanalyzer.data.update.GitHubReleaseClient
import com.kmt.healthanalyzer.data.update.ReleaseInfo
import com.kmt.healthanalyzer.data.update.UpdateCheckResult
import com.kmt.healthanalyzer.data.update.UpdateError
import com.kmt.healthanalyzer.data.update.UpdateInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Espace deux vérifications automatiques d'au moins un jour, pour rester discrète. */
private val MIN_AUTO_CHECK_INTERVAL: Duration = Duration.ofDays(1)

private const val CHECK_FAILURE_MESSAGE = "La vérification de mise à jour a échoué."
private const val DOWNLOAD_FAILURE_MESSAGE = "Le téléchargement de la mise à jour a échoué."

/** État d'une vérification de mise à jour, montré dans la section « Mise à jour » des réglages. */
sealed class UpdateStatus {
    data object Idle : UpdateStatus()
    data object Checking : UpdateStatus()
    data object UpToDate : UpdateStatus()
    data class Available(val release: ReleaseInfo, val apkDownloadUrl: String) : UpdateStatus()
    data class Downloading(val bytesReceived: Long, val totalBytes: Long) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Failed(val message: String) : UpdateStatus()
}

data class UpdateUiState(
    val installedVersionName: String = BuildConfig.VERSION_NAME,
    val installedVersionCode: Int = BuildConfig.VERSION_CODE,
    val status: UpdateStatus = UpdateStatus.Idle,
)

/**
 * Porte la vérification, le téléchargement et l'installation d'une mise à jour de l'app.
 *
 * Ce ViewModel est partagé entre [com.kmt.healthanalyzer.ui.navigation.HealthAnalyzerNavHost],
 * qui le crée à portée de l'activité pour lancer la vérification discrète au démarrage, et
 * [SettingsScreen], qui réutilise la même instance pour montrer son état et proposer les
 * actions manuelles — sans relancer une seconde vérification à l'ouverture de l'écran.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val releaseClient: GitHubReleaseClient,
    private val apkDownloader: ApkDownloader,
    private val installer: UpdateInstaller,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { checkIfDue() }
    }

    /** Vérification manuelle, déclenchée par le bouton « Vérifier » : toujours immédiate. */
    fun checkNow() {
        viewModelScope.launch { check(silent = false) }
    }

    fun downloadAndInstall() {
        val available = _state.value.status as? UpdateStatus.Available ?: return
        viewModelScope.launch {
            apkDownloader.download(available.apkDownloadUrl, apkFileName(available.release))
                .catch { failure ->
                    _state.update { it.copy(status = UpdateStatus.Failed(failure.toUserMessage(DOWNLOAD_FAILURE_MESSAGE))) }
                }
                .collect { progress ->
                    _state.update { it.copy(status = progress.toUpdateStatus()) }
                }
        }
    }

    /** Vrai si l'app peut ouvrir l'installeur système directement, sans détour par les réglages. */
    fun canInstallPackages(): Boolean = installer.canInstallPackages()

    fun installIntentFor(apkFile: File) = installer.installIntent(apkFile)

    fun requestInstallPermissionIntent() = installer.requestInstallPermissionIntent()

    /**
     * Décide si une vérification automatique est due, puis la lance le cas échéant.
     *
     * C'est la toute première instruction du chemin de démarrage, avant même le client : un
     * fichier de préférences corrompu lève à la lecture, pas seulement à l'écriture (voir
     * [recordCheckAttempt]). Une lecture qui échoue est donc traitée comme une vérification
     * due plutôt que de laisser l'exception remonter — le pire cas est une vérification de
     * trop, jamais un plantage au démarrage.
     */
    private suspend fun checkIfDue() {
        val dueNow = try {
            val lastCheck = preferences.lastUpdateCheckEpochMillis()
            lastCheck == null ||
                Duration.between(Instant.ofEpochMilli(lastCheck), Instant.now()) >= MIN_AUTO_CHECK_INTERVAL
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (readFailure: Exception) {
            true
        }
        if (dueNow) check(silent = true)
    }

    /**
     * @param silent vrai pour la vérification automatique au démarrage : elle ne montre ni
     *   l'état « en cours », ni une éventuelle erreur — un réseau absent ne doit rien afficher.
     *   Silencieuse ou non, elle ne doit jamais laisser une exception imprévue remonter jusqu'à
     *   la coroutine de [init] : ce chemin s'exécute à l'ouverture de l'app, sans le moindre
     *   geste de l'utilisateur à incriminer si elle plantait (voir CLAUDE.md, « Les règles
     *   ProGuard portent du sens », pour le précédent réel de ce projet).
     */
    private suspend fun check(silent: Boolean) {
        if (!silent) _state.update { it.copy(status = UpdateStatus.Checking) }
        try {
            val result = releaseClient.checkForUpdate(_state.value.installedVersionCode)
            _state.update { it.copy(status = result.toUpdateStatus()) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // Attrape plus large qu'UpdateError : le client traduit les cas prévus, mais
            // « prévu » est justement ce qu'on ne sait pas d'avance. Une vérification
            // silencieuse qui plante l'app est pire qu'une vérification qui échoue.
            if (!silent) {
                _state.update { it.copy(status = UpdateStatus.Failed(failure.toUserMessage(CHECK_FAILURE_MESSAGE))) }
            }
        }
        recordCheckAttempt()
    }

    /**
     * Horodate la tentative, que la vérification ait réussi ou échoué — une panne réseau ne
     * doit pas déclencher une nouvelle tentative à chaque ouverture de l'app. Protégée à part :
     * cette écriture DataStore peut elle-même échouer (disque plein, fichier corrompu), et un
     * échec ici ne doit pas non plus faire tomber l'app. La prochaine ouverture retentera
     * simplement plus tôt que prévu.
     */
    private suspend fun recordCheckAttempt() {
        try {
            preferences.setLastUpdateCheckEpochMillis(System.currentTimeMillis())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (writeFailure: Exception) {
            // Rien à faire : voir la documentation de la fonction.
        }
    }

    private fun apkFileName(release: ReleaseInfo): String = release.apk

    private fun UpdateCheckResult.toUpdateStatus(): UpdateStatus = when (this) {
        UpdateCheckResult.UpToDate -> UpdateStatus.UpToDate
        is UpdateCheckResult.UpdateAvailable -> UpdateStatus.Available(release, apkDownloadUrl)
    }

    private fun DownloadProgress.toUpdateStatus(): UpdateStatus = when (this) {
        is DownloadProgress.InProgress -> UpdateStatus.Downloading(bytesReceived, totalBytes)
        is DownloadProgress.Done -> UpdateStatus.ReadyToInstall(file)
    }

    private fun Throwable.toUserMessage(fallback: String): String = (this as? UpdateError)?.message ?: fallback
}
