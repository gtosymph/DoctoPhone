package com.kmt.healthanalyzer.ui.report

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmt.healthanalyzer.data.report.ReportExporter
import com.kmt.healthanalyzer.data.report.SummaryReview
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.domain.report.ReportModel
import com.kmt.healthanalyzer.domain.report.ReportNarrative
import com.kmt.healthanalyzer.domain.usecase.AnalyzeReportNarrativeUseCase
import com.kmt.healthanalyzer.domain.usecase.NarrativeResult
import com.kmt.healthanalyzer.domain.usecase.ReviewWeekUseCase
import com.kmt.healthanalyzer.ui.home.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * État de l'écran de rapport : le JSON déjà sérialisé, prêt à pousser dans la WebView.
 *
 * [narrativeRange] porte la période pour laquelle le récit courant a été rédigé. Quand
 * elle diffère de [range], le récit existe mais ne correspond plus à la période affichée :
 * il n'est alors pas mélangé au rapport montré, et l'écran peut le signaler.
 */
data class ReportUiState(
    val isLoading: Boolean = true,
    val range: TimeRange = TimeRange.MONTH,
    val reportJson: String? = null,
    val errorMessage: String? = null,
    val isExporting: Boolean = false,
    val exportError: String? = null,
    val isWritingNarrative: Boolean = false,
    val narrativeError: String? = null,
    val narrativeRange: TimeRange? = null,
)

/**
 * Calcule la période demandée puis fait rendre le [ReportModel] par
 * [HealthRepository.buildReport], avant de le sérialiser en JSON.
 *
 * La sérialisation se fait sur [serializationDispatcher], hors thread principal : un
 * rapport complet (17 graphiques, plusieurs mois de mesures) représente assez de texte
 * pour valoir la peine de ne pas bloquer l'UI, même brièvement.
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val zone: ZoneId,
    private val exporter: ReportExporter,
    private val analyzeNarrative: AnalyzeReportNarrativeUseCase,
    private val reviewWeek: ReviewWeekUseCase,
    @ReportSerializationDispatcher private val serializationDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val json = Json { encodeDefaults = true }

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    // Événement à usage unique : partager le fichier exporté. Un StateFlow rejouerait le
    // partage à chaque recomposition qui relit l'état ; un canal ne le fait qu'une fois.
    private val _shareEvents = Channel<Intent>(Channel.BUFFERED)
    val shareEvents: Flow<Intent> = _shareEvents.receiveAsFlow()

    // Le dernier rapport rendu, sans son récit : sert de base pour y injecter le récit dès
    // qu'il arrive, sans reconstruire le rapport ni rappeler HealthRepository.buildReport.
    private var currentModel: ReportModel? = null

    // Le récit déjà rédigé, gardé même après un changement de période — voir ReportUiState.narrativeRange.
    private var narrative: ReportNarrative? = null
    private var narrativeRange: TimeRange? = null

    init {
        refresh()
    }

    fun selectRange(range: TimeRange) {
        _state.update { it.copy(range = range) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val requestedRange = _state.value.range
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val span = dateRange(requestedRange, LocalDate.now(zone))
                val builtModel = repository.buildReport(span, zone)
                // Ne réapplique le récit gardé que s'il a été rédigé pour cette même période :
                // un récit écrit sur 30 jours n'a plus de sens une fois la période changée.
                val model = if (narrative != null && narrativeRange == requestedRange) {
                    builtModel.copy(narrative = narrative)
                } else {
                    builtModel
                }
                currentModel = model
                val reportJson = withContext(serializationDispatcher) { json.encodeToString(model) }
                _state.update {
                    it.copy(isLoading = false, reportJson = reportJson, narrativeRange = narrativeRange)
                }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "La construction du rapport a échoué : ${failure.message ?: "cause inconnue"}",
                    )
                }
            }
        }
    }

    /**
     * Écrit l'export HTML du rapport affiché et déclenche son partage.
     *
     * Repart du JSON déjà en état — pas d'un nouvel appel à [HealthRepository.buildReport] —
     * pour que le fichier exporté soit la copie exacte de ce que l'écran montre.
     */
    fun export() {
        val reportJson = _state.value.reportJson ?: return
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportError = null) }
            try {
                val file = exporter.export(reportJson)
                _shareEvents.send(exporter.shareIntent(file))
                _state.update { it.copy(isExporting = false) }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        isExporting = false,
                        exportError = "L'export a échoué : ${failure.message ?: "cause inconnue"}",
                    )
                }
            }
        }
    }


    /**
     * Écrit la synthèse d'une à deux pages destinée à un médecin, puis la partage.
     *
     * Deux différences avec [export], et elles comptent toutes les deux :
     *
     * - le document ne porte **aucun texte écrit par un modèle de langage**. Le bilan
     *   rédigé reste dans le rapport complet, où l'utilisateur sait d'où il vient ;
     * - il porte en revanche le bilan de la semaine écoulée, calculé par le même
     *   [ReviewWeekUseCase] que l'écran d'accueil et la notification hebdomadaire. Un seul
     *   calcul : le document remis au médecin ne peut pas contredire l'écran.
     *
     * Un échec du bilan hebdomadaire n'annule pas l'export. La synthèse s'ouvre alors sur
     * la période, sans ce bloc — un document amputé d'une section vaut mieux qu'un document
     * absent le jour du rendez-vous.
     */
    fun exportSummary() {
        val reportJson = _state.value.reportJson ?: return
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportError = null) }
            try {
                val reviewJson = withContext(serializationDispatcher) {
                    runCatching { json.encodeToString(SummaryReview.from(reviewWeek())) }.getOrNull()
                }
                val file = exporter.exportSummary(reportJson, reviewJson)
                _shareEvents.send(exporter.shareIntent(file))
                _state.update { it.copy(isExporting = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        isExporting = false,
                        exportError = "La synthèse n'a pas pu être produite : ${failure.message ?: "cause inconnue"}",
                    )
                }
            }
        }
    }

    /**
     * Fait rédiger le récit du rapport affiché par [AnalyzeReportNarrativeUseCase].
     *
     * Aucun prompt ne se construit ici : le cas d'usage porte seul cette responsabilité,
     * et lui seul lit le [ReportModel] pour l'envoyer au fournisseur de LLM.
     */
    fun writeNarrative() {
        val model = currentModel ?: return
        val requestedRange = _state.value.range
        viewModelScope.launch {
            _state.update { it.copy(isWritingNarrative = true, narrativeError = null) }
            when (val result = analyzeNarrative(model)) {
                is NarrativeResult.Success -> {
                    narrative = result.narrative
                    narrativeRange = requestedRange
                    // La période a pu changer pendant l'appel réseau : n'affiche le récit que
                    // s'il correspond encore à ce que l'écran montre au retour.
                    if (_state.value.range == requestedRange) {
                        val merged = model.copy(narrative = result.narrative)
                        currentModel = merged
                        val reportJson = withContext(serializationDispatcher) { json.encodeToString(merged) }
                        _state.update {
                            it.copy(isWritingNarrative = false, reportJson = reportJson, narrativeRange = requestedRange)
                        }
                    } else {
                        _state.update { it.copy(isWritingNarrative = false) }
                    }
                }
                is NarrativeResult.Failure -> {
                    _state.update { it.copy(isWritingNarrative = false, narrativeError = result.message) }
                }
            }
        }
    }

    internal companion object {
        /** La période couverte par le rapport : [TimeRange.days] jours se terminant aujourd'hui, inclus. */
        internal fun dateRange(range: TimeRange, today: LocalDate): ClosedRange<LocalDate> =
            today.minusDays(range.days - 1)..today
    }
}
