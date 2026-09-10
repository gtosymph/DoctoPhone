package com.kmt.healthanalyzer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmt.healthanalyzer.data.llm.LlmProvider
import com.kmt.healthanalyzer.data.preferences.AppPreferences
import com.kmt.healthanalyzer.data.repository.HealthRepository
import com.kmt.healthanalyzer.data.settings.ApiKeyStore
import com.kmt.healthanalyzer.data.work.HealthAnalyzerWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val provider: LlmProvider = LlmProvider.ANTHROPIC,
    val model: String = LlmProvider.ANTHROPIC.defaultModel,
    val sleepTargetMinutes: Int = 450,
    val providersWithKey: Set<LlmProvider> = emptySet(),
    val autoChecksEnabled: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val apiKeyStore: ApiKeyStore,
    private val repository: HealthRepository,
    private val workScheduler: HealthAnalyzerWorkScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                val withKey = providersWithKey()
                _state.update {
                    it.copy(
                        provider = settings.provider,
                        model = settings.model,
                        sleepTargetMinutes = settings.sleepTargetMinutes,
                        providersWithKey = withKey,
                        autoChecksEnabled = settings.autoChecksEnabled,
                    )
                }
            }
        }
    }

    fun selectProvider(provider: LlmProvider) {
        viewModelScope.launch { preferences.setProvider(provider) }
    }

    fun setModel(model: String) {
        viewModelScope.launch {
            runCatching { preferences.setModel(model) }
                .onFailure { failure -> _state.update { it.copy(message = failure.message) } }
        }
    }

    fun setSleepTargetMinutes(minutes: Int) {
        viewModelScope.launch {
            runCatching { preferences.setSleepTargetMinutes(minutes) }
                .onFailure { failure -> _state.update { it.copy(message = failure.message) } }
        }
    }

    fun saveApiKey(provider: LlmProvider, key: String) {
        viewModelScope.launch {
            try {
                apiKeyStore.setKey(provider, key)
                refreshKeys("La clé de ${provider.displayName} est enregistrée.")
            } catch (failure: IllegalArgumentException) {
                _state.update { it.copy(message = failure.message) }
            }
        }
    }

    fun clearApiKey(provider: LlmProvider) {
        viewModelScope.launch {
            apiKeyStore.clearKey(provider)
            refreshKeys("La clé de ${provider.displayName} est effacée.")
        }
    }

    /**
     * Active ou coupe la synchronisation nocturne et le bilan hebdomadaire automatiques.
     *
     * La demande de la permission `POST_NOTIFICATIONS` (Android 13+) reste du ressort de
     * l'écran — un `ViewModel` ne peut pas lancer de dialogue système — cette méthode ne
     * fait que persister le choix et (des)programmer les tâches en conséquence, que la
     * permission ait été accordée ou non : la synchronisation et l'affichage des dérives à
     * l'écran ne dépendent pas des notifications.
     */
    fun setAutoChecksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoChecksEnabled(enabled)
            if (enabled) workScheduler.schedule() else workScheduler.cancel()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            _state.update { it.copy(message = "Toutes les données locales sont effacées.") }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private suspend fun refreshKeys(message: String) {
        val withKey = providersWithKey()
        _state.update { it.copy(providersWithKey = withKey, message = message) }
    }

    /** Lit le magasin chiffré hors du thread principal : c'est un accès disque. */
    private suspend fun providersWithKey(): Set<LlmProvider> = withContext(Dispatchers.IO) {
        LlmProvider.entries.filter(apiKeyStore::hasKey).toSet()
    }
}
