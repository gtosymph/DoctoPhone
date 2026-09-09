package com.kmt.healthanalyzer.data.repository

import com.kmt.healthanalyzer.domain.analysis.HealthPromptBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Zone horaire qui découpe les journées.
     *
     * L'app suit la zone de l'appareil : un utilisateur qui change de fuseau veut voir
     * ses journées dans le fuseau où il vit, pas dans celui de la mesure.
     */
    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    @Singleton
    fun providePromptBuilder(): HealthPromptBuilder = HealthPromptBuilder()
}
