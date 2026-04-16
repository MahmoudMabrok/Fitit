# Video Splitter Feature — Design Spec
**Date:** 2026-04-16  
**Status:** Approved

---

## Overview

New tool in Fitit that lets users pick a video from gallery (max 5 minutes), split it into 30-second overlapping chunks (2s overlap with next chunk), then save or share each chunk individually.

---

## Chunk Math

| Parameter | Value |
|-----------|-------|
| Max input duration | 300s (5 min) |
| Chunk length | 32s (30s content + 2s overlap into next) |
| Step between chunk starts | 30s |
| Max chunks | ~10 |

**Formula:**
- Chunk i: `start = i * 30_000ms`, `end = start + 32_000ms` (clamped to video end)
- Last chunk ends at video end regardless of length

---

## Architecture

### Files
```
ui/videosplitter/
  VideoSplitterScreen.kt
  VideoSplitterViewModel.kt
```

Follows existing ViewModel + Composable screen pattern used by all other tools.

### Navigation
- Route: `video_splitter`
- Added to `NavHost` in `MainActivity`
- New card on `HomeScreen` with red accent `Color(0xFFFF3B30)`

---

## ViewModel (`VideoSplitterViewModel`)

**State**
```kotlin
var selectedVideoUri: Uri? 
var videoDurationMs: Long
var isProcessing: Boolean
var progress: Float          // 0.0 → 1.0
var chunks: List<VideoChunk> // output
var errorMessage: String?
```

**Data class**
```kotlin
data class VideoChunk(
    val file: File,          // in app cache dir
    val startMs: Long,
    val endMs: Long,
    val index: Int
)
```

**Actions**
- `onVideoSelected(uri, durationMs)` — validates ≤ 300s, stores uri
- `split()` — launches coroutine on `Dispatchers.IO`, calls `splitVideo()`
- `saveChunk(context, chunk)` — copies file to MediaStore `Movies/Fitit/`
- `shareChunk(context, chunk)` — fires `ACTION_SEND` intent via `FileProvider`

**Processing (`splitVideo`)**  
Uses `MediaExtractor` + `MediaMuxer` — no external dependencies, no re-encoding, lossless quality.

```
for each chunk i:
  - create MediaExtractor, seekTo(startMs, SEEK_TO_PREVIOUS_SYNC)
  - create MediaMuxer → output File in cacheDir
  - copy all tracks: advance sample-by-sample until sample time ≥ endMs
  - release both
  - emit progress = (i+1) / totalChunks
```

Keyframe-seek (`SEEK_TO_PREVIOUS_SYNC`) means actual start may be up to ~1s before target — acceptable for video splitting use case.

---

## UI (`VideoSplitterScreen`)

**Sections (top → bottom in LazyColumn):**

1. **Feature description** — one-line summary
2. **Pick video card** — button launches `PickVisualMedia(VIDEO)` contract
3. **Selected video info row** — filename + duration. Red error text if > 5 min.
4. **Split button** — gradient, disabled if no video or validation fails
5. **Progress bar** — visible only while `isProcessing == true`
6. **Results** — `LazyColumn` of chunk cards (see below)

**Chunk card:**
- Header: index badge + time range label (e.g., "0:00 – 0:32")
- Save button → calls `saveChunk()`
- Share button → calls `shareChunk()`
- Save shows checkmark animation on success (same pattern as TextSplitter copy)

**Accent color:** `Color(0xFFFF3B30)` — red, distinct from all existing tool colors

---

## Permissions

| API | Needed for | How |
|-----|-----------|-----|
| All | Gallery pick | `PickVisualMedia` contract — no runtime permission needed |
| 24–28 | Save to storage | `WRITE_EXTERNAL_STORAGE` in manifest + runtime request |
| 29+ | Save to MediaStore | No extra permission needed |

Manifest: add `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`.

FileProvider entry needed in manifest for sharing cached chunk files.

---

## Error States

| Condition | Handling |
|-----------|---------|
| Video > 5 min | Red inline error below video info, split button disabled |
| No video selected | Split button disabled |
| MediaExtractor/Muxer failure | `errorMessage` set, shown in ErrorCard |
| Save to MediaStore failure | Snackbar or error on chunk card |

---

## Analytics

- `trackScreenView("video_splitter")` on screen open
- `trackEvent("video_split_started", duration)` when split begins
- `trackEvent("video_chunk_saved")` / `trackEvent("video_chunk_shared")` per action

---

## Out of Scope

- Custom chunk length (fixed 30s + 2s overlap)
- Camera recording as input
- Trimming / preview playback
- Background processing / notification when done
