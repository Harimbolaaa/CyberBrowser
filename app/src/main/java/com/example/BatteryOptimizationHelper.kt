package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

  fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
  }

  @SuppressLint("BatteryLife")
  fun openBatteryOptimizationRequest(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val directIntent =
      Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = packageUri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    try {
      context.startActivity(directIntent)
    } catch (_: Exception) {
      val fallbackIntent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      runCatching { context.startActivity(fallbackIntent) }
    }
  }

  fun openExternalBrowser(context: Context, url: String) {
    val safeUrl = BrowserPreferences.normalizeUrl(url)
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
        // Active Custom Tabs si Chrome ou un navigateur compatible est présent (sans dépendance lourde).
        putExtra("android.support.customtabs.extra.SESSION", Bundle.EMPTY)
        putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF0B1120.toInt())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { context.startActivity(intent) }
  }
}
