package com.example

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class CyberJsBridge(
  private val context: Context,
  private val onBatteryStatusChanged: () -> Unit = {},
) {

  private val appContext = context.applicationContext
  private val audioDuckTtsManager = AudioDuckTtsManager.getInstance(appContext)
  private val prefs = BrowserPreferences(appContext)

  private val _lastBridgeEvent = MutableStateFlow("Pont JS prêt (AndroidCyberBridge)")
  val lastBridgeEvent: StateFlow<String> = _lastBridgeEvent.asStateFlow()

  @JavascriptInterface
  fun speakAndDuck(message: String, repeatCount: Int, playChime: Boolean) {
    _lastBridgeEvent.value = "TTS + Ducking: ${message.take(40)}"
    audioDuckTtsManager.speakAndDuck(
      message = message,
      repeatCount = repeatCount,
      playChime = playChime,
    )
  }

  @JavascriptInterface
  fun speakAndDuckWithOptions(
    message: String,
    voiceId: String,
    rate: Float,
    pitch: Float,
    repeatCount: Int,
    playChime: Boolean,
  ) {
    _lastBridgeEvent.value = "TTS (${voiceId.take(18)}): ${message.take(30)}"
    audioDuckTtsManager.speakAndDuck(
      message = message,
      repeatCount = repeatCount,
      playChime = playChime,
      voiceId = voiceId.ifBlank { null },
      rate = rate,
      pitch = pitch,
    )
  }

  @JavascriptInterface
  fun getAvailableVoicesJson(): String {
    val voices = audioDuckTtsManager.availableFrenchVoices.value
    val array = JSONArray()
    if (voices.isEmpty()) {
      array.put(
        JSONObject().apply {
          put("voiceURI", "cyber_default_fr_FR")
          put("name", "Android Français (Hors-ligne)")
          put("lang", "fr-FR")
          put("localService", true)
          put("default", true)
        }
      )
    } else {
      voices.forEachIndexed { idx, voice ->
        array.put(
          JSONObject().apply {
            put("voiceURI", voice.id)
            put("name", voice.displayName)
            put("lang", voice.langTag)
            put("localService", voice.isOffline)
            put("default", idx == 0)
          }
        )
      }
    }
    return array.toString()
  }

  @JavascriptInterface
  fun setAudioFocusMode(mode: String) {
    val parsed =
      if (mode.equals("PAUSE", ignoreCase = true) || mode.equals("PAUSE_RESUME", ignoreCase = true)) {
        AudioFocusMode.PAUSE_RESUME
      } else {
        AudioFocusMode.DUCK
      }
    audioDuckTtsManager.setAudioFocusMode(parsed)
    _lastBridgeEvent.value = "Mode AudioFocus: ${parsed.name}"
  }

  @JavascriptInterface
  fun stopSpeaking() {
    _lastBridgeEvent.value = "Arrêt vocal demandé par la page"
    audioDuckTtsManager.stopSpeaking()
  }

  @JavascriptInterface
  fun showAlertNotification(title: String, body: String) {
    _lastBridgeEvent.value = "Notification: ${title.take(30)}"
    audioDuckTtsManager.showHighPriorityNotification(title, body)
  }

  @JavascriptInterface
  fun requestBatteryOptimization() {
    _lastBridgeEvent.value = "Demande d'exclusion batterie"
    BatteryOptimizationHelper.openBatteryOptimizationRequest(appContext)
    onBatteryStatusChanged()
  }

  @JavascriptInterface
  fun isBatteryOptimizationIgnored(): Boolean {
    return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(appContext)
  }

  @JavascriptInterface
  fun startBackgroundMonitor() {
    _lastBridgeEvent.value = "Surveillance arrière-plan activée"
    prefs.monitorEnabled = true
    KeepAliveService.startMonitor(appContext)
  }

  @JavascriptInterface
  fun stopBackgroundMonitor() {
    _lastBridgeEvent.value = "Surveillance arrière-plan arrêtée"
    prefs.monitorEnabled = false
    KeepAliveService.stopMonitor(appContext)
  }

  companion object {
    const val JS_INTERFACE_NAME = "AndroidCyberBridge"

    // Injecté dans onPageCommitVisible / onPageFinished :
    // 1. Remplit speechSynthesis.getVoices() avec les vraies voix Android FR (hors-ligne inclus)
    // 2. Redirige speechSynthesis.speak() vers AndroidCyberBridge.speakAndDuckWithOptions()
    val JS_SPEECH_INTERCEPT_SCRIPT: String =
      """
      (function() {
        var bridge = window.AndroidCyberBridge;
        if (!bridge) return;

        function loadAndroidVoices() {
          try {
            var raw = bridge.getAvailableVoicesJson();
            var list = JSON.parse(raw);
            return Array.isArray(list) ? list : [];
          } catch (e) {
            return [{ voiceURI: 'cyber_default_fr_FR', name: 'Android Français (Hors-ligne)', lang: 'fr-FR', localService: true, default: true }];
          }
        }

        window.__cyberRefreshVoices = function() {
          try {
            var synth = window.speechSynthesis;
            if (!synth) return;
            var event = new Event('voiceschanged');
            if (typeof synth.onvoiceschanged === 'function') {
              synth.onvoiceschanged(event);
            }
            if (typeof synth.dispatchEvent === 'function') {
              synth.dispatchEvent(event);
            }
          } catch (e) {}
        };

        if (window.__cyberSpeechBridgeInstalled) {
          window.__cyberRefreshVoices();
          return;
        }
        window.__cyberSpeechBridgeInstalled = true;

        if (typeof window.SpeechSynthesisUtterance !== 'function') {
          window.SpeechSynthesisUtterance = function(text) {
            this.text = text || '';
            this.lang = 'fr-FR';
            this.voice = null;
            this.rate = 1.0;
            this.pitch = 1.0;
            this.volume = 1.0;
            this.onstart = null;
            this.onend = null;
            this.onerror = null;
          };
        }

        var patchedSpeak = function(utterance) {
          try {
            var text = '';
            var voiceId = '';
            var rate = 1.0;
            var pitch = 1.0;

            if (typeof utterance === 'string') {
              text = utterance;
            } else if (utterance) {
              text = typeof utterance.text === 'string' ? utterance.text : '';
              if (utterance.voice) {
                voiceId = utterance.voice.voiceURI || utterance.voice.name || '';
              }
              if (typeof utterance.rate === 'number' && !isNaN(utterance.rate)) {
                rate = utterance.rate;
              }
              if (typeof utterance.pitch === 'number' && !isNaN(utterance.pitch)) {
                pitch = utterance.pitch;
              }
            }

            if (text && text.trim().length > 0) {
              bridge.speakAndDuckWithOptions(text, voiceId, rate, pitch, 1, true);
              if (utterance && typeof utterance.onstart === 'function') {
                setTimeout(function() { try { utterance.onstart(new Event('start')); } catch (e) {} }, 20);
              }
              if (utterance && typeof utterance.onend === 'function') {
                setTimeout(function() { try { utterance.onend(new Event('end')); } catch (e) {} }, 1400);
              }
              return;
            }
          } catch (err) {}
        };

        var patchedGetVoices = function() {
          return loadAndroidVoices();
        };

        try {
          if (window.speechSynthesis) {
            window.speechSynthesis.speak = patchedSpeak;
            window.speechSynthesis.getVoices = patchedGetVoices;
            window.speechSynthesis.cancel = function() {
              try { bridge.stopSpeaking(); } catch (e) {}
            };
          } else {
            Object.defineProperty(window, 'speechSynthesis', {
              value: {
                speak: patchedSpeak,
                cancel: function() { try { bridge.stopSpeaking(); } catch (e) {} },
                pause: function() {},
                resume: function() {},
                getVoices: patchedGetVoices,
                addEventListener: function() {},
                removeEventListener: function() {},
                dispatchEvent: function() { return true; },
                onvoiceschanged: null,
                speaking: false,
                pending: false,
                paused: false
              },
              configurable: true
            });
          }
          setTimeout(window.__cyberRefreshVoices, 150);
          setTimeout(window.__cyberRefreshVoices, 800);
        } catch (e) {}
      })();
      """.trimIndent()
  }
}
