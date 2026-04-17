# Text Splitter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 4th feature "تقسيم النص" that splits long text into word-aware chunks with per-chunk copy and clipboard-history-based "Copy All" in reverse order.

**Architecture:** Single screen + ViewModel pattern matching existing features. Pure Kotlin `splitText()` function handles word-aware chunking. "Copy All" uses `viewModelScope` + `delay(150)` to write each chunk individually to clipboard history in reverse order.

**Tech Stack:** Jetpack Compose, Material3, ViewModel + mutableStateOf, ClipboardManager, Firebase Analytics, Kotlin coroutines (delay only)

---

## File Map

| Action | File |
|--------|------|
| Modify | `app/src/main/res/values/strings.xml` |
| Modify | `app/src/main/java/tools/mo3ta/fitit/analytics/AnalyticsManager.kt` |
| Create | `app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterViewModel.kt` |
| Create | `app/src/test/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterViewModelTest.kt` |
| Create | `app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterScreen.kt` |
| Modify | `app/src/main/java/tools/mo3ta/fitit/MainActivity.kt` |
| Modify | `app/src/main/java/tools/mo3ta/fitit/ui/HomeScreen.kt` |

---

## Task 1: Add Arabic strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings before closing `</resources>` tag**

Open `strings.xml`. It currently ends around line 70+. Add before `</resources>`:

```xml
    <!-- Text Splitter Screen -->
    <string name="tool_text_splitter">تقسيم النص</string>
    <string name="tool_text_splitter_desc">قسّم النص الطويل إلى أجزاء مناسبة للنشر</string>
    <string name="text_splitter_title">تقسيم النص</string>
    <string name="text_splitter_preset_whatsapp">واتساب</string>
    <string name="text_splitter_preset_twitter">تويتر</string>
    <string name="text_splitter_preset_custom">مخصص</string>
    <string name="text_splitter_custom_size_label">الحد الأقصى للحروف</string>
    <string name="text_splitter_input_hint">أدخل النص هنا...</string>
    <string name="text_splitter_split_button">تقسيم</string>
    <string name="text_splitter_copy_all">نسخ الكل</string>
    <string name="text_splitter_error_too_short">النص قصير جداً، لا حاجة للتقسيم</string>
    <string name="text_splitter_chunk_label">الجزء</string>
    <string name="text_splitter_chars">حرف</string>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add Arabic strings for text splitter screen"
```

---

## Task 2: Add analytics events to AnalyticsManager

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/fitit/analytics/AnalyticsManager.kt`

- [ ] **Step 1: Add three new tracking methods**

Open `AnalyticsManager.kt`. After the `trackOpenWaSent()` line, add:

```kotlin
    fun trackTextSplitterUsed(preset: String, chunkCount: Int) =
        log("zaki_text_splitter_used", "preset" to preset, "chunk_count" to chunkCount.toString())

    fun trackTextSplitterCopyAll(chunkCount: Int) =
        log("zaki_text_splitter_copy_all", "chunk_count" to chunkCount.toString())
```

`trackScreenView("text_splitter")` already works via the existing `trackScreenView(screenName)` method — no new method needed.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/analytics/AnalyticsManager.kt
git commit -m "feat: add text splitter analytics events"
```

---

## Task 3: Create TextSplitterViewModel

**Files:**
- Create: `app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterViewModel.kt`
- Create: `app/src/test/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterViewModelTest.kt`

- [ ] **Step 1: Write the failing tests first**

Create `app/src/test/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterViewModelTest.kt`:

