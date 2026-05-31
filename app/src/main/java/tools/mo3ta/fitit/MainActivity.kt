package tools.mo3ta.fitit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tools.mo3ta.fitit.analytics.AnalyticsManager
import tools.mo3ta.fitit.data.ThemeMode
import tools.mo3ta.fitit.ui.HomeScreen
import tools.mo3ta.fitit.ui.emptytext.EmptyTextScreen
import tools.mo3ta.fitit.ui.mediamerger.MediaMergerScreen
import tools.mo3ta.fitit.ui.onboarding.OnboardingScreen
import tools.mo3ta.fitit.ui.openwa.OpenWaScreen
import tools.mo3ta.fitit.ui.settings.SettingsScreen
import tools.mo3ta.fitit.ui.settings.SettingsViewModel
import tools.mo3ta.fitit.ui.textimage.TextImageScreen
import tools.mo3ta.fitit.ui.textsplitter.TextSplitterScreen
import tools.mo3ta.fitit.ui.videosplitter.VideoSplitterScreen
import tools.mo3ta.fitit.ui.videoenhancer.VideoEnhancerScreen
import tools.mo3ta.fitit.ui.audioextractor.AudioExtractorScreen
import tools.mo3ta.fitit.ui.audioenhancer.AudioEnhancerScreen
import tools.mo3ta.fitit.ui.theme.FititTheme

class MainActivity : ComponentActivity() {

    // Holds a route a notification asked us to open; consumed once the nav graph is ready.
    private val pendingDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingDestination.value = intent?.getStringExtra(EXTRA_DESTINATION)

        enableEdgeToEdge()
        AnalyticsManager.trackAppOpen()
        setContent {
            FitItApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // App was already running: pick up the notification's requested destination.
        pendingDestination.value = intent.getStringExtra(EXTRA_DESTINATION)
    }

    @Composable
    fun FitItApp() {
        val navController = rememberNavController()
        val settingsViewModel: SettingsViewModel = viewModel()
        val themeMode by settingsViewModel.themeMode.collectAsState()

        // Deep link from a notification: jump to the requested screen once, with Home underneath so
        // Back lands somewhere sensible. Runs after the NavHost has composed its graph.
        val destination by pendingDestination
        LaunchedEffect(destination) {
            if (destination == DEST_VIDEO_ENHANCER) {
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                    launchSingleTop = true
                }
                navController.navigate("video_enhancer") { launchSingleTop = true }
                pendingDestination.value = null
            }
        }

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
                        onNavigateToTextSplitter = { navController.navigate("text_splitter") },
                        onNavigateToVideoSplitter = { navController.navigate("video_splitter") },
                        onNavigateToVideoEnhancer = { navController.navigate("video_enhancer") },
                        onNavigateToMediaMerger = { navController.navigate("media_merger") },
                        onNavigateToAudioExtractor = { navController.navigate("audio_extractor") },
                        onNavigateToAudioEnhancer = { navController.navigate("audio_enhancer") },
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
                composable("text_splitter") {
                    TextSplitterScreen(onBack = { navController.popBackStack() })
                }
                composable("video_splitter") {
                    VideoSplitterScreen(onBack = { navController.popBackStack() })
                }
                composable("video_enhancer") {
                    VideoEnhancerScreen(onBack = { navController.popBackStack() })
                }
                composable("media_merger") {
                    MediaMergerScreen(onBack = { navController.popBackStack() })
                }
                composable("audio_extractor") {
                    AudioExtractorScreen(onBack = { navController.popBackStack() })
                }
                composable("audio_enhancer") {
                    AudioEnhancerScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    companion object {
        /** Intent extra naming a screen to deep-link to (e.g. from a notification). */
        const val EXTRA_DESTINATION = "destination"
        const val DEST_VIDEO_ENHANCER = "video_enhancer"
    }
}
