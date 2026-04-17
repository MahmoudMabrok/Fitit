# Video Preview, File Sizes & Rotation Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-chunk preview via system player, show original + chunk file sizes, and fix output video rotation by preserving source orientation metadata.

**Architecture:** Pure logic (`formatFileSize`) added to `VideoChunkMath.kt`. ViewModel gains `videoFileSizeBytes`, `fileSizeBytes` on `VideoChunk`, `previewChunk()`, and rotation fix in `extractSegment()`. Screen adds Preview button and size labels. One new analytics event. One new string resource.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidViewModel, MediaMetadataRetriever (rotation), ContentResolver (file size), FileProvider + ACTION_VIEW (preview), Firebase Analytics.

---

## File Map

| Action | File | Change |
|--------|------|--------|
| Modify | `ui/videosplitter/VideoChunkMath.kt` | Add `formatFileSize()` |
| Modify | `test/.../videosplitter/VideoChunkMathTest.kt` | Tests for `formatFileSize()` |
| Modify | `ui/videosplitter/VideoSplitterViewModel.kt` | `VideoChunk.fileSizeBytes`, `videoFileSizeBytes`, `previewChunk()`, rotation fix, size read |
| Modify | `test/.../videosplitter/VideoSplitterViewModelTest.kt` | Test `videoFileSizeBytes` resets on new selection |
| Modify | `ui/videosplitter/VideoSplitterScreen.kt` | Preview button, size labels in PickVideoCard + chunk card |
| Modify | `analytics/AnalyticsManager.kt` | `trackVideoChunkPreviewed()` |
| Modify | `res/values/strings.xml` | `video_splitter_preview` string |

---