```kotlin
package tools.mo3ta.fitit.ui.textsplitter

import org.junit.Assert.*
import org.junit.Test

class TextSplitterViewModelTest {

    @Test
    fun `splitText returns multiple chunks for long text`() {
        val text = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10"
        val result = splitText(text, 20)
        assertTrue("Expected more than 1 chunk", result.size > 1)
    }

    @Test
    fun `splitText does not cut mid-word`() {
        val text = "hello world goodbye"
        val result = splitText(text, 10)
        result.forEach { chunk ->
            assertFalse("Chunk should not start or end with partial word fragment",
                chunk.startsWith(" ") || chunk.endsWith(" "))
        }
    }

    @Test
    fun `splitText preserves all words across chunks`() {
        val text = "one two three four five six seven eight nine ten"
        val result = splitText(text, 15)
        val rejoined = result.joinToString(" ")
        assertEquals(text, rejoined)
    }

    @Test
    fun `splitText hard-splits a word longer than chunkSize`() {
        val longWord = "a".repeat(50)
        val result = splitText(longWord, 20)
        assertEquals(3, result.size)
        assertEquals("a".repeat(20), result[0])
        assertEquals("a".repeat(20), result[1])
        assertEquals("a".repeat(10), result[2])
    }

    @Test
    fun `splitText returns single chunk when text fits`() {
        val text = "short text"
        val result = splitText(text, 100)
        assertEquals(1, result.size)
        assertEquals("short text", result[0])
    }

    @Test
    fun `isSplitEnabled is false for blank input`() {
        val vm = TextSplitterViewModel()
        vm.inputText = "   "
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `isSplitEnabled is false for custom preset with zero size`() {
        val vm = TextSplitterViewModel()
        vm.inputText = "some text"
        vm.selectedPreset = SplitPreset.CUSTOM
        vm.customSizeInput = "0"
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `split sets errorMessage when text fits in one chunk`() {
        val vm = TextSplitterViewModel()
        vm.inputText = "short"
        vm.selectedPreset = SplitPreset.TWITTER  // 280 chars
        vm.split()
        assertNotNull(vm.errorMessage)
        assertTrue(vm.chunks.isEmpty())
    }

    @Test
    fun `split produces chunks and clears errorMessage for long text`() {
        val vm = TextSplitterViewModel()
        vm.inputText = "word ".repeat(60).trim()  // ~300 chars
        vm.selectedPreset = SplitPreset.WHATSAPP  // 200 chars
        vm.split()
        assertNull(vm.errorMessage)
        assertTrue(vm.chunks.size > 1)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

In Android Studio: right-click `TextSplitterViewModelTest` → Run. Or via terminal:

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.textsplitter.TextSplitterViewModelTest"
```

Expected: compile error — `TextSplitterViewModel`, `SplitPreset`, `splitText` not found.

- [ ] **Step 3: Create TextSplitterViewModel.kt**

Create `app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterViewModel.kt`:

```kotlin
package tools.mo3ta.fitit.ui.textsplitter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tools.mo3ta.fitit.analytics.AnalyticsManager

enum class SplitPreset(val fixedSize: Int?) {
    WHATSAPP(200),
    TWITTER(280),
    CUSTOM(null)
}

class TextSplitterViewModel : ViewModel() {

    var inputText by mutableStateOf("")
    var selectedPreset by mutableStateOf(SplitPreset.WHATSAPP)
    var customSizeInput by mutableStateOf("100")
    var chunks by mutableStateOf<List<String>>(emptyList())
    var errorMessage by mutableStateOf<String?>(null)

    val chunkSize: Int
        get() = selectedPreset.fixedSize ?: (customSizeInput.toIntOrNull() ?: 0)

    val isSplitEnabled: Boolean
        get() = inputText.isNotBlank() &&
                (selectedPreset != SplitPreset.CUSTOM || (customSizeInput.toIntOrNull() ?: 0) > 0)

    fun split() {
        val size = chunkSize
        if (size <= 0) return
        val trimmed = inputText.trim()
        if (trimmed.length <= size) {
            errorMessage = "النص قصير جداً، لا حاجة للتقسيم"
            chunks = emptyList()
            return
        }
        errorMessage = null
        chunks = splitText(trimmed, size)
        AnalyticsManager.trackTextSplitterUsed(
            preset = selectedPreset.name.lowercase(),
            chunkCount = chunks.size
        )
    }

    fun copyChunk(context: Context, chunk: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("chunk", chunk))
    }

    fun copyAll(context: Context) {
        AnalyticsManager.trackTextSplitterCopyAll(chunkCount = chunks.size)
        viewModelScope.launch {
            chunks.reversed().forEach { chunk ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("chunk", chunk))
                delay(150)
            }
        }
    }
}

/**
 * Word-aware text splitter. Breaks at last space before [chunkSize].
 * Hard-splits any single word longer than [chunkSize].
 */
fun splitText(text: String, chunkSize: Int): List<String> {
    val words = text.split(" ")
    val chunks = mutableListOf<String>()
    val current = StringBuilder()

    for (word in words) {
        if (word.length > chunkSize) {
            // Flush current buffer first
            if (current.isNotEmpty()) {
                chunks.add(current.toString().trimEnd())
                current.clear()
            }
            // Hard-split the oversized word
            var remaining = word
            while (remaining.length > chunkSize) {
                chunks.add(remaining.substring(0, chunkSize))
                remaining = remaining.substring(chunkSize)
            }
            if (remaining.isNotEmpty()) {
                current.append(remaining).append(" ")
            }
        } else {
            val proposed = current.toString().trimEnd().let {
                if (it.isEmpty()) word else "$it $word"
            }
            if (proposed.length > chunkSize) {
                chunks.add(current.toString().trimEnd())
                current.clear()
                current.append(word).append(" ")
            } else {
                current.append(word).append(" ")
            }
        }
    }

    if (current.isNotEmpty()) {
        chunks.add(current.toString().trimEnd())
    }

    return chunks
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.textsplitter.TextSplitterViewModelTest"
```

