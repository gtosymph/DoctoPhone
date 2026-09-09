package com.kmt.healthanalyzer.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kmt.healthanalyzer.data.llm.LlmProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFERENCES_FILE_NAME = "llm_api_keys"

/**
 * Stocke et restitue les clés API des fournisseurs LLM choisies par l'utilisateur.
 *
 * Une clé n'est jamais journalisée ni transmise ailleurs qu'au fournisseur pour lequel
 * elle a été enregistrée.
 */
interface ApiKeyStore {
    /** Renvoie la clé enregistrée pour [provider], ou `null` si aucune n'a été fournie. */
    suspend fun key(provider: LlmProvider): String?

    /**
     * Enregistre [key] pour [provider].
     *
     * @throws IllegalArgumentException si [key] est vide ou ne contient que des espaces.
     */
    suspend fun setKey(provider: LlmProvider, key: String)

    /** Efface la clé enregistrée pour [provider], si elle existe. */
    suspend fun clearKey(provider: LlmProvider)

    /** Indique si une clé est enregistrée pour [provider], sans exposer sa valeur. */
    fun hasKey(provider: LlmProvider): Boolean
}

/**
 * Implémentation de [ApiKeyStore] appuyée sur [EncryptedSharedPreferences], chiffrée avec une
 * [MasterKey] AES256-GCM générée et conservée par l'Android Keystore.
 */
@Singleton
class EncryptedApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : ApiKeyStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun key(provider: LlmProvider): String? = withContext(Dispatchers.IO) {
        preferences.getString(provider.preferenceKey, null)
    }

    override suspend fun setKey(provider: LlmProvider, key: String) {
        require(key.isNotBlank()) { "La clé API ne peut pas être vide." }
        withContext(Dispatchers.IO) {
            preferences.edit().putString(provider.preferenceKey, key).apply()
        }
    }

    override suspend fun clearKey(provider: LlmProvider) {
        withContext(Dispatchers.IO) {
            preferences.edit().remove(provider.preferenceKey).apply()
        }
    }

    override fun hasKey(provider: LlmProvider): Boolean = preferences.contains(provider.preferenceKey)

    private val LlmProvider.preferenceKey: String get() = name
}
