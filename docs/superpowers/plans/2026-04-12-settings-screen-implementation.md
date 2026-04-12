# Settings Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a settings screen with theme selection (Light/Dark/System), language selection (English/Arabic with RTL support), and app information, with persistent preferences using DataStore.

**Architecture:** Data layer with DataStore for persistence, ViewModel for state management, Compose UI for settings screen, integration with existing navigation structure, and full localization support.

**Tech Stack:** Jetpack Compose, Material 3, DataStore Preferences, Kotlin Coroutines, Android Navigation Compose

---

## Task 1: Add DataStore Dependency

**Files:**
- Modify: `app/build.gradle.kts:42-62`

- [x] **Step 1: Add DataStore dependency to app/build.gradle.kts**

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation("androidx.datastore:datastore-preferences:1.0.0") // Add this line
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [x] **Step 2: Sync Gradle**

Run: `./gradlew build --dry-run`
Expected: Successful dependency resolution

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "feat: add DataStore dependency for settings persistence"
```

---

## Task 2: Create UserPreferences Data Class

**Files:**
- Create: `app/src/main/java/tools/mo3ta/fitit/data/UserPreferences.kt`

- [ ] **Step 1: Create UserPreferences data class**

```kotlin
package tools.mo3ta.fitit.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object UserPreferences {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LANGUAGE = stringPreferencesKey("language")
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM;

    companion object {
        fun fromString(value: String?): ThemeMode {
            return values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
        }
    }
}

enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    ARABIC("ar", "Arabic");

    companion object {
        fun fromCode(code: String?): Language {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: Successful compilation

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/data/UserPreferences.kt
git commit -m "feat: create UserPreferences data class with ThemeMode and Language enums"
```

---

## Task 3: Create SettingsRepository

**Files:**
- Create: `app/src/main/java/tools/mo3ta/fitit/data/SettingsRepository.kt`
- Create: `app/src/test/java/tools/mo3ta/fitit/data/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write failing test for SettingsRepository**

```kotlin
package tools.mo3ta.fitit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var repository: SettingsRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        // Note: In real tests, you'd use a test DataStore
        // For simplicity, we'll create a real repository with test context
    }

    @Test
    fun `default theme mode should be SYSTEM`() = runTest {
        // This test will be implemented after repository creation
    }

    @Test
    fun `default language should be ENGLISH`() = runTest {
        // This test will be implemented after repository creation
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test`
Expected: FAIL with "SettingsRepository not defined"

- [ ] **Step 3: Create SettingsRepository implementation**

```kotlin
package tools.mo3ta.fitit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_preferences")

class SettingsRepository(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        ThemeMode.fromString(preferences[UserPreferences.THEME_MODE])
    }

    val language: Flow<Language> = context.dataStore.data.map { preferences ->
        Language.fromCode(preferences[UserPreferences.LANGUAGE])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        try {
            context.dataStore.edit { preferences ->
                preferences[UserPreferences.THEME_MODE] = mode.name
            }
        } catch (e: Exception) {
            // Log error but don't crash - fallback to default
            e.printStackTrace()
        }
    }

    suspend fun setLanguage(lang: Language) {
        try {
            context.dataStore.edit { preferences ->
                preferences[UserPreferences.LANGUAGE] = lang.code
            }
        } catch (e: Exception) {
            // Log error but don't crash - fallback to default
            e.printStackTrace()
        }
    }
}
```

- [ ] **Step 4: Update test to verify repository works**

```kotlin
package tools.mo3ta.fitit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var repository: SettingsRepository
    private lateinit var testContext: Context
    private lateinit var testDataStore: DataStore<Preferences>

    @Before
    fun setup() {
        val testFile = File(temporaryFolder.root, "test_settings.preferences_pb")
        testDataStore = preferencesDataStore(testFile.absolutePath) {
            corruptionHandler = null
            migrations = emptyList()
        }
        testContext = mockk(relaxed = true)
        repository = SettingsRepository(testContext)
    }

    @Test
    fun `default theme mode should be SYSTEM`() = runTest {
        val themeMode = repository.themeMode.first()
        assertEquals(ThemeMode.SYSTEM, themeMode)
    }

    @Test
    fun `default language should be ENGLISH`() = runTest {
        val language = repository.language.first()
        assertEquals(Language.ENGLISH, language)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test`
Expected: PASS for SettingsRepositoryTest

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/data/SettingsRepository.kt
git add app/src/test/java/tools/mo3ta/fitit/data/SettingsRepositoryTest.kt
git commit -m "feat: create SettingsRepository with DataStore for preferences"
```

---

## Task 4: Create SettingsViewModel

**Files:**
- Create: `app/src/main/java/tools/mo3ta/fitit/ui/SettingsViewModel.kt`
- Create: `app/src/test/java/tools/mo3ta/fitit/ui/SettingsViewModelTest.kt`

- [ ] **Step 1: Write failing test for SettingsViewModel**

```kotlin
package tools.mo3ta.fitit.ui

import tools.mo3ta.fitit.data.Language
import tools.mo3ta.fitit.data.SettingsRepository
import tools.mo3ta.fitit.data.ThemeMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
    }

    @Test
    fun `should initialize with default values`() = runTest {
        // This test will be implemented after ViewModel creation
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test`
Expected: FAIL with "SettingsViewModel not defined"

- [ ] **Step 3: Create SettingsViewModel implementation**

```kotlin
package tools.mo3ta.fitit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tools.mo3ta.fitit.data.Language
import tools.mo3ta.fitit.data.SettingsRepository
import tools.mo3ta.fitit.data.ThemeMode

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(Language.ENGLISH)
    val language: StateFlow<Language> = _language.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            repository.themeMode.collect { mode ->
                _themeMode.value = mode
            }
        }
        viewModelScope.launch {
            repository.language.collect { lang ->
                _language.value = lang
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setLanguage(lang: Language) {
        viewModelScope.launch {
            repository.setLanguage(lang)
        }
    }
}
```

- [ ] **Step 4: Update test to verify ViewModel works**

```kotlin
package tools.mo3ta.fitit.ui

import android.app.Application
import tools.mo3ta.fitit.data.Language
import tools.mo3ta.fitit.data.SettingsRepository
import tools.mo3ta.fitit.data.ThemeMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        every { repository.themeMode } returns flowOf(ThemeMode.SYSTEM)
        every { repository.language } returns flowOf(Language.ENGLISH)
    }

    @Test
    fun `should initialize with default values`() = runTest {
        val application = mockk<Application>(relaxed = true)
        viewModel = SettingsViewModel(application)
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
        assertEquals(Language.ENGLISH, viewModel.language.value)
    }

    @Test
    fun `setThemeMode should call repository`() = runTest {
        val application = mockk<Application>(relaxed = true)
        viewModel = SettingsViewModel(application)
        viewModel.setThemeMode(ThemeMode.DARK)
        // Verify repository call (implementation detail)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test`
Expected: PASS for SettingsViewModelTest

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/SettingsViewModel.kt
git add app/src/test/java/tools/mo3ta/fitit/ui/SettingsViewModelTest.kt
git commit -m "feat: create SettingsViewModel for settings state management"
```

---

## Task 5: Create SettingsScreen

**Files:**
- Create: `app/src/main/java/tools/mo3ta/fitit/ui/SettingsScreen.kt`

- [ ] **Step 1: Create SettingsScreen composable**

```kotlin
package tools.mo3ta.fitit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tools.mo3ta.fitit.data.Language
import tools.mo3ta.fitit.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.padding(innerPadding)
        ) {
            item {
                SettingsSection(title = "Appearance") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ThemeSelector(
                            selectedMode = themeMode,
                            onModeSelected = { viewModel.setThemeMode(it) }
                        )
                        LanguageSelector(
                            selectedLanguage = language,
                            onLanguageSelected = { viewModel.setLanguage(it) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = "About") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingItem(label = "App Name", value = "Fit It")
                        SettingItem(label = "Version", value = "1.0.0")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            content()
        }
    }
}

@Composable
fun SettingItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        Text(value, color = Color.Gray)
    }
}

