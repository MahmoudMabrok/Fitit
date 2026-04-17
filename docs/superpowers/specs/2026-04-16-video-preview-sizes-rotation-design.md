# Video Preview, File Sizes & Rotation Fix — Design Spec
**Date:** 2026-04-16  
**Status:** Approved

---

## Overview

Three improvements to the Video Splitter tool:
1. **Preview button** on each chunk card — opens chunk in the system video player
2. **File sizes** — show original video size in the pick card, and each chunk's size in its card
3. **Rotation fix** — split chunks preserve the source video's orientation

---

## 1. Preview

**Trigger:** "معاينة" (Preview) button in each `VideoChunkCard`, alongside the existing Save and Share buttons.

**Implementation:**
- `VideoSplitterViewModel.previewChunk(context: Context, chunk: VideoChunk)`
- Creates a `FileProvider` URI for `chunk.file` (same authority already configured: `${packageName}.provider`)
- Fires `Intent(Intent.ACTION_VIEW)` with `type = "video/mp4"`, `FLAG_GRANT_READ_URI_PERMISSION`, `FLAG_ACTIVITY_NEW_TASK`
- System picks the default video player — no in-app player, no new dependency

**Analytics:** `trackVideoChunkPreviewed()` — new event in `AnalyticsManager`

---

## 2. File Sizes

### Original video size
- Read in `onVideoSelected()` via `context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), ...)`
- Stored as `videoFileSizeBytes: Long` on the ViewModel (0L if query fails)
- Displayed in `PickVideoCard` below the duration label: e.g. `"٤٥.٣ MB"`

### Chunk sizes
- Read after `extractSegment` completes: `chunk.file.length()`
- Stored as `fileSizeBytes: Long` in `VideoChunk` data class
- Displayed in `VideoChunkCard` header row next to the time-range label: e.g. `"٤.٢ MB"`

### Formatting
New `formatFileSize(bytes: Long): String` pure function in `VideoChunkMath.kt`:
- `< 1 MB` → `"X KB"` (1 decimal)
- `≥ 1 MB` → `"X.X MB"` (1 decimal)
- Uses Arabic-numeral rendering consistent with rest of UI (via existing `toArabicNumeral()` pattern but as a standalone format function)

---

## 3. Rotation Fix

**Root cause:** `MediaMuxer` doesn't automatically copy rotation metadata from the source container. Without `setOrientationHint()`, output files lose the rotation flag, causing players to display the video sideways or upside-down.

**Fix in `extractSegment()`:**
```
MediaMetadataRetriever().use { retriever ->
    retriever.setDataSource(context, uri)
    val rotation = retriever.extractMetadata(METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
}
muxer.setOrientationHint(rotation)   // must be called before muxer.start()
```

`setOrientationHint` is called once per segment, before `muxer.start()`. All chunks produced from the same source URI share the same rotation value.

---

## Files Changed

| Action | File | Change |
|--------|------|--------|
| Modify | `VideoChunk` data class | Add `fileSizeBytes: Long` field |
| Modify | `VideoSplitterViewModel.kt` | Add `videoFileSizeBytes`, `previewChunk()`, rotation fix in `extractSegment`, size read after each segment |
| Modify | `VideoChunkMath.kt` | Add `formatFileSize()` pure function |
| Modify | `VideoSplitterScreen.kt` | Preview button in `VideoChunkCard`, size label in `PickVideoCard` + chunk card |
| Modify | `AnalyticsManager.kt` | Add `trackVideoChunkPreviewed()` |
| Modify | `strings.xml` | Add `video_splitter_preview` = "معاينة" |

---

## Out of Scope

- In-app video player / thumbnail generation
- Custom preview UI / seek bar
- Batch preview
