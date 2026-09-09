package com.kmt.healthanalyzer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kmt.healthanalyzer.ui.navigation.HealthAnalyzerNavHost
import com.kmt.healthanalyzer.ui.rationale.PermissionsRationaleScreen
import com.kmt.healthanalyzer.ui.theme.HealthAnalyzerTheme
import dagger.hilt.android.AndroidEntryPoint

/** Action envoyée par Health Connect (Android 14+) pour demander l'écran de justification. */
private const val ACTION_SHOW_PERMISSIONS_RATIONALE = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"

/** Action envoyée par le système (Android 13 et avant) pour la même demande. */
private const val ACTION_VIEW_PERMISSION_USAGE = "android.intent.action.VIEW_PERMISSION_USAGE"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val showRationale = intent.isPermissionsRationaleRequest()

        setContent {
            HealthAnalyzerTheme {
                if (showRationale) {
                    PermissionsRationaleScreen()
                } else {
                    HealthAnalyzerNavHost()
                }
            }
        }
    }
}

private fun Intent?.isPermissionsRationaleRequest(): Boolean =
    this?.action == ACTION_SHOW_PERMISSIONS_RATIONALE || this?.action == ACTION_VIEW_PERMISSION_USAGE
