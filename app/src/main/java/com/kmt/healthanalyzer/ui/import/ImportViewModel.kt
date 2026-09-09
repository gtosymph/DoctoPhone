package com.kmt.healthanalyzer.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectAvailabilityChecker
import com.kmt.healthanalyzer.data.healthconnect.HealthConnectStatus
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.data.samsung.ImportProgress
import com.kmt.healthanalyzer.data.samsung.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class ImportUiState(
    val isImporting: Boolean = false,
    val currentFile: String? = null,
    val progress: Float? = null,
    val summary: ImportSummary? = null,
    val errorMessage: String? = null,
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus.NOT_SUPPORTED,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    /** Vrai si [syncMessage] signale un problème (permission refusée, historique manquant, …). */
    val syncHasIssue: Boolean = false,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val availabilityChecker: HealthConnectAvailabilityChecker,
    private val zone: ZoneId,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(healthConnectStatus = availabilityChecker.status()) }
    }

    /** Importe le dossier d'export produit par Samsung Health sur le téléphone. */
    fun importDirectory(treeUri: Uri) = runImport { repository.importSamsungDirectory(treeUri) }

    /** Importe une archive compressée de ce même dossier. */
    fun importArchive(uri: Uri) = runImport { repository.importSamsungArchive(uri) }

    private fun runImport(source: () -> kotlinx.coroutines.flow.Flow<ImportProgress>) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorMessage = null, summary = null) }
            try {
                source().collect { progress ->
                    when (progress) {
                        is ImportProgress.Reading -> _state.update {
                            it.copy(currentFile = progress.fileName, progress = progress.fraction)
                        }
                        is ImportProgress.Finished -> _state.update {
                            it.copy(isImporting = false, summary = progress.summary, progress = 1f)
                        }
                    }
                }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = failure.message ?: "L'import a échoué.",
                    )
                }
            }
        }
    }

    /** Reprend dans Health Connect ce qui suit le dernier jour déjà en base. */
    fun syncHealthConnect() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncMessage = null, syncHasIssue = false) }
            try {
                val from = repository.lastRecordedDay()
                    ?.plusDays(1)
                    ?.atStartOfDay(zone)
                    ?.toInstant()
                    ?: Instant.now().minusSeconds(DEFAULT_SYNC_WINDOW_SECONDS)

                val result = repository.syncFromHealthConnect(from)
                _state.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = result.toUserMessage(),
                        syncHasIssue = result.hasIssue,
                    )
                }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "La synchronisation a échoué : ${failure.message ?: "cause inconnue"}",
                        syncHasIssue = true,
                    )
                }
            }
        }
    }

    fun refreshHealthConnectStatus() {
        _state.update { it.copy(healthConnectStatus = availabilityChecker.status()) }
    }

    private companion object {
        /** Sans donnée locale, la première synchronisation remonte à 30 jours. */
        const val DEFAULT_SYNC_WINDOW_SECONDS = 30L * 24 * 3600
    }
}
