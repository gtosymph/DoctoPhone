package com.kmt.healthanalyzer.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmt.healthanalyzer.data.db.dao.ChatMessageDao
import com.kmt.healthanalyzer.data.db.entity.ChatMessageEntity
import com.kmt.healthanalyzer.data.db.entity.ChatRole
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.domain.usecase.ChatContext
import com.kmt.healthanalyzer.domain.usecase.ChatResult
import com.kmt.healthanalyzer.ui.state.StateAction
import com.kmt.healthanalyzer.ui.state.StateCopy
import com.kmt.healthanalyzer.domain.usecase.ChatTurn
import com.kmt.healthanalyzer.domain.usecase.ChatWithHealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État de l'écran de conversation.
 *
 * [reportModelJson] est le `ReportModel` de **tout** l'historique, déjà sérialisé :
 * l'écran le sert à la WebView par interception (`shouldInterceptRequest`, voir
 * `AnalysisScreen.kt` et `ChatJsBridge.kt` pour la mesure qui motive ce choix), pour que
 * `chart-catalog.js` puisse résoudre les graphiques que l'assistant désigne par
 * référence. Il ne quitte jamais la WebView locale — voir `ChatWithHealthUseCase`. Ce
 * n'est jamais la fenêtre active : chaque graphique se résout avec la fenêtre gravée sur
 * son propre message (`ChatMessageEntity.rangeFrom`/`rangeTo`), pas avec celle du moment
 * — voir `chat-view.js` (`messageRange`).
 *
 * [statusMessage] est la ligne d'état facultative qui dit quelle période le modèle a
 * examinée pour la dernière réponse, quand il a utilisé `healthrange` — voir
 * [ChatResult.Success.statusNote]. `null` s'il n'a pas eu besoin de changer de fenêtre.
 *
 * [isLoadingContext] couvre le tout premier chargement (historique + rapport), bloquant
 * la saisie. [isRefreshingContext] couvre les rechargements silencieux qui suivent, quand
 * un import a changé le nombre d'enregistrements : la saisie reste utilisable pendant ce
 * temps, seul un indicateur discret le signale.
 */
data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val reportModelJson: String? = null,
    val statusMessage: String? = null,
    val draft: String = "",
    val isLoadingContext: Boolean = true,
    val isRefreshingContext: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    /** Le geste que l'écran propose sous le message d'échec. Voir `StateCopy`. */
    val errorAction: StateAction = StateAction.NONE,
    val providerName: String = "",
)