Expected: all 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterViewModel.kt
git add app/src/test/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterViewModelTest.kt
git commit -m "feat: add TextSplitterViewModel with word-aware split logic and tests"
```

---

## Task 4: Create TextSplitterScreen

**Files:**
- Create: `app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterScreen.kt`

- [ ] **Step 1: Create the screen**

Create `app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterScreen.kt`:

```kotlin
package tools.mo3ta.fitit.ui.textsplitter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSplitterScreen(
    onBack: () -> Unit,
    viewModel: TextSplitterViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("text_splitter")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.text_splitter_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preset chips
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SplitPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = viewModel.selectedPreset == preset,
                            onClick = { viewModel.selectedPreset = preset },
                            label = {
                                Text(
                                    text = when (preset) {
                                        SplitPreset.WHATSAPP -> stringResource(R.string.text_splitter_preset_whatsapp)
                                        SplitPreset.TWITTER -> stringResource(R.string.text_splitter_preset_twitter)
                                        SplitPreset.CUSTOM -> stringResource(R.string.text_splitter_preset_custom)
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Custom size field — only shown for CUSTOM preset
            if (viewModel.selectedPreset == SplitPreset.CUSTOM) {
                item {
                    OutlinedTextField(
                        value = viewModel.customSizeInput,
                        onValueChange = { viewModel.customSizeInput = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.text_splitter_custom_size_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Text input
            item {
                OutlinedTextField(
                    value = viewModel.inputText,
                    onValueChange = { viewModel.inputText = it },
                    label = { Text(stringResource(R.string.text_splitter_input_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    maxLines = 12
                )
            }

            // Split button
            item {
                Button(
                    onClick = { viewModel.split() },
                    enabled = viewModel.isSplitEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.text_splitter_split_button))
                }
            }

            // Error state
            viewModel.errorMessage?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Copy All button + chunks
            if (viewModel.chunks.isNotEmpty()) {
                item {
                    Button(
                        onClick = { viewModel.copyAll(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.text_splitter_copy_all))
                    }
                }

                itemsIndexed(viewModel.chunks) { index, chunk ->
                    ChunkCard(
                        index = index + 1,
                        chunk = chunk,
                        onCopy = { viewModel.copyChunk(context, chunk) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChunkCard(index: Int, chunk: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index.toArabicNumeral()} — ${chunk.length.toArabicNumeral()} ${stringResource(R.string.text_splitter_chars)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = chunk,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun Int.toArabicNumeral(): String = this.toString().map { c ->
    when (c) {
        '0' -> '٠'; '1' -> '١'; '2' -> '٢'; '3' -> '٣'; '4' -> '٤'
        '5' -> '٥'; '6' -> '٦'; '7' -> '٧'; '8' -> '٨'; '9' -> '٩'
        else -> c
    }
}.joinToString("")
```

- [ ] **Step 2: Build to check for compile errors**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Fix any import or type errors if they appear.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterScreen.kt
git commit -m "feat: add TextSplitterScreen UI with chunk list and copy actions"
```

---

## Task 5: Wire navigation and HomeScreen card

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/fitit/MainActivity.kt`
- Modify: `app/src/main/java/tools/mo3ta/fitit/ui/HomeScreen.kt`

- [ ] **Step 1: Add nav param to HomeScreen signature**

In `HomeScreen.kt`, update the function signature from:

```kotlin
fun HomeScreen(
    onNavigateToTextImage: () -> Unit,
    onNavigateToEmptyText: () -> Unit,
    onNavigateToOpenWa: () -> Unit,
    onNavigateToSettings: () -> Unit
)
```

to:

```kotlin
fun HomeScreen(
    onNavigateToTextImage: () -> Unit,
    onNavigateToEmptyText: () -> Unit,
    onNavigateToOpenWa: () -> Unit,
    onNavigateToTextSplitter: () -> Unit,
    onNavigateToSettings: () -> Unit
)
```

- [ ] **Step 2: Add import and ToolItem for text splitter in HomeScreen**

In `HomeScreen.kt`, add import at the top:

```kotlin
import androidx.compose.material.icons.filled.ContentCut
```

Then find the `val tools = remember(...)` block. Update it to include the 4th tool. The remember key must also include the new title:

```kotlin
val textSplitterTitle = stringResource(R.string.tool_text_splitter)
val textSplitterDesc = stringResource(R.string.tool_text_splitter_desc)

val tools = remember(textImageTitle, emptyTextTitle, openWaTitle, textSplitterTitle) {
    listOf(
        ToolItem(
            title = textImageTitle,
            description = textImageDesc,
            icon = Icons.Default.AutoAwesome,
            color = Color(0xFF007AFF)
        ),
        ToolItem(
            title = emptyTextTitle,
            description = emptyTextDesc,
            icon = Icons.Default.VisibilityOff,
            color = Color(0xFF5856D6)
        ),
        ToolItem(
            title = openWaTitle,
            description = openWaDesc,
            icon = Icons.Default.Chat,
            color = Color(0xFF25D366)
        ),
        ToolItem(
            title = textSplitterTitle,
            description = textSplitterDesc,
            icon = Icons.Default.ContentCut,
            color = Color(0xFFFF9500)
        )
    )
}
```

Also update the `onClicks` remember block:

```kotlin
val onClicks = remember(onNavigateToTextImage, onNavigateToEmptyText, onNavigateToOpenWa, onNavigateToTextSplitter) {
    listOf(onNavigateToTextImage, onNavigateToEmptyText, onNavigateToOpenWa, onNavigateToTextSplitter)
}
```

- [ ] **Step 3: Add route and import to MainActivity**

In `MainActivity.kt`, add import:

```kotlin
import tools.mo3ta.fitit.ui.textsplitter.TextSplitterScreen
```

In the `NavHost` block, add the new composable route after the `settings` route:

```kotlin
composable("text_splitter") {
    TextSplitterScreen(onBack = { navController.popBackStack() })
}
```

Update the `HomeScreen(...)` call to pass the new lambda:

```kotlin
composable("home") {
    HomeScreen(
        onNavigateToTextImage = { navController.navigate("text_image") },
        onNavigateToEmptyText = { navController.navigate("empty_text") },
        onNavigateToOpenWa = { navController.navigate("open_wa") },
        onNavigateToTextSplitter = { navController.navigate("text_splitter") },
        onNavigateToSettings = { navController.navigate("settings") }
    )
}
```

- [ ] **Step 4: Build to verify everything compiles**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/MainActivity.kt
git add app/src/main/java/tools/mo3ta/fitit/ui/HomeScreen.kt
git commit -m "feat: wire text splitter navigation and add HomeScreen card"
```
