package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class TtsEngineState {
  INITIALIZING,
  READY,
  MISSING_FRENCH,
  ERROR,
}

data class TtsVoiceInfo(
  val id: String,
  val displayName: String,
  val langTag: String,
  val isOffline: Boolean,
  val isDefault: Boolean = false,
)

private data class DuckSpeechRequest(
  val message: String,
  val voiceId: String?,
  val rate: Float,
  val pitch: Float,
  val repeatCount: Int,
  val playChime: Boolean,
  val title: String,
)

class AudioDuckTtsManager private constructor(private val appContext: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val prefs = BrowserPreferences(appContext)
  private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val notificationManager =
    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val alertAudioAttributes: AudioAttributes =
    AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build()

  private val requestQueue = Channel<DuckSpeechRequest>(Channel.UNLIMITED)
  private val notificationIdCounter = AtomicInteger(2000)

  private val _ttsState = MutableStateFlow(TtsEngineState.INITIALIZING)
  val ttsState: StateFlow<TtsEngineState> = _ttsState.asStateFlow()

  private val _availableFrenchVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
  val availableFrenchVoices: StateFlow<List<TtsVoiceInfo>> = _availableFrenchVoices.asStateFlow()

  private val _selectedVoiceId = MutableStateFlow<String?>(prefs.preferredVoiceId)
  val selectedVoiceId: StateFlow<String?> = _selectedVoiceId.asStateFlow()

  private val _audioFocusMode = MutableStateFlow(prefs.audioFocusMode)
  val audioFocusMode: StateFlow<AudioFocusMode> = _audioFocusMode.asStateFlow()

  private val _lastAlertText = MutableStateFlow("Aucune alerte émise")
  val lastAlertText: StateFlow<String> = _lastAlertText.asStateFlow()

  private var textToSpeech: TextToSpeech? = null
  private val ttsInitDeferred = CompletableDeferred<Boolean>()
  private val pendingUtterances = mutableMapOf<String, CompletableDeferred<Unit>>()
  private val installedVoiceObjects = mutableMapOf<String, Voice>()

  private var activeFocusRequest: AudioFocusRequest? = null
  private var hasAudioFocus = false

  private val focusChangeListener =
    AudioManager.OnAudioFocusChangeListener {
      // Le focus transitoire est libéré proprement dès la fin de l'annonce vocale + chime.
    }

  init {
    ensureAlertChannel()
    initTts()
    startQueueProcessor()
  }

  private fun initTts() {
    textToSpeech =
      TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          val tts = textToSpeech
          if (tts == null) {
            _ttsState.value = TtsEngineState.ERROR
            ttsInitDeferred.complete(false)
            return@TextToSpeech
          }
          tts.setAudioAttributes(alertAudioAttributes)
          val langResult = chooseBestFrenchLocale(tts)
          refreshFrenchVoices(tts, langResult)

          when (langResult) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
              _ttsState.value = TtsEngineState.READY
              ttsInitDeferred.complete(true)
            }
            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED -> {
              _ttsState.value = TtsEngineState.MISSING_FRENCH
              // Complète à true si des voix de secours existent pour ne jamais bloquer le chime/alerte
              ttsInitDeferred.complete(_availableFrenchVoices.value.isNotEmpty())
            }
            else -> {
              _ttsState.value = TtsEngineState.READY
              ttsInitDeferred.complete(true)
            }
          }

          tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) {}

              override fun onDone(utteranceId: String?) {
                if (utteranceId != null) {
                  scope.launch { pendingUtterances.remove(utteranceId)?.complete(Unit) }
                }
              }

              @Deprecated("Deprecated in API 21")
              override fun onError(utteranceId: String?) {
                if (utteranceId != null) {
                  scope.launch { pendingUtterances.remove(utteranceId)?.complete(Unit) }
                }
              }

              override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != null) {
                  scope.launch { pendingUtterances.remove(utteranceId)?.complete(Unit) }
                }
              }
            }
          )
        } else {
          _ttsState.value = TtsEngineState.ERROR
          ttsInitDeferred.complete(false)
        }
      }
  }

  fun reloadVoices() {
    val tts = textToSpeech ?: return
    val langResult = chooseBestFrenchLocale(tts)
    refreshFrenchVoices(tts, langResult)
  }

  private fun refreshFrenchVoices(tts: TextToSpeech, langResult: Int) {
    installedVoiceObjects.clear()
    val rawVoices = runCatching { tts.voices }.getOrNull().orEmpty()

    val frenchVoices =
      rawVoices
        .filter { voice ->
          val isFrench = voice.locale.language.equals("fr", ignoreCase = true)
          val notMissing =
            !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
          isFrench && notMissing
        }
        .sortedWith(
          compareBy<Voice>(
            { it.isNetworkConnectionRequired }, // Offline d'abord (false < true)
            { !it.locale.country.equals("FR", ignoreCase = true) }, // fr-FR en priorité
            { -it.quality },
          )
        )

    val mapped =
      frenchVoices.mapIndexed { index, voice ->
        installedVoiceObjects[voice.name] = voice
        val tag = voice.locale.toLanguageTag().ifBlank { "fr-FR" }
        val modeTag = if (voice.isNetworkConnectionRequired) "En ligne" else "Hors-ligne"
        val regionLabel =
          when (voice.locale.country.uppercase(Locale.ROOT)) {
            "FR" -> "France"
            "CA" -> "Canada"
            "BE" -> "Belgique"
            "CH" -> "Suisse"
            else -> "FR"
          }
        TtsVoiceInfo(
          id = voice.name,
          displayName = "Voix Android #$index ($regionLabel • $modeTag)".replace("#0", "#1").let {
            "Voix FR ${index + 1} ($regionLabel • $modeTag)"
          },
          langTag = tag,
          isOffline = !voice.isNetworkConnectionRequired,
          isDefault = index == 0,
        )
      }.toMutableList()

    val isFrenchSupported =
      langResult == TextToSpeech.LANG_AVAILABLE ||
        langResult == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
        langResult == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE

    if (mapped.isEmpty() && isFrenchSupported) {
      mapped.add(
        TtsVoiceInfo(
          id = DEFAULT_FR_VOICE_ID,
          displayName = "Voix Android Français (Hors-ligne par défaut)",
          langTag = "fr-FR",
          isOffline = true,
          isDefault = true,
        )
      )
    }

    _availableFrenchVoices.value = mapped

    val savedId = prefs.preferredVoiceId
    val activeVoiceId =
      when {
        savedId != null && mapped.any { it.id == savedId } -> savedId
        mapped.isNotEmpty() -> mapped.first().id
        else -> null
      }
    _selectedVoiceId.value = activeVoiceId
    if (activeVoiceId != null) {
      installedVoiceObjects[activeVoiceId]?.let { runCatching { tts.voice = it } }
    }
  }

  fun selectVoice(voiceId: String) {
    prefs.preferredVoiceId = voiceId
    _selectedVoiceId.value = voiceId
    val tts = textToSpeech ?: return
    installedVoiceObjects[voiceId]?.let { runCatching { tts.voice = it } }
  }

  fun setAudioFocusMode(mode: AudioFocusMode) {
    prefs.audioFocusMode = mode
    _audioFocusMode.value = mode
  }

  private fun chooseBestFrenchLocale(tts: TextToSpeech): Int {
    val franceResult = tts.setLanguage(Locale.FRANCE)
    if (
      franceResult != TextToSpeech.LANG_MISSING_DATA &&
        franceResult != TextToSpeech.LANG_NOT_SUPPORTED
    ) {
      return franceResult
    }
    return tts.setLanguage(Locale.FRENCH)
  }

  private fun startQueueProcessor() {
    scope.launch {
      for (request in requestQueue) {
        processSingleAlert(request)
        if (requestQueue.isEmpty) {
          abandonDuckFocus()
        }
      }
    }
  }

  fun speakAndDuck(
    message: String,
    repeatCount: Int = 1,
    playChime: Boolean = true,
    title: String = "Alerte vocale CyberBrowser",
    voiceId: String? = null,
    rate: Float = 1.0f,
    pitch: Float = 1.0f,
  ) {
    val cleanMessage = message.trim()
    if (cleanMessage.isEmpty() && !playChime) return

    val safeRepeat = repeatCount.coerceIn(1, 5)
    _lastAlertText.value = cleanMessage.ifEmpty { "Signal sonore" }
    requestQueue.trySend(
      DuckSpeechRequest(
        message = cleanMessage,
        voiceId = voiceId,
        rate = rate.coerceIn(0.5f, 2.0f),
        pitch = pitch.coerceIn(0.5f, 2.0f),
        repeatCount = safeRepeat,
        playChime = playChime,
        title = title,
      )
    )
  }

  fun stopSpeaking() {
    runCatching { textToSpeech?.stop() }
    pendingUtterances.values.forEach { it.complete(Unit) }
    pendingUtterances.clear()
    abandonDuckFocus()
  }

  fun openInstallFrenchVoiceSettings(context: Context) {
    val installIntent =
      Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    try {
      context.startActivity(installIntent)
    } catch (_: Exception) {
      openTtsSystemSettings(context)
    }
  }

  fun openTtsSystemSettings(context: Context) {
    val settingsIntent =
      Intent("com.android.settings.TTS_SETTINGS").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { context.startActivity(settingsIntent) }
  }

  fun showHighPriorityNotification(title: String, body: String) {
    ensureAlertChannel()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) return
    }

    val openIntent =
      Intent(appContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingFlags =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    val contentPendingIntent = PendingIntent.getActivity(appContext, 101, openIntent, pendingFlags)

    val notification =
      NotificationCompat.Builder(appContext, ALERT_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_shield_pulse)
        .setContentTitle(title.ifBlank { "CyberBrowser Alerte" })
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setSilent(true)
        .setContentIntent(contentPendingIntent)
        .build()

    runCatching {
      notificationManager.notify(notificationIdCounter.incrementAndGet(), notification)
    }
  }

  private suspend fun processSingleAlert(request: DuckSpeechRequest) {
    showHighPriorityNotification(request.title, request.message.ifEmpty { "Signal sonore" })
    requestDuckFocus()

    val safetyTimeoutMs = (request.repeatCount * 10_000L).coerceAtLeast(12_000L)
    withTimeoutOrNull(safetyTimeoutMs) {
      if (request.message.isNotEmpty()) {
        val ttsReady = withTimeoutOrNull(2_500L) { ttsInitDeferred.await() } ?: false
        if (ttsReady) {
          applySpeechParameters(request)
          for (i in 0 until request.repeatCount) {
            speakSingleUtterance(request.message)
          }
        }
      }
      if (request.playChime) {
        playChimeSound()
      }
    }
  }

  private fun applySpeechParameters(request: DuckSpeechRequest) {
    val tts = textToSpeech ?: return
    val targetVoiceId = request.voiceId?.takeIf { it.isNotBlank() } ?: _selectedVoiceId.value
    val matchedVoice = targetVoiceId?.let { installedVoiceObjects[it] }
    if (matchedVoice != null) {
      runCatching { tts.voice = matchedVoice }
    } else {
      chooseBestFrenchLocale(tts)
    }
    tts.setSpeechRate(request.rate)
    tts.setPitch(request.pitch)
  }

  private suspend fun speakSingleUtterance(text: String) {
    val tts = textToSpeech ?: return
    val utteranceId = "cyber_utt_${UUID.randomUUID()}"
    val deferred = CompletableDeferred<Unit>()
    pendingUtterances[utteranceId] = deferred

    val params =
      Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
      }
    val result = tts.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
    if (result != TextToSpeech.SUCCESS) {
      pendingUtterances.remove(utteranceId)
      deferred.complete(Unit)
      return
    }
    withTimeoutOrNull(10_000L) { deferred.await() }
    pendingUtterances.remove(utteranceId)
  }

  private suspend fun playChimeSound() {
    val deferred = CompletableDeferred<Unit>()
    var mediaPlayer: MediaPlayer? = null
    try {
      val afd = appContext.resources.openRawResourceFd(R.raw.tohizana)
      mediaPlayer =
        MediaPlayer().apply {
          setAudioAttributes(alertAudioAttributes)
          setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          afd.close()
          setOnCompletionListener { deferred.complete(Unit) }
          setOnErrorListener { _, _, _ ->
            deferred.complete(Unit)
            true
          }
          prepare()
          start()
        }
      withTimeoutOrNull(5_000L) { deferred.await() }
    } catch (_: Exception) {
      deferred.complete(Unit)
    } finally {
      runCatching {
        mediaPlayer?.stop()
        mediaPlayer?.release()
      }
    }
  }

  private fun requestDuckFocus() {
    if (hasAudioFocus) return
    val focusGainType =
      when (_audioFocusMode.value) {
        AudioFocusMode.DUCK -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        AudioFocusMode.PAUSE_RESUME -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
      }

    val result =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val focusRequest =
          AudioFocusRequest.Builder(focusGainType)
            .setAudioAttributes(alertAudioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(_audioFocusMode.value == AudioFocusMode.PAUSE_RESUME)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
        activeFocusRequest = focusRequest
        audioManager.requestAudioFocus(focusRequest)
      } else {
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
          focusChangeListener,
          AudioManager.STREAM_MUSIC,
          focusGainType,
        )
      }
    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  private fun abandonDuckFocus() {
    if (!hasAudioFocus) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      activeFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
      activeFocusRequest = null
    } else {
      @Suppress("DEPRECATION")
      audioManager.abandonAudioFocus(focusChangeListener)
    }
    hasAudioFocus = false
  }

  private fun ensureAlertChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val existing = notificationManager.getNotificationChannel(ALERT_CHANNEL_ID)
      if (existing == null) {
        val channel =
          NotificationChannel(
              ALERT_CHANNEL_ID,
              appContext.getString(R.string.notif_channel_alerts_name),
              NotificationManager.IMPORTANCE_HIGH,
            )
            .apply {
              description = appContext.getString(R.string.notif_channel_alerts_desc)
              enableVibration(true)
              setSound(null, null)
            }
        notificationManager.createNotificationChannel(channel)
      }
    }
  }

  companion object {
    private const val ALERT_CHANNEL_ID = "cyber_alerts_channel"
    private const val DEFAULT_FR_VOICE_ID = "cyber_default_fr_FR"

    @Volatile private var instance: AudioDuckTtsManager? = null

    fun getInstance(context: Context): AudioDuckTtsManager {
      return instance
        ?: synchronized(this) {
          instance ?: AudioDuckTtsManager(context.applicationContext).also { instance = it }
        }
    }
  }
}
