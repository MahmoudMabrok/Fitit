# Text Splitter Feature — Design Spec
Date: 2026-04-15

## Overview

Fourth feature for the Zaki (ذكي) app. Splits long text into word-aware chunks based on a size preset. Displays chunks in a list with per-chunk copy and a "Copy All" that pushes each chunk into clipboard history in reverse order.

---

## Architecture

**New files:**
- `app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterScreen.kt`
- `app/src/main/java/tools/mo3ta/fitit/ui/textsplitter/TextSplitterViewModel.kt`

**Modified files:**
- `MainActivity.kt` — add `text_splitter` nav route
- `HomeScreen.kt` — add ToolCard for تقسيم النص
- `AnalyticsManager.kt` — add 3 new tracking events

---

## ViewModel

```kotlin
enum class SplitPreset(val size: Int?) {
    WHATSAPP(200),
    TWITTER(280),
    CUSTOM(null)
}
```

**State:**
- `inputText: String`
- `selectedPreset: SplitPreset` (default: WHATSAPP)
- `customSize: String` (raw input, parsed to Int; default "100")
- `chunks: List<String>` (empty until user taps Split)
- `error: String?`

**Splitting logic — word-aware:**
```
fun splitText(text: String, chunkSize: Int): List<String>
```
- Iterate words, accumulate into current chunk
- When adding next word would exceed chunkSize, close current chunk and start new one
- A single word longer than chunkSize gets hard-split at the limit

**Split button enabled when:**
- `inputText` is not blank
- If preset = CUSTOM, `customSize.toIntOrNull()` is not null and > 0

---

## UI — TextSplitterScreen

Single scrollable screen (no nested nav). RTL layout (inherited app-wide).

### Top Section
1. **Preset chips** — row of 3 `FilterChip`:
   - واتساب (200)
   - تويتر (280)
   - مخصص
2. **Custom size field** — `OutlinedTextField` with `KeyboardType.Number`, visible only when preset = CUSTOM
3. **Text input** — `OutlinedTextField`, multiline, hint: "أدخل النص هنا..."
4. **Split button** — full-width, enabled per rules above, label: "تقسيم"

### Results Section (shown after split)

**Error state** (text ≤ chunk size):
- Card with message: "النص قصير جداً، لا حاجة للتقسيم"
- No chunk list rendered

**Success state:**
- "نسخ الكل" button at top of results — copies each chunk to clipboard in reverse order with 150ms delay between writes
- `LazyColumn` of chunk cards, each containing:
  - Header row: Arabic ordinal number (١، ٢، ٣...) + char count (e.g. "٢٨٠ حرف")
  - Chunk text (selectable)
  - Copy icon button (copies this chunk only)

---

## HomeScreen Card

- **Label:** تقسيم النص
- **Icon:** `Icons.Default.ContentCut` (scissors)
- **Gradient:** `#FF9500` → `#FF6B00` (orange)
- **Route:** `text_splitter`

---

## Analytics

Three new events added to `AnalyticsManager`:

| Method | Event name | Params |
|--------|-----------|--------|
| `trackScreenView("text_splitter")` | `zaki_screen_view` | `screen_name = "text_splitter"` |
| `trackTextSplitterUsed(preset, chunkCount)` | `zaki_text_splitter_used` | `preset: String`, `chunk_count: Int` |
| `trackTextSplitterCopyAll(chunkCount)` | `zaki_text_splitter_copy_all` | `chunk_count: Int` |

`trackScreenView` fires in `LaunchedEffect(Unit)`.
`trackTextSplitterUsed` fires when Split button is tapped (only on success).
`trackTextSplitterCopyAll` fires when "نسخ الكل" is tapped.

---

## Error Handling

| Condition | Behavior |
|-----------|----------|
| Empty input | Split button disabled |
| Text ≤ chunk size | Error card shown, no chunks |
| Custom size blank or 0 | Split button disabled |
| Single word > chunk size | Hard-split at char limit |

---

## Navigation

Route string: `"text_splitter"`

Added to NavHost in `MainActivity` alongside existing routes:
`onboarding`, `home`, `text_image`, `empty_text`, `open_wa`, `settings`
