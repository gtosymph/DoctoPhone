package com.kmt.healthanalyzer.data.settings

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Relie l'interface [ApiKeyStore] à son implémentation chiffrée [EncryptedApiKeyStore]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindApiKeyStore(impl: EncryptedApiKeyStore): ApiKeyStore
}
