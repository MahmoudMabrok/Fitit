# Design: "Convert to Image" Text Selection Action

**Date:** 2026-04-18
**Status:** Approved

---

## Goal

Register the app as an `ACTION_PROCESS_TEXT` handler so it appears in Android's text-selection context menu (alongside Copy, Translate, etc.) with the app icon and label "Convert to Image". Tapping it opens `TextImageScreen` with the selected text pre-filled.

---

## Architecture

### 1. Manifest — new intent-filter on MainActivity

Add a second `<intent-filter>` inside `<activity android:name=".MainActivity">`:

```xml
<intent-filter>
    <action android:name="android.intent.action.PROCESS_TEXT" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

`android:label` on this filter is **not** set — Android displays the app's launcher label ("Zaki") and icon automatically. The menu item label is controlled by a string resource `process_text_action` used as the activity label override (see §4).

### 2. MainActivity — intent detection + navigation

In `onCreate`, before `setContent`, read the incoming intent:

```kotlin
val processText = intent
    .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
    ?.toString()
    .orEmpty()
```

Pass `processText` into `FitItApp(initialText = processText)`.

Inside `FitItApp`, if `initialText` is non-empty:
- Start destination = `"text_image?text={text}"` (skip onboarding/home)
- Else start destination = `"onboarding"` as today

### 3. Navigation route — add optional text arg

Change the `text_image` composable route:

```kotlin
composable(
    route = "text_image?text={text}",
    arguments = listOf(navArgument("text") { defaultValue = "" })
) {
    TextImageScreen(onBack = { navController.popBackStack() })
}
```

When navigating from home: `navController.navigate("text_image")` — default arg `""` applies, no change in behaviour.

When navigating from `ACTION_PROCESS_TEXT`:
```kotlin
navController.navigate("text_image?text=${Uri.encode(initialText)}")
```

`Uri.encode` handles spaces, newlines, emoji, and other special characters.

### 4. TextImageViewModel — read initial text from SavedStateHandle

Change constructor signature:

```kotlin
class TextImageViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel()
```

Seed `text` state:

```kotlin
var text by mutableStateOf(savedStateHandle.get<String>("text") ?: "")
```

`SavedStateHandle` in Compose Navigation automatically contains the route's `navArgument` values. No other change needed — the existing `reset()` function clears the text normally.

### 5. String resources

Add to both `values/strings.xml` (Arabic) and `values-en/strings.xml` (English):

| Key | Arabic | English |
|---|---|---|
| `process_text_action` | تحويل إلى صورة | Convert to Image |

> **Note:** Android uses the activity `android:label` for the menu item text. The label on the existing `<activity>` tag is `@string/app_name`. To show "Convert to Image" instead of "Zaki" in the menu, the intent-filter must carry its own `android:label="@string/process_text_action"`.

---

## Data Flow

```
User selects text in any app
  → Android shows context menu
    → "Convert to Image" (app icon + process_text_action label)
      → MainActivity.onCreate receives ACTION_PROCESS_TEXT
        → reads EXTRA_PROCESS_TEXT → "Hello world"
          → FitItApp sets startDestination = "text_image?text=Hello%20world"
            → NavHost creates TextImageViewModel via SavedStateHandle
              → text = "Hello world"
                → TextImageScreen shows pre-filled text
```

---

## What Does NOT Change

- Normal launch (app icon) → `initialText` is `""` → existing flow unchanged
- `reset()` in ViewModel still clears text to `""`
- `TextImageScreen` composable signature unchanged
- No read-only / write-back (`EXTRA_PROCESS_TEXT_READONLY`) needed — feature is view-only

---

## Files Touched

| File | Change |
|---|---|
| `AndroidManifest.xml` | Add `ACTION_PROCESS_TEXT` intent-filter with label |
| `MainActivity.kt` | Read intent extra, pass to `FitItApp`, update start destination |
| `TextImageViewModel.kt` | Add `SavedStateHandle` constructor param, seed `text` |
| `values/strings.xml` | Add `process_text_action` Arabic |
| `values-en/strings.xml` | Add `process_text_action` English |

---

## Out of Scope

- Returning modified text back to the source app (`EXTRA_PROCESS_TEXT` write-back)
- Supporting other MIME types (HTML, images)
- A dedicated `ProcessTextActivity` — reusing `MainActivity` is sufficient
