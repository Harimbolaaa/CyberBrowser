package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CyberBrowser", appName)
  }

  @Test
  fun `normalize url prepends https when scheme missing`() {
    assertEquals("https://cyber-cyber.web.app", BrowserPreferences.normalizeUrl("cyber-cyber.web.app"))
    assertEquals("http://192.168.1.5:3000", BrowserPreferences.normalizeUrl("http://192.168.1.5:3000"))
    assertEquals(BrowserPreferences.DEFAULT_URL, BrowserPreferences.normalizeUrl("   "))
  }
}
