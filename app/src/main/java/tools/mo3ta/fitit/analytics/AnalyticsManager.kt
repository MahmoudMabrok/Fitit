package tools.mo3ta.fitit.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

object AnalyticsManager {

    fun trackAppOpen() = log("zaki_app_open")

    fun trackOnboardingCompleted() = log("zaki_onboarding_completed")

    fun trackScreenView(screenName: String) = log("zaki_screen_view", "screen_name" to screenName)

    fun trackTextImageExported() = log("zaki_text_image_exported")

    fun trackTextImageBackgroundPicked() = log("zaki_text_image_background_picked")

    fun trackEmptyTextCopied(charType: String, length: Int) =
        log("zaki_empty_text_copied", "char_type" to charType, "length" to length.toString())

    fun trackOpenWaSent() = log("zaki_open_wa_sent")

    fun trackThemeChanged(theme: String) = log("zaki_settings_theme_changed", "theme" to theme)

    fun trackLanguageChanged(language: String) = log("zaki_settings_language_changed", "language" to language)

    fun trackNotificationsSettingsOpened() = log("zaki_settings_notifications_opened")

    private fun log(name: String, vararg params: Pair<String, String>) {
        val bundle = Bundle()
        params.forEach { (key, value) -> bundle.putString(key, value) }
        Firebase.analytics.logEvent(name, bundle)
    }
}