## Task 1: `formatFileSize` pure function + unit tests

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMath.kt`
- Modify: `app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMathTest.kt`

- [ ] **Step 1: Write failing tests**

Add to the bottom of `VideoChunkMathTest.kt` (inside the class, before the closing `}`):

```kotlin
    @Test
    fun `formatFileSize returns KB for bytes under 1MB`() {
        assertEquals("512.0 KB", formatFileSize(524_288L))
    }

    @Test
    fun `formatFileSize returns MB for bytes at or over 1MB`() {
        assertEquals("1.0 MB", formatFileSize(1_048_576L))
    }

    @Test
    fun `formatFileSize rounds to one decimal`() {
        assertEquals("4.2 MB", formatFileSize(4_404_019L))
    }

    @Test
    fun `formatFileSize returns 0 point 0 KB for zero bytes`() {
        assertEquals("0.0 KB", formatFileSize(0L))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/appleworld/AndroidStudioProjects/Fitit
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.videosplitter.VideoChunkMathTest" 2>&1 | tail -10
```

Expected: compilation error — `formatFileSize` not defined.

- [ ] **Step 3: Add `formatFileSize` to VideoChunkMath.kt**

Append to the end of `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMath.kt`:

```kotlin

fun formatFileSize(bytes: Long): String {
    return if (bytes < 1_048_576L) {
        "%.1f KB".format(bytes / 1024.0)
    } else {
        "%.1f MB".format(bytes / 1_048_576.0)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.videosplitter.VideoChunkMathTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMath.kt \
        app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoChunkMathTest.kt
git commit -m "feat: add formatFileSize helper to VideoChunkMath"
```

---

## Task 2: ViewModel — file sizes, preview, rotation fix

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModel.kt`
- Modify: `app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModelTest.kt`
- Modify: `app/src/main/java/tools/mo3ta/fitit/analytics/AnalyticsManager.kt`

- [ ] **Step 1: Write failing ViewModel test**

Add to the bottom of `VideoSplitterViewModelTest.kt` (inside the class, before `}`):

```kotlin
    @Test
    fun `onVideoSelected resets videoFileSizeBytes`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 60_000L)
        // After selection videoFileSizeBytes is reset to 0 (ContentResolver unavailable in unit test)
        assertEquals(0L, vm.videoFileSizeBytes)
    }
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.videosplitter.VideoSplitterViewModelTest" 2>&1 | tail -10
```

Expected: compilation error — `videoFileSizeBytes` not defined.

- [ ] **Step 3: Add analytics event to AnalyticsManager**

In `app/src/main/java/tools/mo3ta/fitit/analytics/AnalyticsManager.kt`, add after `trackVideoChunkShared()`:

```kotlin
    fun trackVideoChunkPreviewed() = log("zaki_video_chunk_previewed")
```

- [ ] **Step 4: Update VideoChunk data class**

In `VideoSplitterViewModel.kt`, change the `VideoChunk` data class from:

```kotlin
data class VideoChunk(
    val file: File,
    val startMs: Long,
    val endMs: Long,
    val index: Int
)
```

To:

```kotlin
data class VideoChunk(
    val file: File,
    val startMs: Long,
    val endMs: Long,
    val index: Int,
    val fileSizeBytes: Long = 0L
)
```

- [ ] **Step 5: Add `videoFileSizeBytes` state + `readFileSize` + update `onVideoSelected`**

In `VideoSplitterViewModel.kt`:

After the `savedChunkIndices` state declaration, add:

```kotlin
    var videoFileSizeBytes by mutableStateOf(0L)
        private set
```

Add this private helper function inside the class (after `shareChunk`):

```kotlin
    private fun readFileSize(uri: Uri): Long = try {
        getApplication<Application>().contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
    } catch (_: Exception) { 0L }
```

Update `onVideoSelected` to also reset and read file size:

```kotlin
    fun onVideoSelected(uri: Uri, durationMs: Long) {
        selectedVideoUri = uri
        videoDurationMs = durationMs
        chunks = emptyList()
        errorMessage = null
        savedChunkIndices = emptySet()
        videoFileSizeBytes = readFileSize(uri)
    }
```

- [ ] **Step 6: Update `split()` to capture chunk file size**

In `split()`, change the `VideoChunk(...)` construction inside `withContext(Dispatchers.IO)` from:

```kotlin
VideoChunk(outputFile, range.startMs, range.endMs, range.index)
```

To:

```kotlin
VideoChunk(outputFile, range.startMs, range.endMs, range.index, outputFile.length())
```

- [ ] **Step 7: Add `previewChunk` function**

Add after `shareChunk` in `VideoSplitterViewModel.kt`:

```kotlin
    fun previewChunk(context: Context, chunk: VideoChunk) {
        val fileUri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", chunk.file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        try { AnalyticsManager.trackVideoChunkPreviewed() } catch (_: Exception) {}
    }
```

- [ ] **Step 8: Fix rotation in `extractSegment`**

In the `extractSegment` private function, add rotation detection before the `MediaExtractor` block. Replace the current opening of `extractSegment`:

```kotlin
private fun extractSegment(
    context: Context,
    uri: Uri,
    startMs: Long,
    endMs: Long,
    output: File
) {
    val extractor = MediaExtractor()
```

With:

```kotlin
private fun extractSegment(
    context: Context,
    uri: Uri,
    startMs: Long,
    endMs: Long,
    output: File
) {
    val rotation: Int
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        rotation = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
        )?.toIntOrNull() ?: 0
    } finally {
        retriever.release()
    }

    val extractor = MediaExtractor()
```

Then, in the `muxer` try block, add `muxer.setOrientationHint(rotation)` immediately before `muxer.start()`:

```kotlin
            muxer.setOrientationHint(rotation)
            muxer.start()
```

The required import `android.media.MediaMetadataRetriever` is already imported at the top of the screen file — verify it's present in the ViewModel file too. If not, add:
```kotlin
import android.media.MediaMetadataRetriever
```

- [ ] **Step 9: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.videosplitter.*" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModel.kt \
        app/src/test/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterViewModelTest.kt \
        app/src/main/java/tools/mo3ta/fitit/analytics/AnalyticsManager.kt
git commit -m "feat: add file sizes, preview, and rotation fix to VideoSplitterViewModel"
```

---

## Task 3: Screen — string, size labels, preview button

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterScreen.kt`

- [ ] **Step 1: Add string resource**

In `app/src/main/res/values/strings.xml`, add inside the `<!-- Video Splitter Screen -->` block:

```xml
    <string name="video_splitter_preview">معاينة</string>
```

- [ ] **Step 2: Update `PickVideoCard` to show original file size**

In `VideoSplitterScreen.kt`, update the `PickVideoCard` function signature — add `videoFileSizeBytes: Long`:

```kotlin
@Composable
private fun PickVideoCard(
    hasVideo: Boolean,
    durationMs: Long,
    isDurationValid: Boolean,
    videoFileSizeBytes: Long,
    pickVideoLabel: String,
    durationErrorLabel: String,
    maxDurationLabel: String,
    tapToChangeLabel: String,
    onPick: () -> Unit
)
```

Inside `PickVideoCard`, in the `else` branch (when `hasVideo == true` and `isDurationValid == true`), after the `tapToChangeLabel` Text, add the file size line:

```kotlin
                    if (isDurationValid) {
                        Text(
                            text = tapToChangeLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (videoFileSizeBytes > 0L) {
                            Text(
                                text = formatFileSize(videoFileSizeBytes),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
```

Update the call site in `VideoSplitterScreen` (inside the `LazyColumn`) to pass the new parameter:

```kotlin
            item {
                PickVideoCard(
                    hasVideo = viewModel.selectedVideoUri != null,
                    durationMs = viewModel.videoDurationMs,
                    isDurationValid = viewModel.isDurationValid,
                    videoFileSizeBytes = viewModel.videoFileSizeBytes,
                    pickVideoLabel = stringResource(R.string.video_splitter_pick_video),
                    durationErrorLabel = stringResource(R.string.video_splitter_duration_error),
                    maxDurationLabel = maxDurationLabel,
                    tapToChangeLabel = tapToChangeLabel,
                    onPick = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                )
            }
```

- [ ] **Step 3: Update `VideoChunkCard` — add size label and Preview button**

Update the `VideoChunkCard` function signature to add `fileSizeBytes: Long`, `previewLabel: String`, and `onPreview: () -> Unit`:

```kotlin
@Composable
private fun VideoChunkCard(
    chunk: VideoChunk,
    isSaved: Boolean,
    saveLabel: String,
    savedLabel: String,
    shareLabel: String,
    previewLabel: String,
    chunkLabel: String,
    fileSizeBytes: Long,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPreview: () -> Unit
)
```

In the header `Text` (time-range label), add the file size after the time range. Replace:

```kotlin
                    Text(
                        text = "$chunkLabel ${chunk.index}  ·  ${chunk.startMs.toTimeLabel()} – ${chunk.endMs.toTimeLabel()}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
```

With:

```kotlin
                    Column {
                        Text(
                            text = "$chunkLabel ${chunk.index}  ·  ${chunk.startMs.toTimeLabel()} – ${chunk.endMs.toTimeLabel()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (fileSizeBytes > 0L) {
                            Text(
                                text = formatFileSize(fileSizeBytes),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
```

In the actions section of `VideoChunkCard`, add the Preview button as a full-width row **above** the Save/Share row. Replace the existing actions `Row(...)` block with:

```kotlin
            // Preview button — full width
            OutlinedButton(
                onClick = onPreview,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAccent),
                border = BorderStroke(1.5.dp, RedAccent)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(previewLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            // Save + Share row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        border = BorderStroke(
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

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAccent),
                    border = BorderStroke(1.5.dp, RedAccent)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(shareLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
```

Add the `Icons.Default.PlayArrow` import to the top of the file:

```kotlin
import androidx.compose.material.icons.filled.PlayArrow
```

- [ ] **Step 4: Update `VideoChunkCard` call site in `VideoSplitterScreen`**

In the `itemsIndexed` block in `VideoSplitterScreen`, update the `VideoChunkCard(...)` call:

```kotlin
                itemsIndexed(viewModel.chunks, key = { _, chunk -> chunk.index }) { _, chunk ->
                    VideoChunkCard(
                        chunk = chunk,
                        isSaved = chunk.index in viewModel.savedChunkIndices,
                        saveLabel = stringResource(R.string.video_splitter_save),
                        savedLabel = stringResource(R.string.video_splitter_saved),
                        shareLabel = stringResource(R.string.video_splitter_share),
                        previewLabel = stringResource(R.string.video_splitter_preview),
                        chunkLabel = stringResource(R.string.video_splitter_chunk_label),
                        fileSizeBytes = chunk.fileSizeBytes,
                        onSave = { viewModel.saveChunk(context, chunk) },
                        onShare = { viewModel.shareChunk(context, chunk) },
                        onPreview = { viewModel.previewChunk(context, chunk) }
                    )
                }
```

- [ ] **Step 5: Verify compilation**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep "^e:" | head -20
```

Expected: no errors.

- [ ] **Step 6: Run all video splitter tests**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.fitit.ui.videosplitter.*" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/java/tools/mo3ta/fitit/ui/videosplitter/VideoSplitterScreen.kt
git commit -m "feat: add preview button, file size labels to video splitter UI"
```

---

## Self-Review

- **Spec coverage:** `formatFileSize` ✓ · original video size ✓ · chunk size ✓ · preview button ✓ · system player intent ✓ · rotation fix via `setOrientationHint` ✓ · analytics `trackVideoChunkPreviewed` ✓ · string resource ✓
- **No placeholders found**
- **Type consistency:** `VideoChunk.fileSizeBytes: Long` defined in Task 2 Step 4, used in Task 3 Steps 3–4 ✓ · `formatFileSize(bytes: Long)` defined Task 1, used in Task 3 ✓ · `videoFileSizeBytes: Long` on ViewModel defined Task 2 Step 5, accessed in Task 3 Step 2 ✓ · `previewChunk(context, chunk)` defined Task 2 Step 7, called in Task 3 Step 4 ✓
