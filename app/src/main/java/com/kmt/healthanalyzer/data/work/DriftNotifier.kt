package com.kmt.healthanalyzer.data.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kmt.healthanalyzer.MainActivity
import com.kmt.healthanalyzer.R
import com.kmt.healthanalyzer.domain.drift.DriftNarrator
import com.kmt.healthanalyzer.domain.drift.DriftReport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prévient l'utilisateur d'une dérive, quand il y en a une à montrer.
 *
 * Seule classe du chantier qui touche à l'API de notification : `WeeklyDriftCheckWorker`
 * ne connaît que [DriftReport], jamais `NotificationManager` directement.
 *
 * Silencieuse par construction : sans la permission `POST_NOTIFICATIONS` (refusée, ou
 * jamais demandée), [notify] ne fait rien — ni exception, ni notification muette. Les
 * dérives restent visibles sur l'écran de rapport dans tous les cas ; seule l'alerte
 * proactive dépend de la permission.
 */
@Singleton
class DriftNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Toucher la notification ouvre l'app directement sur l'écran qui montre les dérives.
     *
     * `@SuppressLint` : la vérification a bien lieu, dans [hasNotificationPermission] ci-dessous
     * — lint ne relie pas ce garde-fou à l'appel `notify()` plus bas car les deux passent par
     * des méthodes séparées, et le signale à tort comme manquant.
     */
    @SuppressLint("MissingPermission")
    fun notify(report: DriftReport) {
        if (!hasNotificationPermission()) return
        ensureChannel()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val detailText = report.drifts.joinToString(separator = "\n") { DriftNarrator.describe(it) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_drift)
            .setContentTitle(title(report))
            .setContentText(summary(report))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun hasNotificationPermission(): Boolean {
        // La permission n'existe qu'à partir d'Android 13 (API 33) ; en dessous, poster
        // une notification n'a jamais demandé d'autorisation explicite.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        // Pas de garde de version ici : minSdk (29) est déjà au-dessus de la version qui a
        // introduit les canaux de notification (26) — la vérifier serait du code mort.
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Prévient quand une mesure de santé s'écarte durablement de vos habitudes."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun title(report: DriftReport): String =
        if (report.drifts.size == 1) "Bilan de la semaine : 1 mesure a changé" else "Bilan de la semaine : ${report.drifts.size} mesures ont changé"

    private fun summary(report: DriftReport): String = report.drifts.first().metric.label

    companion object {
        const val CHANNEL_ID = "health_drift_channel"
        const val CHANNEL_NAME = "Bilans de santé"
        private const val NOTIFICATION_ID = 1001
        private const val OPEN_APP_REQUEST_CODE = 2001
    }
}
