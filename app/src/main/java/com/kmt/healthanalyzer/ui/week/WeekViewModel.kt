package com.kmt.healthanalyzer.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.domain.drift.WeeklyReview
import com.kmt.healthanalyzer.domain.usecase.ReviewWeekUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * L'état de l'écran d'accueil.
 *
 * [hasAnyData] sépare deux situations qui se ressemblent à l'écran et ne veulent pas dire
 * la même chose : une app sans aucune mesure, qu'il faut mener vers l'import, et une app
 * alimentée dont la semaine est simplement calme.
 *
 * [coveredDays] et [lastMeasuredOn] sont des mentions de bas de page. Leur absence
 * n'empêche rien : elles valent `null` quand la base refuse de répondre, et le bilan reste
 * affiché.
 */
data class WeekUiState(
    val isLoading: Boolean = true,
    val review: WeeklyReview? = null,
    val errorMessage: String? = null,
    val hasAnyData: Boolean = true,
    val coveredDays: Int? = null,
    val lastMeasuredOn: LocalDate? = null,
)

/**
 * Alimente l'écran « Cette semaine » à chaque ouverture de l'app.
 *
 * ## La règle qui prime sur toutes les autres
 *
 * **Rien de ce qui se passe ici ne doit empêcher l'app de s'ouvrir.** C'est le premier
 * écran ; une exception qui remonte ferme l'application avant le moindre affichage. Le
 * précédent est réel : une contrainte de tâche de fond refusée par Android fermait l'app au
 * démarrage, et 450 tests verts n'avaient rien vu (voir `CLAUDE.md`).
 *
 * D'où deux niveaux de rattrapage distincts. Le bilan est la raison d'être de l'écran :
 * son échec s'affiche, nommément. L'état des données n'est qu'une mention de bas de page :
 * son échec est silencieux, et n'emporte pas le bilan avec lui.
 *
 * [CancellationException] est re-jetée dans les deux cas. Elle hérite d'`Exception` en
 * Kotlin, donc un `catch (Exception)` nu l'attrape et casse la concurrence structurée ;
 * elle survient normalement quand l'utilisateur quitte l'écran pendant le calcul, et la
 * transformer en message d'erreur poserait un texte rouge sur un écran déjà abandonné.
 *
 * ## Un seul calcul pour trois écrans
 *
 * [ReviewWeekUseCase] alimente aussi `DetectHealthDriftsUseCase`, donc l'écran de rapport
 * et la notification hebdomadaire. L'accueil ne peut pas contredire la notification.
 */
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val reviewWeek: ReviewWeekUseCase,
    private val repository: HealthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeekUiState())
    val state: StateFlow<WeekUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            loadCoverage()
            loadReview()
        }
    }

    private suspend fun loadReview() {
        try {
            val review = reviewWeek()
            _state.update { it.copy(isLoading = false, review = review, errorMessage = null) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    review = null,
                    errorMessage = "Le bilan de la semaine n'a pas pu être calculé : " +
                        (failure.message ?: "cause inconnue"),
                )
            }
        }
    }

    /**
     * La ligne d'état en bas de l'écran : y a-t-il des mesures, et sur quelle période.
     *
     * Volontairement muette en cas d'échec. Ce sont trois requêtes de comptage sur la base
     * locale ; si elles échouent, le bilan a de bonnes chances d'échouer aussi et de le
     * dire. Ajouter un second message ne ferait que doubler le bruit.
     */
    private suspend fun loadCoverage() {
        try {
            val count = repository.recordCount()
            val first = repository.firstRecordedDay()
            val last = repository.lastRecordedDay()
            val covered = if (first != null && last != null) {
                (ChronoUnit.DAYS.between(first, last) + 1).toInt()
            } else {
                null
            }
            _state.update { it.copy(hasAnyData = count > 0, coveredDays = covered, lastMeasuredOn = last) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            _state.update { it.copy(coveredDays = null, lastMeasuredOn = null) }
        }
    }
}
