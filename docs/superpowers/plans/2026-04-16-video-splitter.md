# Video Splitter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Video Splitter" tool that picks a gallery video (≤5 min), splits it into 30s overlapping chunks (2s overlap), and lets the user save or share each chunk.

**Architecture:** Pure chunk-math function lives in `videosplitter/VideoChunkMath.kt` (unit-testable, no Android deps). `VideoSplitterViewModel` handles state + coroutine work using `MediaExtractor`/`MediaMuxer`. `VideoSplitterScreen` is a Composable following the same ViewModel-Screen pattern as `TextSplitterScreen`.

**Tech Stack:** Jetpack Compose, AndroidViewModel, MediaExtractor + MediaMuxer (built-in, no new deps), ActivityResultContracts.PickVisualMedia, MediaStore (API 29+) / Environment (API 24–28), FileProvider for sharing.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `ui/videosplitter/VideoChunkMath.kt` | Pure chunk-range calculation, no Android deps |
| Create | `ui/videosplitter/VideoSplitterViewModel.kt` | State, split coroutine, save, share |
| Create | `ui/videosplitter/VideoSplitterScreen.kt` | Full Composable UI |
| Create | `test/.../videosplitter/VideoChunkMathTest.kt` | Unit tests for chunk math |
| Create | `test/.../videosplitter/VideoSplitterViewModelTest.kt` | Unit tests for ViewModel state |
| Modify | `res/xml/file_paths.xml` (create new) | FileProvider cache paths |
| Modify | `AndroidManifest.xml` | Add FileProvider `<provider>` |
| Modify | `res/values/strings.xml` | New strings for video splitter |
| Modify | `analytics/AnalyticsManager.kt` | 3 new event functions |
| Modify | `ui/HomeScreen.kt` | New tool card + navigation callback |
| Modify | `MainActivity.kt` | Nav route + callback wiring |

---

## Task 1: Strings + FileProvider setup

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add strings**

Open `app/src/main/res/values/strings.xml`. Add at the end, before `</resources>`:

```xml
    <!-- Video Splitter Screen -->
    <string name="tool_video_splitter">تقسيم الفيديو</string>
    <string name="tool_video_splitter_desc">قسّم الفيديو إلى مقاطع قصيرة مناسبة للنشر</string>
    <string name="video_splitter_title">تقسيم الفيديو</string>
    <string name="video_splitter_feature_desc">اختر فيديو بحد أقصى ٥ دقائق، سيتم تقسيمه إلى مقاطع مدتها ٣٠ ثانية مع تداخل ثانيتين بين كل مقطع.</string>
    <string name="video_splitter_pick_video">اختر فيديو من المعرض</string>
    <string name="video_splitter_duration_error">مدة الفيديو تتجاوز ٥ دقائق</string>
    <string name="video_splitter_split_button">تقسيم</string>
    <string name="video_splitter_save">حفظ</string>
    <string name="video_splitter_share">مشاركة</string>
    <string name="video_splitter_saved">تم الحفظ ✓</string>
    <string name="video_splitter_chunk_label">مقطع</string>
    <string name="video_splitter_error_generic">فشل تقسيم الفيديو، حاول مرة أخرى</string>
```

- [ ] **Step 2: Create file_paths.xml**

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="video_chunks" path="video_chunks/" />
</paths>
```

- [ ] **Step 3: Add FileProvider to AndroidManifest**

Inside `<application>` in `app/src/main/AndroidManifest.xml`, add after the `<activity>` closing tag:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.provider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/res/xml/file_paths.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add video splitter strings and FileProvider setup"
```

---

## Task 2: Chunk math pure function + unit tests

**Files:**
- Create: `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMath.kt`
- Create: `app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMathTest.kt`

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMathTest.kt`:

```kotlin
package tools.mo3ta.fitit.ui.videosplitter

import org.junit.Assert.*
import org.junit.Test

class VideoChunkMathTest {

    @Test
    fun `calculateChunks returns 2 chunks for 60s video`() {
        val chunks = calculateChunks(60_000L)
        assertEquals(2, chunks.size)
    }

    @Test
    fun `calculateChunks first chunk starts at 0 and ends at 32s`() {
        val chunks = calculateChunks(60_000L)
        assertEquals(0L, chunks[0].startMs)
        assertEquals(32_000L, chunks[0].endMs)
    }

    @Test
    fun `calculateChunks second chunk starts at 30s`() {
        val chunks = calculateChunks(60_000L)
        assertEquals(30_000L, chunks[1].startMs)
    }

    @Test
    fun `calculateChunks last chunk end clamped to video duration`() {
        val chunks = calculateChunks(55_000L)
        assertEquals(55_000L, chunks.last().endMs)
    }

