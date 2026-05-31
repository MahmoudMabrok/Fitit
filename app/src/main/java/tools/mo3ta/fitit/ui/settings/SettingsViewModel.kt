package tools.mo3ta.fitit.ui.settings

import android.app.Application
import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.data.SettingsRepository
import tools.mo3ta.fitit.data.ThemeMode
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM
        )

    private val _cacheSizeBytes = MutableStateFlow(0L)

    /** Total size, in bytes, of the files the app has generated in its cache. */
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache: StateFlow<Boolean> = _isClearingCache.asStateFlow()

    init {
        refreshCacheSize()
    }

    /** Recomputes the current cache size off the main thread. */
    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                getApplication<Application>().cacheDir?.directorySize() ?: 0L
            }
            _cacheSizeBytes.value = size
        }
    }

    /**
     * Deletes the files the app cached (generated tool outputs and temporary files)
     * and refreshes the reported cache size. Invokes [onComplete] with the number of
     * bytes that were freed.
     */
    fun clearCache(onComplete: (freedBytes: Long) -> Unit = {}) {
        if (_isClearingCache.value) return
        _isClearingCache.value = true
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                val cacheDir = getApplication<Application>().cacheDir ?: return@withContext 0L
                val before = cacheDir.directorySize()
                cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                before
            }
            _cacheSizeBytes.value = withContext(Dispatchers.IO) {
                getApplication<Application>().cacheDir?.directorySize() ?: 0L
            }
            _isClearingCache.value = false
            onComplete(freed)
        }
    }

    private fun File.directorySize(): Long {
        if (!exists()) return 0L
        return walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setLanguage(locale: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = getApplication<Application>()
                .getSystemService(LocaleManager::class.java)
            localeManager?.setApplicationLocales(LocaleList.forLanguageTags(locale))
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
        }
    }

    fun getCurrentLocale(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = getApplication<Application>()
                .getSystemService(LocaleManager::class.java)
            val locales = localeManager?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                return locales[0]?.language ?: "ar"
            }
        } else {
            val localeList = AppCompatDelegate.getApplicationLocales()
            if (!localeList.isEmpty) {
                return localeList[0]?.language ?: "ar"
            }
        }
        return "ar"
    }

    fun isNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // notifications are always enabled on API < 33
        }
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }

    private fun playStoreUrl(context: Context): String =
        "https://play.google.com/store/apps/details?id=${context.packageName}"

    /** Opens the app's Google Play listing so the user can update or rate it. */
    fun openPlayStore(context: Context) {
        val packageName = context.packageName
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            // Play Store app not installed; fall back to the web listing.
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(playStoreUrl(context)))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
    }

    /** Opens the system share sheet with the app's Google Play URL. */
    fun shareApp(context: Context) {
        val message = context.getString(R.string.share_app_message, playStoreUrl(context))
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_app_chooser_title))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(chooser)
    }
}
