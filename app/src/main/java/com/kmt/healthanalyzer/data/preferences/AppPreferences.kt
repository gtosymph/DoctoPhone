package com.kmt.healthanalyzer.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kmt.healthanalyzer.data.llm.LlmProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

/** Réglages de l'app, hors clés API qui vivent dans le magasin chiffré. */
data class AppSettings(
    val provider: LlmProvider,
    val model: String,
    val sleepTargetMinutes: Int,
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { stored ->
        val provider = stored[PROVIDER]
            ?.let { name -> LlmProvider.entries.firstOrNull { it.name == name } }
            ?: LlmProvider.ANTHROPIC
        AppSettings(
            provider = provider,
            model = stored[MODEL] ?: provider.defaultModel,
            sleepTargetMinutes = stored[SLEEP_TARGET] ?: DEFAULT_SLEEP_TARGET_MINUTES,
        )
    }

    suspend fun setProvider(provider: LlmProvider) {
        context.dataStore.edit { settings ->
            settings[PROVIDER] = provider.name
            // Le modèle par défaut suit le fournisseur : un modèle OpenAI n'a pas de sens chez Google.
            settings[MODEL] = provider.defaultModel
        }
    }

    suspend fun setModel(model: String) {
        require(model.isNotBlank()) { "Le nom du modèle ne peut pas être vide." }
        context.dataStore.edit { it[MODEL] = model.trim() }
    }

    suspend fun setSleepTargetMinutes(minutes: Int) {
        require(minutes in MIN_SLEEP_TARGET..MAX_SLEEP_TARGET) {
            "L'objectif de sommeil doit tenir entre ${MIN_SLEEP_TARGET / 60} et ${MAX_SLEEP_TARGET / 60} heures."
        }
        context.dataStore.edit { it[SLEEP_TARGET] = minutes }
    }

    /**
     * Horodatage (millis, epoch) de la dernière vérification automatique de mise à jour, ou
     * `null` si l'app n'en a encore lancé aucune. Sert à espacer les vérifications au
     * démarrage : voir `UpdateViewModel`.
     */
    suspend fun lastUpdateCheckEpochMillis(): Long? =
        context.dataStore.data.map { it[LAST_UPDATE_CHECK_EPOCH_MILLIS] }.first()

    suspend fun setLastUpdateCheckEpochMillis(epochMillis: Long) {
        context.dataStore.edit { it[LAST_UPDATE_CHECK_EPOCH_MILLIS] = epochMillis }
    }

    private companion object {
        val PROVIDER = stringPreferencesKey("llm_provider")
        val MODEL = stringPreferencesKey("llm_model")
        val SLEEP_TARGET = intPreferencesKey("sleep_target_minutes")
        val LAST_UPDATE_CHECK_EPOCH_MILLIS = longPreferencesKey("last_update_check_epoch_millis")

        const val DEFAULT_SLEEP_TARGET_MINUTES = 450
        const val MIN_SLEEP_TARGET = 240
        const val MAX_SLEEP_TARGET = 720
    }
}