/**
 * Pilote la conversation de l'onglet Analyse.
 *
 * [onScreenVisible] charge l'historique et le contexte du rapport la première fois, puis
 * les recharge à chaque fois que l'écran redevient visible **si** [HealthRepository.recordCount]
 * a changé depuis le dernier chargement — signe qu'un import a eu lieu entretemps. Un
 * compte inchangé ne redéclenche rien : le [ChatContext] reste en mémoire ([currentContext])
 * et sert tel quel à chaque tour de conversation, sans reconstruction. Un rechargement
 * remet à zéro la fenêtre que le modèle avait choisie : les données ayant changé, mieux
 * vaut la lui laisser rechoisir plutôt que de garder une fenêtre potentiellement périmée.
 *
 * Chaque message envoyé et chaque réponse reçue sont écrits dans [ChatMessageDao] dès
 * qu'ils existent — la conversation survit à une fermeture de l'app. Aucun prompt ne se
 * construit ici : [ChatWithHealthUseCase] porte seul cette responsabilité, cette classe
 * ne fait que lui passer l'historique et ranger le résultat.
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val chatWithHealth: ChatWithHealthUseCase,
    private val repository: HealthRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    // Le contexte courant (rapport sur tout l'historique + fenêtre active choisie par le
    // modèle), gardé pour le repasser à chaque tour — voir ChatWithHealthUseCase.invoke.
    private var currentContext: ChatContext? = null

    // Le compte d'enregistrements au dernier chargement du contexte ; `null` avant le premier.
    private var lastKnownRecordCount: Int? = null

    private var historyLoaded = false

    /**
     * L'envoi en cours, gardé pour pouvoir y renoncer.
     *
     * Un appel au fournisseur peut prendre une demi-minute, parfois davantage. Sans ce
     * fil, la seule sortie serait de quitter l'écran, ce qui laisse l'appel courir et la
     * réponse arriver dans le vide.
     */
    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = preferences.settings.first()
            _state.update { it.copy(providerName = settings.provider.displayName) }
        }
    }

    /**
     * À appeler chaque fois que l'écran redevient visible (voir `LaunchedEffect` dans
     * `AnalysisScreen`).
     *
     * Charge l'historique une seule fois. Recharge le contexte du rapport seulement si
     * [HealthRepository.recordCount] a changé depuis le dernier chargement — l'utilisateur
     * ne doit jamais dialoguer avec un rapport périmé sans le savoir, ni voir le rapport
     * se reconstruire à chaque fois qu'il revient sur l'onglet sans raison.
     */
    fun onScreenVisible() {
        viewModelScope.launch {
            if (!historyLoaded) {
                historyLoaded = true
                _state.update { it.copy(messages = chatMessageDao.all()) }
            }

            val recordCount = try {
                repository.recordCount()
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        isLoadingContext = false,
                        errorMessage = StateCopy.forFailure(failure).message,
                        errorAction = StateCopy.forFailure(failure).action,
                    )
                }
                return@launch
            }

            if (recordCount == lastKnownRecordCount) {
                _state.update { it.copy(isLoadingContext = false) }
                return@launch
            }

            val isFirstLoad = lastKnownRecordCount == null
            if (!isFirstLoad) {
                _state.update { it.copy(isRefreshingContext = true) }
            }

            try {
                val context = chatWithHealth.loadContext()
                currentContext = context
                lastKnownRecordCount = recordCount
                _state.update {
                    it.copy(
                        reportModelJson = context.historyReportJson,
                        statusMessage = null,
                        isLoadingContext = false,
                        isRefreshingContext = false,
                        errorMessage = null,
                        errorAction = StateAction.NONE,
                    )
                }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        isLoadingContext = false,
                        isRefreshingContext = false,
                        errorMessage = StateCopy.forFailure(failure).message,
                        errorAction = StateCopy.forFailure(failure).action,
                    )
                }
            }
        }
    }

    fun setDraft(text: String) = _state.update { it.copy(draft = text) }

    /**
     * Envoie le brouillon courant : l'écrit tout de suite dans l'historique affiché et en
     * base, puis attend la réponse du modèle avant de l'y ajouter à son tour.
     *
     * Un message utilisateur déjà envoyé reste dans l'historique même si l'appel au
     * modèle échoue ensuite : seule la réponse manque, la question, elle, a bien eu lieu.
     *
     * Si le contexte de santé n'a pas encore pu se charger, l'échec se dit tout de suite
     * dans la conversation plutôt que de laisser l'utilisateur poser une question qui
     * échouerait silencieusement.
     */
    fun send() {
        val question = _state.value.draft.trim()
        if (question.isEmpty() || _state.value.isSending) return

        val context = currentContext
        if (context == null) {
            _state.update {
                it.copy(
                    errorMessage = "Vos données ne sont pas encore prêtes. Réessayez dans un instant.",
                    errorAction = StateAction.RETRY,
                )
            }
            return
        }

        sendJob = viewModelScope.launch {
            _state.update {
                it.copy(isSending = true, errorMessage = null, errorAction = StateAction.NONE, draft = "")
            }

            val userMessage = ChatMessageEntity(
                role = ChatRole.USER,
                content = question,
                createdAtEpochMillis = Instant.now().toEpochMilli(),
            )
            val userId = chatMessageDao.insert(userMessage)
            val messagesWithUser = _state.value.messages + userMessage.copy(id = userId)
            _state.update { it.copy(messages = messagesWithUser) }

            val history = messagesWithUser.map { ChatTurn(role = it.role, content = it.content) }
            when (val result = chatWithHealth(history, context)) {
                is ChatResult.Success -> {
                    currentContext = result.context
                    // La fenêtre active au moment de CETTE réponse, gravée sur le message —
                    // voir ChatMessageEntity.rangeFrom/rangeTo. `null` tant qu'aucune
                    // `healthrange` n'a encore été choisie : un graphique de ce message se
                    // résout alors sur tout l'historique, comme avant ce mécanisme.
                    val activeMeta = result.context.activeReport?.meta
                    val assistantMessage = ChatMessageEntity(
                        role = ChatRole.ASSISTANT,
                        content = result.reply,
                        createdAtEpochMillis = Instant.now().toEpochMilli(),
                        rangeFrom = activeMeta?.from,
                        rangeTo = activeMeta?.to,
                    )
                    val assistantId = chatMessageDao.insert(assistantMessage)
                    _state.update {
                        it.copy(
                            isSending = false,
                            messages = it.messages + assistantMessage.copy(id = assistantId),
                            statusMessage = result.statusNote,
                        )
                    }
                }
                is ChatResult.Failure -> {
                    val copy = StateCopy.forFailure(result.cause)
                    _state.update {
                        it.copy(isSending = false, errorMessage = copy.message, errorAction = copy.action)
                    }
                }
            }
        }
    }

    /**
     * Renonce à l'envoi en cours.
     *
     * La question déjà écrite en base reste : elle a bien été posée, et l'effacer donnerait
     * le sentiment que l'app a perdu ce qu'on venait de taper. Renoncer n'est pas un échec,
     * donc aucun message d'erreur ne s'affiche — l'écran revient simplement à son repos.
     */
    fun cancelSend() {
        sendJob?.cancel()
        sendJob = null
        if (_state.value.isSending) {
            _state.update { it.copy(isSending = false) }
        }
    }

    /** Efface la conversation, en base et à l'écran. L'écran confirme avant d'appeler ceci. */
    fun clearConversation() {
        viewModelScope.launch {
            chatMessageDao.deleteAll()
            _state.update { it.copy(messages = emptyList(), errorMessage = null, errorAction = StateAction.NONE) }
        }
    }
}
