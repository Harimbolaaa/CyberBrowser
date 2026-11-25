package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BrowserUiState(
  val urlInput: String = BrowserPreferences.DEFAULT_URL,
  val currentUrl: String = BrowserPreferences.DEFAULT_URL,
  val pageTitle: String = "CyberBrowser",
  val loadProgress: Int = 0,
  val isLoading: Boolean = false,
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false,
  val isTopBarVisible: Boolean = true,
  val isTestSheetOpen: Boolean = false,
  val isBatteryIgnored: Boolean = false,
  val isNotificationGranted: Boolean = true,
)

class CyberBrowserViewModel(application: Application) : AndroidViewModelWithApp(application) {

  private val prefs = BrowserPreferences(application)
  private val audioDuckTtsManager = AudioDuckTtsManager.getInstance(application)

  val jsBridge =
    CyberJsBridge(context = application, onBatteryStatusChanged = { refreshSystemStatuses() })

  val serviceRunning: StateFlow<Boolean> = KeepAliveService.isRunning
  val duckCountdownSeconds: StateFlow<Int?> = KeepAliveService.countdownSeconds
  val ttsState: StateFlow<TtsEngineState> = audioDuckTtsManager.ttsState
  val availableFrenchVoices: StateFlow<List<TtsVoiceInfo>> =
    audioDuckTtsManager.availableFrenchVoices
  val selectedVoiceId: StateFlow<String?> = audioDuckTtsManager.selectedVoiceId
  val audioFocusMode: StateFlow<AudioFocusMode> = audioDuckTtsManager.audioFocusMode
  val lastAlertText: StateFlow<String> = audioDuckTtsManager.lastAlertText
  val lastBridgeEvent: StateFlow<String> = jsBridge.lastBridgeEvent

  private val _uiState =
    MutableStateFlow(
      BrowserUiState(
        urlInput = prefs.savedUrl,
        currentUrl = prefs.savedUrl,
        isBatteryIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(application),
        isNotificationGranted = checkNotificationPermission(application),
      )
    )
  val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

  init {
    if (prefs.monitorEnabled) {
      KeepAliveService.startMonitor(application)
    }
  }

  fun onUrlInputChange(newValue: String) {
    _uiState.update { it.copy(urlInput = newValue) }
  }

  fun openUrl(rawUrl: String): String {
    val normalized = BrowserPreferences.normalizeUrl(rawUrl)
    prefs.savedUrl = normalized
    _uiState.update { it.copy(urlInput = normalized, currentUrl = normalized) }
    return normalized
  }

  fun onNavigationStateChanged(
    url: String?,
    title: String?,
    canGoBack: Boolean,
    canGoForward: Boolean,
  ) {
    val cleanUrl = url?.takeIf { it.isNotBlank() }
    if (cleanUrl != null && !cleanUrl.startsWith("about:")) {
      prefs.savedUrl = cleanUrl
    }
    _uiState.update { state ->
      state.copy(
        urlInput = cleanUrl ?: state.urlInput,
        currentUrl = cleanUrl ?: state.currentUrl,
        pageTitle = title?.takeIf { it.isNotBlank() } ?: state.pageTitle,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
      )
    }
  }

  fun onProgressChanged(progress: Int) {
    _uiState.update {
      it.copy(loadProgress = progress, isLoading = progress in 1..99)
    }
  }

  fun setTopBarVisible(visible: Boolean) {
    _uiState.update { it.copy(isTopBarVisible = visible) }
  }

  fun setTestSheetOpen(open: Boolean) {
    if (open) {
      refreshSystemStatuses()
      audioDuckTtsManager.reloadVoices()
    }
    _uiState.update { it.copy(isTestSheetOpen = open) }
  }

  fun refreshSystemStatuses() {
    val app = getApplication<Application>()
    audioDuckTtsManager.reloadVoices()
    _uiState.update {
      it.copy(
        isBatteryIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(app),
        isNotificationGranted = checkNotificationPermission(app),
      )
    }
  }

  fun toggleBackgroundMonitor(enable: Boolean) {
    val app = getApplication<Application>()
    prefs.monitorEnabled = enable
    if (enable) {
      KeepAliveService.startMonitor(app)
    } else {
      KeepAliveService.stopMonitor(app)
    }
  }

  fun setAudioFocusMode(mode: AudioFocusMode) {
    audioDuckTtsManager.setAudioFocusMode(mode)
  }

  fun selectVoice(voiceId: String) {
    audioDuckTtsManager.selectVoice(voiceId)
  }

  fun startDuckCountdownTest() {
    val app = getApplication<Application>()
    prefs.monitorEnabled = true
    KeepAliveService.startDuckTestCountdown(app)
  }

  fun triggerImmediateVoiceTest() {
    audioDuckTtsManager.speakAndDuck(
      message = "Test immédiat CyberBrowser. L'audio en arrière-plan est baissé puis restauré.",
      repeatCount = 1,
      playChime = true,
      title = "Test immédiat CyberBrowser",
    )
  }

  fun openInstallFrenchTts() {
    audioDuckTtsManager.openInstallFrenchVoiceSettings(getApplication())
  }

  fun openTtsSystemSettings() {
    audioDuckTtsManager.openTtsSystemSettings(getApplication())
  }

  private fun checkNotificationPermission(app: Application): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
}

open class AndroidViewModelWithApp(application: Application) :
  androidx.lifecycle.AndroidViewModel(application)
