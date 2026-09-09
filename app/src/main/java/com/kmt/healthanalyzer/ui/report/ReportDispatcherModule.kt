package com.kmt.healthanalyzer.ui.report

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Distingue le dispatcher utilisé pour sérialiser le rapport en JSON.
 *
 * Un `Dispatchers.Default` codé en dur dans [ReportViewModel] échapperait au
 * `TestCoroutineScheduler` virtuel de `runTest` : le test avancerait avant que la
 * sérialisation, exécutée sur un vrai thread, ait fini. Injecter le dispatcher permet aux
 * tests de le remplacer par leur propre dispatcher de test, et de rester déterministes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReportSerializationDispatcher

@Module
@InstallIn(SingletonComponent::class)
object ReportDispatcherModule {

    @Provides
    @ReportSerializationDispatcher
    fun provideReportSerializationDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
