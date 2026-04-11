# Settings Screen Design - Fit It App

**Date:** 2026-04-12
**Status:** Design Complete

## Overview

Add a settings screen to the Fit It app that supports:
- Theme selection (Light/Dark/System)
- Language selection (English/Arabic with RTL support)
- App information display

The settings will be persisted using DataStore and integrate with the existing navigation structure.

---

## Architecture

### Navigation Structure
```
MainActivity
└── FitItApp (NavHost)
    ├── HomeScreen         ← add settings icon to top bar
    ├── TextImageScreen    
    ├── EmptyTextScreen    
    └── SettingsScreen     ← new
```

### Data Layer
- **UserPreferences**: Data class holding theme and language preferences
- **SettingsRepository**: Interface with DataStore implementation
- **Persistence**: `datastore/settings_preferences.pb`

### UI Layer
- **SettingsScreen.kt**: Material 3 settings UI
- **SettingsViewModel.kt**: Manages theme and language state

### Theme Integration
- Modify `FititTheme` to accept controlled `darkTheme` parameter (already exists)
- Add `SettingsRepository` to provide theme preference
- MainActivity reads preference and passes to `FititTheme`

### Localization Integration
- Create `values-ar/strings.xml` for Arabic translations
- Update all hardcoded strings to use string resources
- MainActivity handles locale changes via `Configuration`

---

## Settings Screen UI

### Layout Structure
```
TopAppBar
└── "Settings" title with back button

LazyColumn
├── Section: Appearance
│   ├── Theme Preference (SegmentedButton: Light/Dark/System)
│   └── Language Preference (DropdownMenu: English/Arabic)
├── Section: About
│   ├── App Name
│   └── Version Number
└── Spacer for content padding
```

### Components
- **Theme Selector**: `SegmentedButton` row with 3 options
- **Language Selector**: `ExposedDropdownMenuBox` with `DropdownMenuItem`
- **Section Headers**: Uppercase, small, gray text
- **Cards**: Wrapped in `Card` with containerColor = Color(0xFF1E1E1E)

### State Management
- `SettingsViewModel` holds `themePreference` and `languagePreference`
- Changes update DataStore and trigger UI recomposition
- Theme changes restart MainActivity to apply
- Language changes update locale and restart MainActivity

### Visual Style
- Background: 0xFF0F0F0F
- Cards: 0xFF1E1E1E
- Accent color: #007AFF for selected states
- Consistent typography and spacing with existing screens

---

## Localization Implementation

### Arabic Resource Files
- Create `app/src/main/res/values-ar/strings.xml`
- Mirror all strings with Arabic translations
- Add Settings screen strings

### String Resource Structure
```xml
<!-- Existing app strings -->
<string name="app_name">Fit It</string>
<string name="home_title">Content Creator Toolkit</string>
<string name="tool_text_image">Text in Image</string>
<string name="tool_empty_text">Invisible Text</string>

<!-- Settings strings -->
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
<string name="version">Version</string>
```

### Locale Switching
- `SettingsRepository.setLanguage(langCode)` updates DataStore
- `MainActivity` observes language preference
- Use `updateConfiguration` with new `Locale` for Arabic (`"ar"`)
- Call `recreate()` to apply locale changes

### RTL Support
- Android automatically handles RTL when locale is Arabic
- Compose layouts adapt automatically (start/end instead of left/right)
- Icons and images are handled by system for most cases

### String Migration
- Replace all hardcoded strings in existing screens
- Use `stringResource(R.string.key)` Composable function
- Ensure all user-facing text is externalized

---

## Error Handling & Edge Cases

### Data Persistence Errors
- Wrap DataStore operations in `try-catch`
- Provide fallback defaults (System theme, English language)
- Log errors without crashing the app

### Theme Application Errors
- Theme changes require activity recreation
- Use `rememberSaveable` for critical UI state
- ViewModel state survives recreation (scoped to nav graph)
- Handle recreation gracefully without data loss

### Locale Switching Errors
- Validate language codes before applying ("en" and "ar" only)
- Fallback to English if invalid language code stored
- Handle configuration changes during async operations
- Ensure string resources exist for both locales

### UI Edge Cases
- Rapid theme switching: debounce changes
- Missing translations: use English as fallback
- Large screens: adapt layout to tablet widths
- System theme changes: auto-update if "System" selected

---

## Testing Strategy

### Unit Tests
- `SettingsRepositoryTest`: Verify DataStore read/write operations
- `SettingsViewModelTest`: Test state updates and user interactions
- Test locale switching logic in isolation
- Mock DataStore for deterministic tests

### UI Tests
- `SettingsScreenTest`: Compose UI tests for theme and language selection
- Verify all settings options are visible and interactive
- Test navigation from home screen to settings
- Verify back button functionality

### Integration Tests
- End-to-end: Change theme → verify app theme updates
- End-to-end: Change language → verify all strings translate
- Test preference persistence across app restarts
- Verify RTL layout activates for Arabic

### Manual Testing Checklist
- Test all 3 theme options (Light/Dark/System)
- Switch between English and Arabic
- Verify RTL layout direction in Arabic
- Test with system dark mode enabled/disabled
- Confirm preferences persist after app restart
- Test on different screen sizes

---

## File Changes Summary

### New Files
- `ui/SettingsScreen.kt` - Settings UI composable
- `ui/SettingsViewModel.kt` - Settings state management
- `data/SettingsRepository.kt` - DataStore repository
- `data/UserPreferences.kt` - Preferences data class
- `app/src/main/res/values-ar/strings.xml` - Arabic translations

### Modified Files
- `MainActivity.kt` - Add settings route, integrate theme/language preferences
- `ui/HomeScreen.kt` - Add settings icon to top bar, externalize strings
- `ui/TextImageScreen.kt` - Externalize strings to resources
- `ui/EmptyTextScreen.kt` - Externalize strings to resources
- `app/build.gradle.kts` - Add DataStore dependency
- `libs.versions.toml` - Add DataStore version (if needed)

---

## Implementation Notes

1. **DataStore Dependency**: Need to add `androidx.datastore:datastore-preferences` to dependencies
2. **Theme Application**: Theme changes require activity recreation - this is expected behavior
3. **Locale Application**: Language changes also require activity recreation
4. **RTL Testing**: Ensure Arabic strings are properly formatted and RTL layout works correctly
5. **String Migration**: Systematically replace all hardcoded strings across the app

---

## Success Criteria

✅ Settings screen accessible from home screen
✅ Theme switching works (Light/Dark/System) with immediate visual feedback
✅ Language switching works (English/Arabic) with proper translations
✅ RTL layout activates correctly for Arabic
✅ Preferences persist across app restarts
✅ All existing functionality remains intact
✅ No crashes or error states during normal usage