    @Test
    fun `calculateChunks returns 10 chunks for 300s video`() {
        val chunks = calculateChunks(300_000L)
        assertEquals(10, chunks.size)
    }

    @Test
    fun `calculateChunks single chunk for video under 30s`() {
        val chunks = calculateChunks(20_000L)
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startMs)
        assertEquals(20_000L, chunks[0].endMs)
    }

    @Test
    fun `calculateChunks chunk indices start at 1`() {
        val chunks = calculateChunks(60_000L)
        assertEquals(1, chunks[0].index)
        assertEquals(2, chunks[1].index)
    }

    @Test
    fun `calculateChunks consecutive chunks overlap by 2s`() {
        val chunks = calculateChunks(90_000L)
        // chunk[0].endMs - chunk[1].startMs = 32000 - 30000 = 2000
        assertEquals(2_000L, chunks[0].endMs - chunks[1].startMs)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/appleworld/AndroidStudioProjects/Fitit
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.videosplitter.VideoChunkMathTest" 2>&1 | tail -20
```

Expected: compilation error — `calculateChunks` not defined.

- [ ] **Step 3: Create VideoChunkMath.kt**

Create `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMath.kt`:

```kotlin
package tools.mo3ta.fitit.ui.videosplitter

import kotlin.math.ceil

const val CHUNK_STEP_MS = 30_000L
const val CHUNK_DURATION_MS = 32_000L  // 30s content + 2s overlap
const val MAX_DURATION_MS = 300_000L   // 5 minutes

data class ChunkRange(val startMs: Long, val endMs: Long, val index: Int)

fun calculateChunks(durationMs: Long): List<ChunkRange> {
    val count = ceil(durationMs.toDouble() / CHUNK_STEP_MS).toInt().coerceAtLeast(1)
    return (0 until count).map { i ->
        ChunkRange(
            startMs = i * CHUNK_STEP_MS,
            endMs = minOf(i * CHUNK_STEP_MS + CHUNK_DURATION_MS, durationMs),
            index = i + 1
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.videosplitter.VideoChunkMathTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 8 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMath.kt \
        app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMathTest.kt
git commit -m "feat: add video chunk math with unit tests"
```

---

## Task 3: VideoSplitterViewModel

**Files:**
- Create: `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModel.kt`
- Create: `app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel state tests**

Create `app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModelTest.kt`:

```kotlin
package tools.mo3ta.fitit.ui.videosplitter

import android.app.Application
import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class VideoSplitterViewModelTest {

    private fun createViewModel(): VideoSplitterViewModel {
        val application = mockk<Application>(relaxed = true)
        return VideoSplitterViewModel(application)
    }

    @Test
    fun `isSplitEnabled is false when no video selected`() {
        val vm = createViewModel()
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `isSplitEnabled is false when duration exceeds 5 minutes`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 360_000L)  // 6 min
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `isSplitEnabled is true for valid video under 5 minutes`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 120_000L)  // 2 min
        assertTrue(vm.isSplitEnabled)
    }

    @Test
    fun `isDurationValid is false for 0ms`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 0L)
        assertFalse(vm.isDurationValid)
    }

    @Test
    fun `isDurationValid is true for exactly 300s`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 300_000L)
        assertTrue(vm.isDurationValid)
    }

    @Test
    fun `onVideoSelected resets previous chunks and error`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 60_000L)
        // Manually simulate previous state
        vm.onVideoSelected(uri, 90_000L)
        assertTrue(vm.chunks.isEmpty())
        assertNull(vm.errorMessage)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.videosplitter.VideoSplitterViewModelTest" 2>&1 | tail -20
```

Expected: compilation error — `VideoSplitterViewModel` not defined.

- [ ] **Step 3: Create VideoSplitterViewModel.kt**

Create `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModel.kt`:

```kotlin
package tools.mo3ta.fitit.ui.videosplitter

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tools.mo3ta.fitit.analytics.AnalyticsManager
import java.io.File
import java.nio.ByteBuffer

data class VideoChunk(
    val file: File,
    val startMs: Long,
    val endMs: Long,
    val index: Int
)

class VideoSplitterViewModel(application: Application) : AndroidViewModel(application) {

    var selectedVideoUri by mutableStateOf<Uri?>(null)
        private set
    var videoDurationMs by mutableStateOf(0L)
        private set
    var isProcessing by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var chunks by mutableStateOf<List<VideoChunk>>(emptyList())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var savedChunkIndices by mutableStateOf<Set<Int>>(emptySet())
        private set

    val isDurationValid: Boolean
        get() = videoDurationMs in 1..MAX_DURATION_MS

    val isSplitEnabled: Boolean
        get() = selectedVideoUri != null && isDurationValid && !isProcessing

    fun onVideoSelected(uri: Uri, durationMs: Long) {
        selectedVideoUri = uri
        videoDurationMs = durationMs
        chunks = emptyList()
        errorMessage = null
        savedChunkIndices = emptySet()
    }

    fun split() {
        val uri = selectedVideoUri ?: return
        val context = getApplication<Application>()

        viewModelScope.launch {
            isProcessing = true
            progress = 0f
            errorMessage = null
            chunks = emptyList()

            try {
                AnalyticsManager.trackVideoSplitStarted(videoDurationMs)
            } catch (_: Exception) {}

            try {
                val outputDir = File(context.cacheDir, "video_chunks").also { it.mkdirs() }
                val ranges = calculateChunks(videoDurationMs)
                val result = mutableListOf<VideoChunk>()

                withContext(Dispatchers.IO) {
                    ranges.forEachIndexed { i, range ->
                        val outputFile = File(outputDir, "chunk_${range.index}.mp4")
                        extractSegment(context, uri, range.startMs, range.endMs, outputFile)
                        result.add(VideoChunk(outputFile, range.startMs, range.endMs, range.index))
                        withContext(Dispatchers.Main) {
                            progress = (i + 1).toFloat() / ranges.size
                        }
                    }
                }

                chunks = result
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isProcessing = false
            }
        }
    }

    fun saveChunk(context: Context, chunk: VideoChunk) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "fitit_chunk_${chunk.index}.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Fitit")
                    }
                    val insertUri = context.contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                    )
                    insertUri?.let { dest ->
                        context.contentResolver.openOutputStream(dest)?.use { os ->
                            chunk.file.inputStream().use { it.copyTo(os) }
                        }
                    }
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "Fitit"
                    ).also { it.mkdirs() }
                    val dest = File(dir, "fitit_chunk_${chunk.index}.mp4")
                    chunk.file.copyTo(dest, overwrite = true)
                    @Suppress("DEPRECATION")
                    android.media.MediaScannerConnection.scanFile(
                        context, arrayOf(dest.absolutePath), null, null
                    )
                }
                withContext(Dispatchers.Main) {
                    savedChunkIndices = savedChunkIndices + chunk.index
                    try { AnalyticsManager.trackVideoChunkSaved() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun shareChunk(context: Context, chunk: VideoChunk) {
        val fileUri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", chunk.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null))
        try { AnalyticsManager.trackVideoChunkShared() } catch (_: Exception) {}
    }
}

private fun extractSegment(
    context: Context,
    uri: Uri,
    startMs: Long,
    endMs: Long,
    output: File
) {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, uri, null)

    val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    val trackMap = mutableMapOf<Int, Int>()
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            trackMap[i] = muxer.addTrack(format)
            extractor.selectTrack(i)
        }
    }

    muxer.start()
    extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

    val buffer = ByteBuffer.allocate(1 * 1024 * 1024)
    val bufferInfo = MediaCodec.BufferInfo()

    while (true) {
        val trackIndex = extractor.sampleTrackIndex
        if (trackIndex < 0) break
        val muxerTrack = trackMap[trackIndex] ?: run { extractor.advance(); continue }

        val sampleTimeUs = extractor.sampleTime
        if (sampleTimeUs > endMs * 1000L) break

        bufferInfo.size = extractor.readSampleData(buffer, 0)
        if (bufferInfo.size < 0) break

        bufferInfo.offset = 0
        bufferInfo.presentationTimeUs = sampleTimeUs - (startMs * 1000L)
        bufferInfo.flags = extractor.sampleFlags

        muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
        extractor.advance()
    }

    muxer.stop()
    muxer.release()
    extractor.release()
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.videosplitter.VideoSplitterViewModelTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModel.kt \
        app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModelTest.kt
git commit -m "feat: add VideoSplitterViewModel with split, save, share logic"
```

---

## Task 4: VideoSplitterScreen UI

**Files:**
- Create: `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterScreen.kt`

- [ ] **Step 1: Create VideoSplitterScreen.kt**

Create `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterScreen.kt`:

```kotlin
package tools.mo3ta.fitit.ui.videosplitter

import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager

private val RedAccent = Color(0xFFFF3B30)
private val RedDark   = Color(0xFFCC2222)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSplitterScreen(
    onBack: () -> Unit,
    viewModel: VideoSplitterViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnalyticsManager.trackScreenView("video_splitter")
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            viewModel.onVideoSelected(uri, durationMs)
        } finally {
            retriever.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.video_splitter_title),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = RedAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Feature description
            item {
                Text(
                    text = stringResource(R.string.video_splitter_feature_desc),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Pick video card
            item {
                PickVideoCard(
                    hasVideo = viewModel.selectedVideoUri != null,
                    durationMs = viewModel.videoDurationMs,
                    isDurationValid = viewModel.isDurationValid,
                    onPick = {
                        videoPicker.launch(
                            ActivityResultContracts.PickVisualMedia.VideoOnly
                        )
                    }
                )
            }

            // Split button
            item {
                SplitButton(
                    onClick = { viewModel.split() },
                    enabled = viewModel.isSplitEnabled,
                    label = stringResource(R.string.video_splitter_split_button)
                )
            }

            // Progress
            if (viewModel.isProcessing) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { viewModel.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = RedAccent,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        Text(
                            text = "${(viewModel.progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error
            viewModel.errorMessage?.let { msg ->
                item { ErrorCard(message = msg.ifBlank { stringResource(R.string.video_splitter_error_generic) }) }
            }

            // Results
            if (viewModel.chunks.isNotEmpty()) {
                item {
                    Text(
                        text = "${viewModel.chunks.size} مقاطع",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                itemsIndexed(viewModel.chunks, key = { _, chunk -> chunk.index }) { _, chunk ->
                    VideoChunkCard(
                        chunk = chunk,
                        isSaved = chunk.index in viewModel.savedChunkIndices,
                        saveLabel = stringResource(R.string.video_splitter_save),
                        savedLabel = stringResource(R.string.video_splitter_saved),
                        shareLabel = stringResource(R.string.video_splitter_share),
                        chunkLabel = stringResource(R.string.video_splitter_chunk_label),
                        onSave = { viewModel.saveChunk(context, chunk) },
                        onShare = { viewModel.shareChunk(context, chunk) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PickVideoCard(
    hasVideo: Boolean,
    durationMs: Long,
    isDurationValid: Boolean,
    onPick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RedAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = RedAccent)
            }

            Column(modifier = Modifier.weight(1f)) {
                if (!hasVideo) {
                    Text(
                        text = "اختر فيديو من المعرض",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "الحد الأقصى ٥ دقائق",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = durationMs.toTimeLabel(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isDurationValid) RedAccent else MaterialTheme.colorScheme.error
                    )
                    if (!isDurationValid) {
                        Text(
                            text = "مدة الفيديو تتجاوز ٥ دقائق",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "اضغط لتغيير الفيديو",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitButton(onClick: () -> Unit, enabled: Boolean, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(RedAccent, RedDark))
                else
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    )
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (enabled) Color.White
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("✕", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
    }
}

@Composable
private fun VideoChunkCard(
    chunk: VideoChunk,
    isSaved: Boolean,
    saveLabel: String,
    savedLabel: String,
    shareLabel: String,
    chunkLabel: String,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val animatedFill by animateFloatAsState(
        targetValue = (chunk.endMs - chunk.startMs).toFloat() / CHUNK_DURATION_MS.toFloat(),
        animationSpec = tween(600),
        label = "fill_${chunk.index}"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(RedAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${chunk.index}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "$chunkLabel ${chunk.index}  ·  ${chunk.startMs.toTimeLabel()} – ${chunk.endMs.toTimeLabel()}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedFill },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = RedAccent,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Save button
                AnimatedContent(
                    targetState = isSaved,
                    transitionSpec = {
                        (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                        (scaleOut() + fadeOut())
                    },
                    label = "save_${chunk.index}",
                    modifier = Modifier.weight(1f)
                ) { saved ->
                    OutlinedButton(
                        onClick = { if (!saved) onSave() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (saved) Color(0xFF34C759) else RedAccent
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (saved) Color(0xFF34C759) else RedAccent
                        )
                    ) {
                        Icon(
                            if (saved) Icons.Default.Check else Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (saved) savedLabel else saveLabel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Share button
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAccent),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, RedAccent)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(shareLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun Long.toTimeLabel(): String {
    val totalSec = this / 1000L
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:" | head -20
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterScreen.kt
git commit -m "feat: add VideoSplitterScreen UI"
```

---

## Task 5: Analytics + HomeScreen + Navigation wiring

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/fitit/analytics/AnalyticsManager.kt`
- Modify: `app/src/main/java/tools/mo3ta/fitit/ui/HomeScreen.kt`
- Modify: `app/src/main/java/tools/mo3ta/fitit/MainActivity.kt`

- [ ] **Step 1: Add analytics events**

In `AnalyticsManager.kt`, add after `trackTextSplitterCopyAll`:

```kotlin
    fun trackVideoSplitStarted(durationMs: Long) =
        log("zaki_video_split_started", "duration_ms" to durationMs.toString())

    fun trackVideoChunkSaved() = log("zaki_video_chunk_saved")

    fun trackVideoChunkShared() = log("zaki_video_chunk_shared")
```

- [ ] **Step 2: Add HomeScreen card**

In `HomeScreen.kt`, add `onNavigateToVideoSplitter: () -> Unit` parameter to the function signature:

```kotlin
fun HomeScreen(
    onNavigateToTextImage: () -> Unit,
    onNavigateToEmptyText: () -> Unit,
    onNavigateToOpenWa: () -> Unit,
    onNavigateToTextSplitter: () -> Unit,
    onNavigateToVideoSplitter: () -> Unit,
    onNavigateToSettings: () -> Unit
)
```

Add string reads inside `{ innerPadding ->`:

```kotlin
val videoSplitterTitle = stringResource(R.string.tool_video_splitter)
val videoSplitterDesc = stringResource(R.string.tool_video_splitter_desc)
```

Update `remember(...)` key to include `videoSplitterTitle`:

```kotlin
val tools = remember(textImageTitle, emptyTextTitle, openWaTitle, textSplitterTitle, videoSplitterTitle) {
    listOf(
        ToolItem(textImageTitle, textImageDesc, Icons.Default.AutoAwesome, Color(0xFF007AFF)),
        ToolItem(emptyTextTitle, emptyTextDesc, Icons.Default.VisibilityOff, Color(0xFF5856D6)),
        ToolItem(openWaTitle, openWaDesc, Icons.Default.Chat, Color(0xFF25D366)),
        ToolItem(textSplitterTitle, textSplitterDesc, Icons.Default.ContentCut, Color(0xFFFF9500)),
        ToolItem(videoSplitterTitle, videoSplitterDesc, Icons.Default.VideoLibrary, Color(0xFFFF3B30))
    )
}
```

Update `onClicks remember(...)`:

```kotlin
val onClicks = remember(onNavigateToTextImage, onNavigateToEmptyText, onNavigateToOpenWa, onNavigateToTextSplitter, onNavigateToVideoSplitter) {
    listOf(onNavigateToTextImage, onNavigateToEmptyText, onNavigateToOpenWa, onNavigateToTextSplitter, onNavigateToVideoSplitter)
}
```

Add import at top of file:
```kotlin
import tools.mo3ta.fitit.ui.videosplitter.VideoSplitterScreen
```
(This import will be used in MainActivity — skip in HomeScreen.kt if IDE flags it.)

- [ ] **Step 3: Wire navigation in MainActivity**

In `MainActivity.kt`, add import:
```kotlin
import tools.mo3ta.fitit.ui.videosplitter.VideoSplitterScreen
```

Update `HomeScreen(...)` call to include new callback:
```kotlin
composable("home") {
    HomeScreen(
        onNavigateToTextImage = { navController.navigate("text_image") },
        onNavigateToEmptyText = { navController.navigate("empty_text") },
        onNavigateToOpenWa = { navController.navigate("open_wa") },
        onNavigateToTextSplitter = { navController.navigate("text_splitter") },
        onNavigateToVideoSplitter = { navController.navigate("video_splitter") },
        onNavigateToSettings = { navController.navigate("settings") }
    )
}
```

Add route after the `text_splitter` composable:
```kotlin
composable("video_splitter") {
    VideoSplitterScreen(onBack = { navController.popBackStack() })
}
```

- [ ] **Step 4: Verify full compilation**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 5: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/analytics/AnalyticsManager.kt \
        app/src/main/java/tools/mo3ta/fitit/ui/HomeScreen.kt \
        app/src/main/java/tools/mo3ta/fitit/MainActivity.kt
git commit -m "feat: wire video splitter into home screen and navigation"
```

---

## Self-Review Notes

- All spec requirements covered: gallery pick ✓, 5-min limit ✓, 30s+2s overlap ✓, per-chunk save ✓, per-chunk share ✓, analytics ✓, FileProvider ✓, API 24-28 storage ✓
- `VideoChunk.index` used consistently across ViewModel, Screen, and `savedChunkIndices`
- `CHUNK_DURATION_MS` from `VideoChunkMath.kt` referenced in `VideoSplitterScreen.kt` — both in same package, no import needed
- `toTimeLabel()` defined in Screen file, not in ViewModel — display-only concern, correct placement
- `extractSegment` is a private top-level function in ViewModel file — not unit-testable (requires device), acceptable since it wraps Android system APIs