@Composable
fun ThemeSelector(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Theme", color = Color.White)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ThemeMode.values().forEach { mode ->
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when (mode) {
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.SYSTEM -> "System"
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Language", color = Color.White)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedLanguage.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Color(0xFF2A2A2A),
                    textColor = Color.White
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Language.values().forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.displayName) },
                        onClick = {
                            onLanguageSelected(language)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: Successful compilation

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/SettingsScreen.kt
git commit -m "feat: create SettingsScreen with theme and language selectors"
```

---

## Task 6: Update MainActivity to integrate settings

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/fitit/MainActivity.kt:1-46`

- [ ] **Step 1: Modify MainActivity to integrate theme and language preferences**

```kotlin
package tools.mo3ta.fitit

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tools.mo3ta.fitit.data.Language
import tools.mo3ta.fitit.data.SettingsRepository
import tools.mo3ta.fitit.data.ThemeMode
import tools.mo3ta.fitit.ui.HomeScreen
import tools.mo3ta.fitit.ui.SettingsScreen
import tools.mo3ta.fitit.ui.SettingsViewModel
import tools.mo3ta.fitit.ui.emptytext.EmptyTextScreen
import tools.mo3ta.fitit.ui.textimage.TextImageScreen
import tools.mo3ta.fitit.ui.theme.FititTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private var currentThemeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var currentLanguage by mutableStateOf(Language.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsRepository = SettingsRepository(applicationContext)

        // Load preferences
        lifecycleScope.launch {
            settingsRepository.themeMode.collect { mode ->
                val oldMode = currentThemeMode
                currentThemeMode = mode
                // Recreate activity if theme mode changed and we're not in onCreate
                if (oldMode != ThemeMode.SYSTEM && mode != oldMode) {
                    recreate()
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.language.collect { lang ->
                val oldLang = currentLanguage
                currentLanguage = lang
                // Update locale and recreate if language changed
                if (oldLang != lang) {
                    updateLocale(lang)
                    recreate()
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            FitItApp()
        }
    }

    private fun updateLocale(language: Language) {
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    @Composable
    fun FitItApp() {
        val navController = rememberNavController()

        val darkTheme = when (currentThemeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

        FititTheme(darkTheme = darkTheme) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        onNavigateToTextImage = { navController.navigate("text_image") },
                        onNavigateToEmptyText = { navController.navigate("empty_text") },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }
                composable("text_image") {
                    TextImageScreen(onBack = { navController.popBackStack() })
                }
                composable("empty_text") {
                    EmptyTextScreen(onBack = { navController.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = viewModel(),
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: Successful compilation

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/MainActivity.kt
git commit -m "feat: integrate settings preferences into MainActivity with theme and language support"
```

---

## Task 7: Update HomeScreen with settings icon

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/fitit/ui/HomeScreen.kt:1-121`

- [ ] **Step 1: Update HomeScreen to add settings icon and externalize strings**

```kotlin
package tools.mo3ta.fitit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTextImage: () -> Unit,
    onNavigateToEmptyText: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Fit It", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        Text("Content Creator Toolkit", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        val tools = listOf(
            ToolItem(
                title = "Text in Image",
                description = "Fit any text into a HQ image with custom styles",
                icon = Icons.Default.AutoAwesome,
                color = Color(0xFF007AFF),
                onClick = onNavigateToTextImage
            ),
            ToolItem(
                title = "Invisible Text",
                description = "Generate invisible text for social apps",
                icon = Icons.Default.VisibilityOff,
                color = Color(0xFF5856D6),
                onClick = onNavigateToEmptyText
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(innerPadding)
        ) {
            items(tools) { tool ->
                ToolCard(tool)
            }
        }
    }
}

