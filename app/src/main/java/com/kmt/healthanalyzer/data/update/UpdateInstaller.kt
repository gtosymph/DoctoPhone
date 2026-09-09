package com.kmt.healthanalyzer.data.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/**
 * Ouvre l'installeur système pour l'APK déjà téléchargé par [ApkDownloader].
 *
 * Ne tente jamais une installation silencieuse : l'app n'est pas un installeur système, et
 * l'utilisateur doit voir ce qu'il installe. [installIntent] passe par
 * `ACTION_VIEW` : Android fait alors apparaître son propre écran de confirmation, c'est voulu.
 *
 * Ce [context] est `@ApplicationContext`, pas celui d'une Activity : `startActivity` sur une
 * intention sans `FLAG_ACTIVITY_NEW_TASK` lève `AndroidRuntimeException` si l'appelant ne
 * fournit pas lui-même un contexte d'Activity. Les deux intentions ci-dessous portent donc ce
 * drapeau, pour rester sûres quel que soit l'appelant — aujourd'hui seul `SettingsScreen` les
 * lance, depuis `LocalContext.current`, qui est déjà un contexte d'Activity.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Faux si l'app n'a pas l'autorisation d'installer des paquets venus d'une source inconnue. */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Écran système où l'utilisateur accorde l'autorisation d'installation pour cette app. */
    fun requestInstallPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Intention qui ouvre l'installeur système pour [apkFile].
     *
     * L'autorité `packageName.fileprovider` doit correspondre à celle déclarée dans
     * `AndroidManifest.xml` : les deux dérivent du même `applicationId`. Voir
     * `res/xml/file_paths.xml` pour le sous-dossier du cache exposé.
     */
    fun installIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
