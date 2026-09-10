package com.kmt.healthanalyzer.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmt.healthanalyzer.domain.drift.MetricDrift
import com.kmt.healthanalyzer.domain.usecase.DetectHealthDriftsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État des dérives montrées en tête de l'écran de rapport.
 *
 * Indépendant de [ReportUiState] à dessein : les dérives portent toujours sur la même
 * fenêtre (référence personnelle contre semaine écoulée), jamais sur la période choisie
 * dans [PeriodSelector] — changer de période affichée ne doit pas recalculer les dérives.
 */
data class DriftUiState(
    val isLoading: Boolean = true,
    val drifts: List<MetricDrift> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Calcule les dérives à l'ouverture de l'écran de rapport, via le même
 * [DetectHealthDriftsUseCase] que `WeeklyDriftCheckWorker` : un seul chemin de calcul entre
 * l'écran et le bilan hebdomadaire en tâche de fond.
 */
@HiltViewModel
class DriftViewModel @Inject constructor(
    private val detectDrifts: DetectHealthDriftsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DriftUiState())
    val state: StateFlow<DriftUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val report = detectDrifts()
                _state.update { it.copy(isLoading = false, drifts = report.drifts) }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Le calcul des dérives a échoué : ${failure.message ?: "cause inconnue"}",
                    )
                }
            }
        }
    }
}
