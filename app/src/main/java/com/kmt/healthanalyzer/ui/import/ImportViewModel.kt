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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    /**
     * Met la base à jour depuis Health Connect.
     *
     * Deux chemins, et la distinction compte. Quand la base porte déjà des mesures, on reprend
     * simplement après le dernier jour connu : c'est court et fréquent. Quand elle est vide,
     * on va chercher **tout l'historique disponible**.
     *
     * Ce second cas corrige un défaut réel signalé sur l'appareil de l'utilisateur : la
     * première synchronisation ne demandait que 30 jours, en dur. Il choisissait « 1 an » dans
     * le rapport et n'y voyait qu'un mois. Le rapport lit la base locale — une base courte ne
     * peut pas produire un rapport long, quelle que soit la période choisie à l'écran.
     */
    fun syncHealthConnect() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncMessage = null, syncHasIssue = false) }
            try {
                val lastDay = repository.lastRecordedDay()
                val result = if (lastDay == null) {
                    fullHistorySync()
                } else {
                    repository.syncFromHealthConnect(lastDay.plusDays(1).atStartOfDay(zone).toInstant())
                }
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

    /**
     * Redemande tout l'historique, même quand la base porte déjà des mesures.
     *
     * Utile après avoir accordé la permission d'historique : les permissions Health Connect ne
     * se demandent qu'une fois, et quelqu'un qui avait autorisé l'app avant l'existence de
     * cette permission ne l'a jamais accordée. Sa base reste alors courte, et rien à l'écran
     * ne lui dit qu'un second geste la remplirait.
     */
    fun syncAllHealthConnect() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncMessage = null, syncHasIssue = false) }
            try {
                val result = fullHistorySync()
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
                        syncMessage = "La récupération de l'historique a échoué : " +
                            (failure.message ?: "cause inconnue"),
                        syncHasIssue = true,
                    )
                }
            }
        }
    }

    /**
     * Lance la marche arrière par tranches et montre où elle en est.
     *
     * L'avancement n'est pas cosmétique : remonter plusieurs années prend du temps, et un
     * écran figé sans un mot ressemble à une panne. Montrer le mois en cours de lecture dit à
     * la fois que le travail avance et jusqu'où il est descendu.
     */
    private suspend fun fullHistorySync() = repository.syncAllFromHealthConnect { day ->
        _state.update { it.copy(syncMessage = "Récupération de l'historique… ${MONTH_FORMAT.format(day)}") }
    }

    fun refreshHealthConnectStatus() {
        _state.update { it.copy(healthConnectStatus = availabilityChecker.status()) }
    }

    private companion object {
        /** Mois de la tranche en cours de lecture, montré pendant la marche arrière. */
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)
    }
}
