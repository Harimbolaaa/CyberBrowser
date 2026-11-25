package com.example

import android.content.Context
import android.content.SharedPreferences

enum class AudioFocusMode {
  DUCK,
  PAUSE_RESUME,
}

class BrowserPreferences(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  var savedUrl: String
    get() = prefs.getString(KEY_SAVED_URL, DEFAULT_URL) ?: DEFAULT_URL
    set(value) {
      prefs.edit().putString(KEY_SAVED_URL, normalizeUrl(value)).apply()
    }

  var monitorEnabled: Boolean
    get() = prefs.getBoolean(KEY_MONITOR_ENABLED, true)
    set(value) {
      prefs.edit().putBoolean(KEY_MONITOR_ENABLED, value).apply()
    }

  var audioFocusMode: AudioFocusMode
    get() {
      val raw = prefs.getString(KEY_AUDIO_FOCUS_MODE, AudioFocusMode.DUCK.name)
      return runCatching { AudioFocusMode.valueOf(raw ?: AudioFocusMode.DUCK.name) }
        .getOrDefault(AudioFocusMode.DUCK)
    }
    set(value) {
      prefs.edit().putString(KEY_AUDIO_FOCUS_MODE, value.name).apply()
    }

  var preferredVoiceId: String?
    get() = prefs.getString(KEY_PREFERRED_VOICE_ID, null)
    set(value) {
      prefs.edit().putString(KEY_PREFERRED_VOICE_ID, value).apply()
    }

  companion object {
    private const val PREFS_NAME = "cyber_browser_prefs"
    private const val KEY_SAVED_URL = "saved_url"
    private const val KEY_MONITOR_ENABLED = "background_monitor_enabled"
    private const val KEY_AUDIO_FOCUS_MODE = "audio_focus_mode"
    private const val KEY_PREFERRED_VOICE_ID = "preferred_voice_id"
    const val DEFAULT_URL = "https://cyber-cyber.web.app"

    fun normalizeUrl(raw: String): String {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) return DEFAULT_URL
      return if (
        trimmed.startsWith("https://", ignoreCase = true) ||
          trimmed.startsWith("http://", ignoreCase = true)
      ) {
        trimmed
      } else {
        "https://$trimmed"
      }
    }
  }
}
