package tools.mo3ta.fitit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tools.mo3ta.fitit.ui.HomeScreen
import tools.mo3ta.fitit.ui.emptytext.EmptyTextScreen
import tools.mo3ta.fitit.ui.openwa.OpenWaScreen
import tools.mo3ta.fitit.ui.textimage.TextImageScreen
import tools.mo3ta.fitit.ui.theme.FititTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FititTheme(darkTheme = true) {
                FitItApp()
            }
        }
    }
}

@Composable
fun FitItApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToTextImage = { navController.navigate("text_image") },
                onNavigateToEmptyText = { navController.navigate("empty_text") },
                onNavigateToOpenWa = { navController.navigate("open_wa") }
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
    }
}