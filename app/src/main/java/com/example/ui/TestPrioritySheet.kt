package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.AudioFocusMode
import com.example.BrowserUiState
import com.example.R
import com.example.TtsEngineState
import com.example.TtsVoiceInfo
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.RoseAlert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestPrioritySheet(
  uiState: BrowserUiState,
  serviceRunning: Boolean,
  countdownSeconds: Int?,
  ttsState: TtsEngineState,
  availableFrenchVoices: List<TtsVoiceInfo>,
  selectedVoiceId: String?,
  audioFocusMode: AudioFocusMode,
  lastAlertText: String,
  lastBridgeEvent: String,
  onDismiss: () -> Unit,
  onToggleService: (Boolean) -> Unit,
  onSelectAudioFocusMode: (AudioFocusMode) -> Unit,
  onSelectVoice: (String) -> Unit,
  onRequestBatteryExclusion: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onInstallFrenchTts: () -> Unit,
  onOpenTtsSettings: () -> Unit,
  onStartCountdownTest: () -> Unit,
  onImmediateVoiceTest: () -> Unit,
  onOpenExternalChrome: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val offlineCount = availableFrenchVoices.count { it.isOffline }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = ObsidianSurface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.testTag("test_priority_sheet"),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Test & Priorité Arrière-Plan",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Compatible toute app audio/vidéo (TikTok, YouTube, Spotify, Reels, Jeux…)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          painter = painterResource(R.drawable.ic_shield_pulse),
          contentDescription = null,
          tint = ElectricCyan,
          modifier = Modifier.size(28.dp),
        )
      }

      // Bouton principal : Test Audio Ducking dans 5s (Toutes apps)
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurfaceHigh,
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_volume_up),
              contentDescription = null,
              tint = ElectricCyan,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = "Validation Audio Ducking + Voix FR",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }

          Text(
            text =
              if (countdownSeconds != null) {
                "Compte à rebours : ${countdownSeconds}s — Basculez sur TikTok, YouTube, Spotify ou une autre app maintenant !"
              } else {
                "Compte à rebours de 5s pour ouvrir n'importe quelle app (TikTok, YouTube, Spotify, Facebook, jeu…) et vérifier la baisse automatique du volume puis sa reprise."
              },
            style = MaterialTheme.typography.labelSmall,
            color =
              if (countdownSeconds != null) ElectricCyan
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Button(
            onClick = onStartCountdownTest,
            enabled = countdownSeconds == null,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = Color(0xFF04222B),
              ),
            modifier = Modifier.fillMaxWidth().testTag("duck_countdown_test_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text =
                if (countdownSeconds != null) {
                  "Annonce dans ${countdownSeconds}s..."
                } else {
                  "Tester Ducking + Voix dans 5s (Toutes apps)"
                },
              fontWeight = FontWeight.Bold,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onImmediateVoiceTest,
              modifier = Modifier.weight(1f).testTag("instant_voice_test_button"),
            ) {
              Text("Test immédiat", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
              onClick = onOpenExternalChrome,
              modifier = Modifier.weight(1f).testTag("external_login_button"),
            ) {
              Icon(
                painter = painterResource(R.drawable.ic_open_in_new),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Connexion externe", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }

      // Mode AudioFocus (Toutes apps : Ducking vs Pause auto)
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurfaceHigh,
        modifier =
          Modifier.fillMaxWidth().border(1.dp, ObsidianOutline, RoundedCornerShape(12.dp)),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Comportement sur les autres applications",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ModeChoiceChip(
              title = "Baisser le son",
              subtitle = "TikTok, YouTube, Spotify…",
              selected = audioFocusMode == AudioFocusMode.DUCK,
              onClick = { onSelectAudioFocusMode(AudioFocusMode.DUCK) },
              modifier = Modifier.weight(1f).testTag("focus_mode_duck"),
            )
            ModeChoiceChip(
              title = "Pause temporaire",
              subtitle = "Jeux & apps strictes",
              selected = audioFocusMode == AudioFocusMode.PAUSE_RESUME,
              onClick = { onSelectAudioFocusMode(AudioFocusMode.PAUSE_RESUME) },
              modifier = Modifier.weight(1f).testTag("focus_mode_pause"),
            )
          }
        }
      }

      // Modèles de voix françaises (Android hors-ligne & système, injectés dans la WebView)
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurfaceHigh,
        modifier =
          Modifier.fillMaxWidth().border(1.dp, ObsidianOutline, RoundedCornerShape(12.dp)),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Voix françaises (WebView + Hors-ligne)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
              )
              val voiceSummary =
                when {
                  ttsState == TtsEngineState.INITIALIZING -> "Détection des voix Android…"
                  availableFrenchVoices.isNotEmpty() ->
                    "${availableFrenchVoices.size} modèle(s) FR prêt(s) ($offlineCount hors-ligne)"
                  else -> "Aucune voix FR détectée sur le téléphone"
                }
              Text(
                text = voiceSummary,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (availableFrenchVoices.isNotEmpty()) EmeraldReady
                  else if (ttsState == TtsEngineState.INITIALIZING) AmberWarn
                  else RoseAlert,
              )
            }
            TextButton(
              onClick = onInstallFrenchTts,
              modifier = Modifier.testTag("install_tts_button"),
            ) {
              Text("Packs hors-ligne", color = ElectricCyan, fontWeight = FontWeight.Bold)
            }
          }

          if (availableFrenchVoices.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              availableFrenchVoices.forEach { voice ->
                val isSelected = voice.id == selectedVoiceId
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color =
                    if (isSelected) ElectricCyan.copy(alpha = 0.16f) else ObsidianSurface,
                  modifier =
                    Modifier.border(
                        1.dp,
                        if (isSelected) ElectricCyan else ObsidianOutline,
                        RoundedCornerShape(10.dp),
                      )
                      .clickable { onSelectVoice(voice.id) }
                      .testTag("voice_chip_${voice.langTag}"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    if (isSelected) {
                      Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp),
                      )
                    }
                    Text(
                      text = voice.displayName,
                      style = MaterialTheme.typography.labelSmall,
                      color =
                        if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            }
          } else {
            OutlinedButton(
              onClick = onOpenTtsSettings,
              modifier = Modifier.fillMaxWidth().testTag("open_tts_settings_button"),
            ) {
              Text(
                "Ouvrir paramètres Synthèse vocale Android",
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
        }
      }

      // 1. Service arrière-plan
      StatusCardRow(
        title = "Service arrière-plan (KeepAlive)",
        statusLabel = if (serviceRunning) "Actif (START_STICKY)" else "Inactif",
        statusColor = if (serviceRunning) EmeraldReady else AmberWarn,
        trailing = {
          Switch(
            checked = serviceRunning,
            onCheckedChange = onToggleService,
            colors =
              SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF04222B),
                checkedTrackColor = EmeraldReady,
              ),
            modifier = Modifier.testTag("service_toggle_switch"),
          )
        },
      )

      // 2. Optimisation batterie
      StatusCardRow(
        title = "Optimisation batterie",
        statusLabel =
          if (uiState.isBatteryIgnored) "Exclue (Protection active)"
          else "Restreinte (Risque de coupure)",
        statusColor = if (uiState.isBatteryIgnored) EmeraldReady else AmberWarn,
        trailing = {
          if (!uiState.isBatteryIgnored) {
            TextButton(
              onClick = onRequestBatteryExclusion,
              modifier = Modifier.testTag("sheet_battery_exclude_button"),
            ) {
              Text("Exclure", color = ElectricCyan, fontWeight = FontWeight.Bold)
            }
          }
        },
      )

      // 3. Notifications Android 13+
      if (!uiState.isNotificationGranted) {
        StatusCardRow(
          title = "Notifications alertes (Android 13+)",
          statusLabel = "Permission requise",
          statusColor = AmberWarn,
          trailing = {
            TextButton(
              onClick = onRequestNotificationPermission,
              modifier = Modifier.testTag("grant_notifications_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Autoriser", color = ElectricCyan)
            }
          },
        )
      }

      HorizontalDivider(color = ObsidianOutline)

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Pont JS : $lastBridgeEvent",
          style = MaterialTheme.typography.labelMedium,
          color = ElectricCyan,
        )
        Text(
          text = "Dernière alerte : $lastAlertText",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ModeChoiceChip(
  title: String,
  subtitle: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (selected) ElectricCyan.copy(alpha = 0.14f) else ObsidianSurface,
    modifier =
      modifier
        .border(
          1.dp,
          if (selected) ElectricCyan else ObsidianOutline,
          RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun StatusCardRow(
  title: String,
  statusLabel: String,
  statusColor: Color,
  trailing: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = ObsidianSurfaceHigh,
    modifier =
      Modifier.fillMaxWidth().border(1.dp, ObsidianOutline, RoundedCornerShape(12.dp)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
          )
        }
      }
      trailing()
    }
  }
}