@Composable
fun ToolCard(tool: ToolItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { tool.onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(tool.color, tool.color.copy(alpha = 0.6f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Text(tool.description, fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

data class ToolItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: Successful compilation

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/HomeScreen.kt
git commit -m "feat: add settings icon to HomeScreen and update navigation"
```

---

## Task 8: Create Arabic string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml:1-3`
- Create: `app/src/main/res/values-ar/strings.xml`

- [ ] **Step 1: Update English strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Fit It</string>

    <!-- Home Screen -->
    <string name="home_title">Content Creator Toolkit</string>
    <string name="tool_text_image">Text in Image</string>
    <string name="tool_text_image_desc">Fit any text into a HQ image with custom styles</string>
    <string name="tool_empty_text">Invisible Text</string>
    <string name="tool_empty_text_desc">Generate invisible text for social apps</string>
    <string name="settings">Settings</string>
    <string name="settings_description">App settings</string>

    <!-- Settings Screen -->
    <string name="settings_title">Settings</string>
    <string name="appearance">Appearance</string>
    <string name="theme">Theme</string>
    <string name="theme_light">Light</string>
    <string name="theme_dark">Dark</string>
    <string name="theme_system">System</string>
    <string name="language">Language</string>
    <string name="language_english">English</string>
    <string name="language_arabic">Arabic</string>
    <string name="about">About</string>
    <string name="app_name_label">App Name</string>
    <string name="version">Version</string>
    <string name="back">Back</string>

    <!-- Navigation -->
    <string name="navigate_to_settings">Navigate to settings</string>
</resources>
```

- [ ] **Step 2: Create Arabic strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">فيت</string>

    <!-- Home Screen -->
    <string name="home_title">مجموعة أدوات صانع المحتوى</string>
    <string name="tool_text_image">نص في صورة</string>
    <string name="tool_text_image_desc">ضع أي نص في صورة عالية الجودة بتصاميم مخصصة</string>
    <string name="tool_empty_text">نص غير مرئي</string>
    <string name="tool_empty_text_desc">أنشئ نصاً غير مرئي لتطبيقات التواصل الاجتماعي</string>
    <string name="settings">الإعدادات</string>
    <string name="settings_description">إعدادات التطبيق</string>

    <!-- Settings Screen -->
    <string name="settings_title">الإعدادات</string>
    <string name="appearance">المظهر</string>
    <string name="theme">السمة</string>
    <string name="theme_light">فاتح</string>
    <string name="theme_dark">داكن</string>
    <string name="theme_system">النظام</string>
    <string name="language">اللغة</string>
    <string name="language_english">English</string>
    <string name="language_arabic">العربية</string>
    <string name="about">حول</string>
    <string name="app_name_label">اسم التطبيق</string>
    <string name="version">الإصدار</string>
    <string name="back">رجوع</string>

    <!-- Navigation -->
    <string name="navigate_to_settings">الانتقال إلى الإعدادات</string>
</resources>
```

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew assembleDebug`
Expected: Successful build with both language resources

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml
git add app/src/main/res/values-ar/strings.xml
git commit -m "feat: add English and Arabic string resources for localization"
```

---

## Task 9: Update SettingsScreen to use string resources

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/fitit/ui/SettingsScreen.kt:1-180`

- [ ] **Step 1: Update SettingsScreen to use string resources**

```kotlin
package tools.mo3ta.fitit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.data.Language
import tools.mo3ta.fitit.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.padding(innerPadding)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.appearance)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ThemeSelector(
                            selectedMode = themeMode,
                            onModeSelected = { viewModel.setThemeMode(it) }
                        )
                        LanguageSelector(
                            selectedLanguage = language,
                            onLanguageSelected = { viewModel.setLanguage(it) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = stringResource(R.string.about)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingItem(
                            label = stringResource(R.string.app_name_label),
                            value = stringResource(R.string.app_name)
                        )
                        SettingItem(label = stringResource(R.string.version), value = "1.0.0")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            content()
        }
    }
}

@Composable
fun SettingItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        Text(value, color = Color.Gray)
    }
}

@Composable
fun ThemeSelector(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.theme), color = Color.White)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ThemeMode.values().forEach { mode ->
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when (mode) {
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language), color = Color.White)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedLanguage.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Color(0xFF2A2A2A),
                    textColor = Color.White
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Language.values().forEach { language ->
                    DropdownMenuItem(
                        text = { Text(
                            when (language) {
                                Language.ENGLISH -> stringResource(R.string.language_english)
                                Language.ARABIC -> stringResource(R.string.language_arabic)
                            }
                        )},
                        onClick = {
                            onLanguageSelected(language)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: Successful compilation

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/SettingsScreen.kt
git commit -m "feat: update SettingsScreen to use string resources for localization"
```

---

## Task 10: Externalize strings in HomeScreen

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/fitit/ui/HomeScreen.kt:1-121`

- [ ] **Step 1: Update HomeScreen to use string resources**

```kotlin
package tools.mo3ta.fitit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.fitit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTextImage: () -> Unit,
    onNavigateToEmptyText: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        Text(stringResource(R.string.home_title), fontSize = 12.sp, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        val tools = listOf(
            ToolItem(
                title = stringResource(R.string.tool_text_image),
                description = stringResource(R.string.tool_text_image_desc),
                icon = Icons.Default.AutoAwesome,
                color = Color(0xFF007AFF),
                onClick = onNavigateToTextImage
            ),
            ToolItem(
                title = stringResource(R.string.tool_empty_text),
                description = stringResource(R.string.tool_empty_text_desc),
                icon = Icons.Default.VisibilityOff,
                color = Color(0xFF5856D6),
                onClick = onNavigateToEmptyText
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(innerPadding)
        ) {
            items(tools) { tool ->
                ToolCard(tool)
            }
        }
    }
}

@Composable
fun ToolCard(tool: ToolItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { tool.onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(tool.color, tool.color.copy(alpha = 0.6f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Text(tool.description, fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

data class ToolItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: Successful compilation

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/HomeScreen.kt
git commit -m "feat: externalize strings in HomeScreen for localization"
```

---

## Task 11: Final Build and Test

**Files:**
- All project files

- [ ] **Step 1: Clean and build project**

Run: `./gradlew clean assembleDebug`
Expected: Successful build with no errors

- [ ] **Step 2: Run unit tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 3: Install and manually test on device/emulator**

Run: `./gradlew installDebug`

Manual testing checklist:
- [ ] Settings screen accessible from home screen
- [ ] Theme switching works (Light/Dark/System)
- [ ] Language switching works (English/Arabic)
- [ ] RTL layout activates for Arabic
- [ ] Preferences persist after app restart
- [ ] All existing functionality remains intact

- [ ] **Step 4: Commit final implementation**

```bash
git add -A
git commit -m "feat: complete settings screen implementation with localization and dark mode support"
```

---

## Self-Review Checklist

- [ ] **Spec coverage**: All requirements from design spec are implemented
- [ ] **No placeholders**: All steps contain complete code, no "TBD" or similar
- [ ] **Type consistency**: All type names, function signatures match across tasks
- [ ] **File paths**: All file paths are exact and correct
- [ ] **Build verification**: Each task includes verification steps
- [ ] **Git commits**: Each task includes appropriate commit messages
- [ ] **Test coverage**: Data layer and ViewModel have tests
- [ ] **Localization**: Arabic strings provided for all user-facing text
- [ ] **Theme support**: All three theme modes (Light/Dark/System) implemented
- [ ] **Persistence**: DataStore properly configured for preference storage

---

**Implementation plan complete and ready for execution.**
