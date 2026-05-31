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

    fun trackTextSplitterUsed(preset: String, chunkCount: Int) =
        log("zaki_text_splitter_used", "preset" to preset, "chunk_count" to chunkCount.toString())

    fun trackTextSplitterCopyAll(chunkCount: Int) =
        log("zaki_text_splitter_copy_all", "chunk_count" to chunkCount.toString())

    fun trackThemeChanged(theme: String) = log("zaki_settings_theme_changed", "theme" to theme)

    fun trackLanguageChanged(language: String) = log("zaki_settings_language_changed", "language" to language)

    fun trackNotificationsSettingsOpened() = log("zaki_settings_notifications_opened")

    fun trackPlayStoreOpened() = log("zaki_settings_play_store_opened")

    fun trackShareApp() = log("zaki_settings_share_app")

    fun trackVideoSplitStarted(durationMs: Long) =
        log("zaki_video_split_started", "duration_ms" to durationMs.toString())

    fun trackVideoChunkSaved() = log("zaki_video_chunk_saved")

    fun trackVideoChunkShared() = log("zaki_video_chunk_shared")

    fun trackVideoChunkPreviewed() = log("zaki_video_chunk_previewed")

    fun trackVideoEnhanceStarted(durationMs: Long, level: String) =
        log("zaki_video_enhance_started", "duration_ms" to durationMs.toString(), "level" to level)

    fun trackVideoEnhanceCompleted(level: String) =
        log("zaki_video_enhance_completed", "level" to level)

    fun trackVideoEnhanceSaved() = log("zaki_video_enhance_saved")

    fun trackVideoEnhanceShared() = log("zaki_video_enhance_shared")

    fun trackMediaMergeStarted(type: String, count: Int) =
        log("zaki_media_merge_started", "type" to type, "count" to count.toString())

    fun trackMediaMergeCompleted(type: String) =
        log("zaki_media_merge_completed", "type" to type)

    fun trackMediaMergeSaved() = log("zaki_media_merge_saved")

    fun trackMediaMergeShared() = log("zaki_media_merge_shared")

    fun trackAudioExtractStarted(format: String) =
        log("zaki_audio_extract_started", "format" to format)

    fun trackAudioExtractCompleted(format: String) =
        log("zaki_audio_extract_completed", "format" to format)

    fun trackAudioSaved(format: String) = log("zaki_audio_saved", "format" to format)

    fun trackAudioShared(format: String) = log("zaki_audio_shared", "format" to format)

    fun trackAudioEnhanceStarted(level: String, ai: Boolean = false) =
        log("zaki_audio_enhance_started", "level" to level, "ai" to ai.toString())

    fun trackAudioEnhanceCompleted(level: String, ai: Boolean = false) =
        log("zaki_audio_enhance_completed", "level" to level, "ai" to ai.toString())

    fun trackAudioEnhanceSaved() = log("zaki_audio_enhance_saved")

    fun trackAudioEnhanceShared() = log("zaki_audio_enhance_shared")

    private fun log(name: String, vararg params: Pair<String, String>) {
        val bundle = Bundle()
        params.forEach { (key, value) -> bundle.putString(key, value) }
        Firebase.analytics.logEvent(name, bundle)
    }
}
