package com.kmt.healthanalyzer.data.update

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Fournit [GitHubReleaseClient] avec ses seules dépendances Hilt-gérées ; l'URL de base de
 * l'API et le validateur d'hôte gardent leur valeur par défaut de production (voir la
 * documentation de la classe pour pourquoi un constructeur `@Inject` ne conviendrait pas ici).
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideGitHubReleaseClient(httpClient: OkHttpClient, json: Json): GitHubReleaseClient =
        GitHubReleaseClient(httpClient, json)
}
