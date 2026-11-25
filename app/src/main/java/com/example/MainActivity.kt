package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.BrowserScreen
import com.example.ui.theme.CyberBrowserTheme

class MainActivity : ComponentActivity() {

  private val viewModel: CyberBrowserViewModel by viewModels()
  private var activeWebView: WebView? = null
  private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )

    setContent {
      CyberBrowserTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
        val countdownSeconds by viewModel.duckCountdownSeconds.collectAsStateWithLifecycle()
        val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
        val availableFrenchVoices by viewModel.availableFrenchVoices.collectAsStateWithLifecycle()
        val selectedVoiceId by viewModel.selectedVoiceId.collectAsStateWithLifecycle()
        val audioFocusMode by viewModel.audioFocusMode.collectAsStateWithLifecycle()
        val lastAlertText by viewModel.lastAlertText.collectAsStateWithLifecycle()
        val lastBridgeEvent by viewModel.lastBridgeEvent.collectAsStateWithLifecycle()

        val notificationPermissionLauncher =
          rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
          ) {
            viewModel.refreshSystemStatuses()
          }

        val fileChooserLauncher =
          rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
          ) { result ->
            val callback = pendingFileCallback
            pendingFileCallback = null
            if (callback != null) {
              val uris =
                if (result.resultCode == Activity.RESULT_OK) {
                  val data = result.data
                  val single = data?.data
                  val clip = data?.clipData
                  when {
                    clip != null && clip.itemCount > 0 ->
                      Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    single != null -> arrayOf(single)
                    else -> emptyArray()
                  }
                } else {
                  emptyArray()
                }
              callback.onReceiveValue(uris)
            }
          }

        LaunchedEffect(Unit) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.isNotificationGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        }

        // Met à jour la liste des voix speechSynthesis dans la WebView dès que le moteur TTS Android a fini son scan
        LaunchedEffect(availableFrenchVoices) {
          activeWebView?.evaluateJavascript(
            "window.__cyberRefreshVoices && window.__cyberRefreshVoices();",
            null,
          )
        }

        BrowserScreen(
          uiState = uiState,
          serviceRunning = serviceRunning,
          countdownSeconds = countdownSeconds,
          ttsState = ttsState,
          availableFrenchVoices = availableFrenchVoices,
          selectedVoiceId = selectedVoiceId,
          audioFocusMode = audioFocusMode,
          lastAlertText = lastAlertText,
          lastBridgeEvent = lastBridgeEvent,
          jsBridge = viewModel.jsBridge,
          onWebViewCreated = { webView -> activeWebView = webView },
          onUrlInputChange = viewModel::onUrlInputChange,
          onOpenUrl = viewModel::openUrl,
          onNavigationStateChanged = viewModel::onNavigationStateChanged,
          onProgressChanged = viewModel::onProgressChanged,
          onToggleTopBar = viewModel::setTopBarVisible,
          onOpenTestSheet = { viewModel.setTestSheetOpen(true) },
          onDismissTestSheet = { viewModel.setTestSheetOpen(false) },
          onToggleService = viewModel::toggleBackgroundMonitor,
          onSelectAudioFocusMode = viewModel::setAudioFocusMode,
          onSelectVoice = viewModel::selectVoice,
          onRequestBatteryExclusion = {
            BatteryOptimizationHelper.openBatteryOptimizationRequest(this@MainActivity)
          },
          onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
          },
          onInstallFrenchTts = viewModel::openInstallFrenchTts,
          onOpenTtsSettings = viewModel::openTtsSystemSettings,
          onStartCountdownTest = viewModel::startDuckCountdownTest,
          onImmediateVoiceTest = viewModel::triggerImmediateVoiceTest,
          onLaunchFileChooser = { intent: Intent, callback: ValueCallback<Array<Uri>> ->
            pendingFileCallback?.onReceiveValue(emptyArray())
            pendingFileCallback = callback
            runCatching { fileChooserLauncher.launch(intent) }.onFailure {
              pendingFileCallback = null
              callback.onReceiveValue(emptyArray())
            }
          },
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshSystemStatuses()
    // IMPORTANT : Toujours garder les timers JS actifs et rafraîchir les voix si pack installé
    activeWebView?.resumeTimers()
    activeWebView?.evaluateJavascript(
      "window.__cyberRefreshVoices && window.__cyberRefreshVoices();",
      null,
    )
  }

  override fun onPause() {
    super.onPause()
    // CRITIQUE : Ne JAMAIS appeler activeWebView?.pauseTimers() ni onPause() pour que la WebView tourne en arrière-plan
    activeWebView?.resumeTimers()
  }

  override fun onStop() {
    super.onStop()
    // CRITIQUE : Forcer resumeTimers() pour que setInterval / Firebase listeners continuent quand une autre app est devant
    activeWebView?.resumeTimers()
  }

  override fun onDestroy() {
    pendingFileCallback?.onReceiveValue(emptyArray())
    pendingFileCallback = null
    super.onDestroy()
  }
}
