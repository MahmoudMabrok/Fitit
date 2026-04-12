package tools.mo3ta.fitit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tools.mo3ta.fitit.data.ThemeMode
import tools.mo3ta.fitit.ui.HomeScreen
import tools.mo3ta.fitit.ui.emptytext.EmptyTextScreen
import tools.mo3ta.fitit.ui.onboarding.OnboardingScreen
import tools.mo3ta.fitit.ui.openwa.OpenWaScreen
import tools.mo3ta.fitit.ui.settings.SettingsScreen
import tools.mo3ta.fitit.ui.settings.SettingsViewModel
import tools.mo3ta.fitit.ui.textimage.TextImageScreen
import tools.mo3ta.fitit.ui.theme.FititTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            // Force RTL for the whole app as it's forced Arabic
            // Default strings in strings.xml are now in Arabic.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FitItApp()
            }
        }
    }

    @Composable
    fun FitItApp() {
        val navController = rememberNavController()
        val settingsViewModel: SettingsViewModel = viewModel()
        val themeMode by settingsViewModel.themeMode.collectAsState()

        // Notification permission is requested in HomeScreen after onboarding

        // Determine dark theme based on settings
        val systemDarkTheme = isSystemInDarkTheme()
        val darkTheme = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDarkTheme
        }

        FititTheme(darkTheme = darkTheme) {
            NavHost(navController = navController, startDestination = "onboarding") {
                composable("onboarding") {
                    OnboardingScreen(onGetStarted = {
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    })
                }
                composable("home") {
                    HomeScreen(
                        onNavigateToTextImage = { navController.navigate("text_image") },
                        onNavigateToEmptyText = { navController.navigate("empty_text") },
                        onNavigateToOpenWa = { navController.navigate("open_wa") },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }
                composable("text_image") {
                    TextImageScreen(onBack = { navController.popBackStack() })
                }
                composable("empty_text") {
                    EmptyTextScreen(onBack = { navController.popBackStack() })
                }
                composable("open_wa") {
                    OpenWaScreen(onBack = { navController.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
