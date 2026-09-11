package com.kmt.healthanalyzer.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kmt.healthanalyzer.ui.analysis.AnalysisScreen
import com.kmt.healthanalyzer.ui.importer.ImportScreen
import com.kmt.healthanalyzer.ui.report.ReportScreen
import com.kmt.healthanalyzer.ui.settings.SettingsScreen
import com.kmt.healthanalyzer.ui.settings.UpdateViewModel
import com.kmt.healthanalyzer.ui.week.WeekScreen

/**
 * Durée de la traversée entre onglets : assez courte pour rester rapide, assez longue pour
 * qu'un doigt qui glisse d'un onglet à l'autre voie le changement, pas un saut.
 */
private const val TAB_TRANSITION_MILLIS = 180

/**
 * Une destination de la barre de navigation principale.
 *
 * **Trois onglets, et c'est une décision.** L'import en occupait un quatrième alors qu'on
 * l'utilise une ou deux fois dans la vie de l'app : Health Connect se synchronise ensuite
 * tout seul, chaque nuit. Les réglages ne s'ouvrent guère plus souvent. Les deux gardent
 * leur route — rien n'est devenu inaccessible — mais ils se rejoignent depuis l'en-tête de
 * l'accueil et depuis le bandeau de premier usage, pas depuis une place permanente dans la
 * barre.
 */
private enum class HealthDestination(val route: String, val label: String) {
    WEEK("semaine", "Cette semaine"),
    REPORT("rapport", "Rapport"),
    ANALYSIS("analyse", "Analyse"),
}

/** Routes atteignables sans onglet : elles ne servent qu'à certains moments. */
private const val ROUTE_SETTINGS = "reglages"
private const val ROUTE_IMPORT = "import"

/** Coquille de navigation : trois onglets, « Cette semaine » au démarrage. */
@Composable
fun HealthAnalyzerNavHost() {
    val navController = rememberNavController()

    // Portée de l'activité (voir sa position, hors de tout `composable{}` de la barre de
    // navigation) : une seule instance pour toute la session, qui lance sa vérification de
    // mise à jour discrète dès sa création — donc au démarrage de l'app, quel que soit
    // l'onglet ouvert — puis la réutilise quand l'utilisateur atteint les réglages.
    val updateViewModel: UpdateViewModel = hiltViewModel()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination

            NavigationBar {
                HealthDestination.entries.forEach { destination ->
                    val selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.switchTo(destination.route) },
                        // `contentDescription` reste nul : le libellé visible ci-dessous porte déjà
                        // le nom de l'onglet, TalkBack fusionne les deux — le doubler l'annoncerait deux fois.
                        icon = { Icon(destination.icon(), contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)
        NavHost(
            navController = navController,
            startDestination = HealthDestination.WEEK.route,
            // Une traversée sobre entre onglets : un fondu-glissé bref, jamais un saut brut
            // ni une animation assez longue pour ralentir la navigation.
            enterTransition = {
                fadeIn(tween(TAB_TRANSITION_MILLIS)) +
                    slideInHorizontally(tween(TAB_TRANSITION_MILLIS)) { it / 12 }
            },
            exitTransition = { fadeOut(tween(TAB_TRANSITION_MILLIS)) },
            popEnterTransition = {
                fadeIn(tween(TAB_TRANSITION_MILLIS)) +
                    slideInHorizontally(tween(TAB_TRANSITION_MILLIS)) { -it / 12 }
            },
            popExitTransition = { fadeOut(tween(TAB_TRANSITION_MILLIS)) },
        ) {
            composable(HealthDestination.WEEK.route) {
                WeekScreen(
                    modifier = screenModifier,
                    onOpenReport = { navController.switchTo(HealthDestination.REPORT.route) },
                    onOpenSettings = { navController.switchTo(ROUTE_SETTINGS) },
                    onOpenImport = { navController.switchTo(ROUTE_IMPORT) },
                )
            }
            composable(HealthDestination.REPORT.route) { ReportScreen(modifier = screenModifier) }
            composable(HealthDestination.ANALYSIS.route) {
                AnalysisScreen(
                    modifier = screenModifier,
                    onOpenSettings = { navController.switchTo(ROUTE_SETTINGS) },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    modifier = screenModifier,
                    updateViewModel = updateViewModel,
                    onOpenImport = { navController.switchTo(ROUTE_IMPORT) },
                )
            }
            composable(ROUTE_IMPORT) { ImportScreen(modifier = screenModifier) }
        }
    }
}

/**
 * Bascule vers une destination sans empiler l'historique.
 *
 * `saveState` et `restoreState` gardent la position de défilement et l'état de chaque écran
 * quitté : revenir sur le rapport après un détour par les réglages ne doit pas le
 * reconstruire ni le ramener en haut.
 */
private fun NavHostController.switchTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun HealthDestination.icon() = when (this) {
    HealthDestination.WEEK -> Icons.Default.CalendarToday
    HealthDestination.REPORT -> Icons.Default.Insights
    HealthDestination.ANALYSIS -> Icons.Default.AutoAwesome
}
