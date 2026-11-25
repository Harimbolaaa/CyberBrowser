package com.example.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BatteryOptimizationHelper
import com.example.BrowserUiState
import com.example.CyberJsBridge
import com.example.R
import com.example.TtsEngineState
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.RoseAlert

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
  uiState: BrowserUiState,
  serviceRunning: Boolean,
  countdownSeconds: Int?,
  ttsState: TtsEngineState,
  availableFrenchVoices: List<com.example.TtsVoiceInfo>,
  selectedVoiceId: String?,
  audioFocusMode: com.example.AudioFocusMode,
  lastAlertText: String,
  lastBridgeEvent: String,
  jsBridge: CyberJsBridge,
  onWebViewCreated: (WebView) -> Unit,
  onUrlInputChange: (String) -> Unit,
  onOpenUrl: (String) -> String,
  onNavigationStateChanged: (String?, String?, Boolean, Boolean) -> Unit,
  onProgressChanged: (Int) -> Unit,
  onToggleTopBar: (Boolean) -> Unit,
  onOpenTestSheet: () -> Unit,
  onDismissTestSheet: () -> Unit,
  onToggleService: (Boolean) -> Unit,
  onSelectAudioFocusMode: (com.example.AudioFocusMode) -> Unit,
  onSelectVoice: (String) -> Unit,
  onRequestBatteryExclusion: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onInstallFrenchTts: () -> Unit,
  onOpenTtsSettings: () -> Unit,
  onStartCountdownTest: () -> Unit,
  onImmediateVoiceTest: () -> Unit,
  onLaunchFileChooser: (Intent, ValueCallback<Array<Uri>>) -> Unit,
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  BackHandler(enabled = uiState.canGoBack) {
    webViewRef?.takeIf { it.canGoBack() }?.goBack()
  }

  DisposableEffect(Unit) {
    onDispose {
      // Ne jamais appeler pauseTimers() ici afin de préserver les timers JS si réattaché.
      webViewRef = null
    }
  }

  val allHealthy =
    serviceRunning && uiState.isBatteryIgnored && ttsState == TtsEngineState.READY

  Box(
    modifier =
      Modifier.fillMaxSize().background(ObsidianBg).navigationBarsPadding().imePadding()
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Barre DNS / URL compacte et masquable
      AnimatedVisibility(
        visible = uiState.isTopBarVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(modifier = Modifier.fillMaxWidth().background(ObsidianSurface).statusBarsPadding()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            IconButton(
              onClick = { webViewRef?.takeIf { it.canGoBack() }?.goBack() },
              enabled = uiState.canGoBack,
              modifier = Modifier.size(40.dp).minimumInteractiveComponentSize().testTag("back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                modifier = Modifier.size(18.dp),
              )
            }

            IconButton(
              onClick = { webViewRef?.takeIf { it.canGoForward() }?.goForward() },
              enabled = uiState.canGoForward,
              modifier =
                Modifier.size(40.dp).minimumInteractiveComponentSize().testTag("forward_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.action_forward),
                modifier = Modifier.size(18.dp),
              )
            }

            IconButton(
              onClick = { webViewRef?.reload() },
              modifier =
                Modifier.size(40.dp).minimumInteractiveComponentSize().testTag("reload_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.action_reload),
                modifier = Modifier.size(18.dp),
              )
            }

            // Champ URL + Bouton Ouvrir
            Row(
              modifier =
                Modifier.weight(1f)
                  .height(38.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(ObsidianSurfaceHigh)
                  .border(1.dp, ObsidianOutline, RoundedCornerShape(10.dp))
                  .padding(start = 10.dp, end = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (uiState.urlInput.isEmpty()) {
                  Text(
                    text = stringResource(R.string.url_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                BasicTextField(
                  value = uiState.urlInput,
                  onValueChange = onUrlInputChange,
                  singleLine = true,
                  textStyle =
                    MaterialTheme.typography.labelMedium.copy(
                      color = MaterialTheme.colorScheme.onSurface
                    ),
                  cursorBrush = SolidColor(ElectricCyan),
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Uri,
                      imeAction = ImeAction.Go,
                    ),
                  keyboardActions =
                    KeyboardActions(
                      onGo = {
                        focusManager.clearFocus()
                        val target = onOpenUrl(uiState.urlInput)
                        webViewRef?.loadUrl(target)
                      }
                    ),
                  modifier = Modifier.fillMaxWidth().testTag("url_input"),
                )
              }

              Surface(
                shape = RoundedCornerShape(7.dp),
                color = ElectricCyan,
                modifier =
                  Modifier.clickable {
                      focusManager.clearFocus()
                      val target = onOpenUrl(uiState.urlInput)
                      webViewRef?.loadUrl(target)
                    }
                    .testTag("open_url_button"),
              ) {
                Text(
                  text = stringResource(R.string.action_open),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF04222B),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
              }
            }

            // Bouton Test & Priorité avec voyant d'état
            Box(contentAlignment = Alignment.TopEnd) {
              IconButton(
                onClick = onOpenTestSheet,
                modifier =
                  Modifier.size(40.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("test_priority_button"),
              ) {
                Icon(
                  painter = painterResource(R.drawable.ic_tune),
                  contentDescription = stringResource(R.string.action_test_panel),
                  tint = ElectricCyan,
                  modifier = Modifier.size(20.dp),
                )
              }
              Box(
                modifier =
                  Modifier.padding(top = 7.dp, end = 7.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (allHealthy) EmeraldReady else AmberWarn)
              )
            }

            // Masquer la barre
            IconButton(
              onClick = { onToggleTopBar(false) },
              modifier =
                Modifier.size(40.dp).minimumInteractiveComponentSize().testTag("hide_bar_button"),
            ) {
              Icon(
                painter = painterResource(R.drawable.ic_visibility_off),
                contentDescription = stringResource(R.string.action_hide_bar),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          // Bandeau Optimisation Batterie si encore restreinte
          AnimatedVisibility(visible = !uiState.isBatteryIgnored) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .background(AmberWarn.copy(alpha = 0.14f))
                  .padding(horizontal = 12.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  painter = painterResource(R.drawable.ic_battery_alert),
                  contentDescription = null,
                  tint = AmberWarn,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = stringResource(R.string.battery_banner_restricted),
                  style = MaterialTheme.typography.labelSmall,
                  color = AmberWarn,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              TextButton(
                onClick = onRequestBatteryExclusion,
                modifier = Modifier.testTag("banner_battery_exclude_button"),
              ) {
                Text(
                  text = stringResource(R.string.battery_banner_action),
                  color = ElectricCyan,
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
          }
        }
      }

      // Barre de progression fine
      if (uiState.isLoading) {
        LinearProgressIndicator(
          progress = { (uiState.loadProgress / 100f).coerceIn(0.05f, 1f) },
          color = ElectricCyan,
          trackColor = ObsidianSurface,
          modifier = Modifier.fillMaxWidth().height(2.dp),
        )
      }

      // WebView plein écran
      AndroidView(
        modifier =
          Modifier.fillMaxWidth()
            .weight(1f)
            .let { if (!uiState.isTopBarVisible) it.statusBarsPadding() else it }
            .testTag("cyber_webview"),
        factory = { ctx ->
          WebView(ctx).apply {
            configureCyberWebView(
              webView = this,
              jsBridge = jsBridge,
              onNavigationStateChanged = onNavigationStateChanged,
              onProgressChanged = onProgressChanged,
              onLaunchFileChooser = onLaunchFileChooser,
            )
            loadUrl(uiState.currentUrl)
            webViewRef = this
            onWebViewCreated(this)
          }
        },
        update = { webView ->
          webViewRef = webView
          // Garantit que les timers JS restent actifs
          webView.resumeTimers()
        },
      )
    }

    // Bouton flottant discret pour réafficher la barre DNS / Test quand elle est masquée
    if (!uiState.isTopBarVisible) {
      Surface(
        shape = RoundedCornerShape(50),
        color = ObsidianSurface.copy(alpha = 0.88f),
        shadowElevation = 6.dp,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 8.dp, end = 10.dp)
            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable { onToggleTopBar(true) }
            .minimumInteractiveComponentSize()
            .testTag("show_bar_pill"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier =
              Modifier.size(8.dp)
                .clip(CircleShape)
                .background(
                  when {
                    countdownSeconds != null -> ElectricCyan
                    allHealthy -> EmeraldReady
                    else -> RoseAlert
                  }
                )
          )
          Icon(
            painter = painterResource(R.drawable.ic_visibility),
            contentDescription = stringResource(R.string.action_show_bar),
            tint = ElectricCyan,
            modifier = Modifier.size(16.dp),
          )
          if (countdownSeconds != null) {
            Text(
              text = "${countdownSeconds}s",
              style = MaterialTheme.typography.labelSmall,
              color = ElectricCyan,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }

    if (uiState.isTestSheetOpen) {
      TestPrioritySheet(
        uiState = uiState,
        serviceRunning = serviceRunning,
        countdownSeconds = countdownSeconds,
        ttsState = ttsState,
        availableFrenchVoices = availableFrenchVoices,
        selectedVoiceId = selectedVoiceId,
        audioFocusMode = audioFocusMode,
        lastAlertText = lastAlertText,
        lastBridgeEvent = lastBridgeEvent,
        onDismiss = onDismissTestSheet,
        onToggleService = onToggleService,
        onSelectAudioFocusMode = onSelectAudioFocusMode,
        onSelectVoice = onSelectVoice,
        onRequestBatteryExclusion = onRequestBatteryExclusion,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onInstallFrenchTts = onInstallFrenchTts,
        onOpenTtsSettings = onOpenTtsSettings,
        onStartCountdownTest = onStartCountdownTest,
        onImmediateVoiceTest = onImmediateVoiceTest,
        onOpenExternalChrome = {
          BatteryOptimizationHelper.openExternalBrowser(context, uiState.currentUrl)
        },
      )
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureCyberWebView(
  webView: WebView,
  jsBridge: CyberJsBridge,
  onNavigationStateChanged: (String?, String?, Boolean, Boolean) -> Unit,
  onProgressChanged: (Int) -> Unit,
  onLaunchFileChooser: (Intent, ValueCallback<Array<Uri>>) -> Unit,
) {
  webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    @Suppress("DEPRECATION")
    databaseEnabled = true
    mediaPlaybackRequiresUserGesture = false
    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    cacheMode = WebSettings.LOAD_DEFAULT
    useWideViewPort = true
    loadWithOverviewMode = true
    setSupportMultipleWindows(true)
    javaScriptCanOpenWindowsAutomatically = true
  }

  CookieManager.getInstance().apply {
    setAcceptCookie(true)
    setAcceptThirdPartyCookies(webView, true)
  }

  webView.addJavascriptInterface(jsBridge, CyberJsBridge.JS_INTERFACE_NAME)

  webView.webViewClient =
    object : WebViewClient() {
      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onNavigationStateChanged(url, view?.title, view?.canGoBack() == true, view?.canGoForward() == true)
      }

      override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        view?.evaluateJavascript(CyberJsBridge.JS_SPEECH_INTERCEPT_SCRIPT, null)
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(CyberJsBridge.JS_SPEECH_INTERCEPT_SCRIPT, null)
        view?.resumeTimers()
        onNavigationStateChanged(url, view?.title, view?.canGoBack() == true, view?.canGoForward() == true)
      }

      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") {
          return false
        }
        // Gère intent://, mailto:, tel:, etc. sans planter la WebView.
        runCatching {
          val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          view?.context?.startActivity(intent)
        }
        return true
      }
    }

  webView.webChromeClient =
    object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
      }

      override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        onNavigationStateChanged(view?.url, title, view?.canGoBack() == true, view?.canGoForward() == true)
      }

      // Compatibilité OAuth Google / Firebase Auth popups (signInWithPopup / target="_blank")
      override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
      ): Boolean {
        val context = view?.context ?: return false
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false

        val popupWebView = WebView(context)
        popupWebView.settings.javaScriptEnabled = true
        popupWebView.webViewClient =
          object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
              popup: WebView?,
              request: WebResourceRequest?,
            ): Boolean {
              val url = request?.url?.toString() ?: return false
              handlePopupUrl(context, view, url)
              popup?.destroy()
              return true
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(popup: WebView?, url: String?): Boolean {
              if (url != null) {
                handlePopupUrl(context, view, url)
              }
              popup?.destroy()
              return true
            }
          }
        transport.webView = popupWebView
        resultMsg.sendToTarget()
        return true
      }

      override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
      ): Boolean {
        if (filePathCallback == null || fileChooserParams == null) return false
        val intent = runCatching { fileChooserParams.createIntent() }.getOrNull() ?: return false
        onLaunchFileChooser(intent, filePathCallback)
        return true
      }
    }
}

private fun handlePopupUrl(context: android.content.Context, mainWebView: WebView, url: String) {
  val lower = url.lowercase()
  if (
    lower.contains("accounts.google.com") ||
      lower.contains("oauth") ||
      lower.contains("appleid.apple.com")
  ) {
    // Google bloque les WebViews intégrées pour OAuth : ouverture via Chrome Custom Tabs.
    BatteryOptimizationHelper.openExternalBrowser(context, url)
  } else {
    mainWebView.loadUrl(url)
  }
}
