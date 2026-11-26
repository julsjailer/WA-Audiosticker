package com.example.whatsappweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppWebTest {

  @Test
  fun `only the exact WhatsApp Web origin is allowed`() {
    assertTrue(WhatsAppWeb.isWhatsAppWeb("https://web.whatsapp.com/"))
    listOf(
        null,
        "http://web.whatsapp.com/",
        "https://web.whatsapp.com.example.org/",
        "https://example.org/?u=https://web.whatsapp.com/",
        "https://user@web.whatsapp.com/",
        "intent://web.whatsapp.com/",
      )
      .forEach { assertFalse(it, WhatsAppWeb.isWhatsAppWeb(it)) }
  }

  @Test
  fun `builds a desktop user agent from the installed version`() {
    val major =
      WhatsAppWeb.chromeMajor("Mozilla/5.0 (Linux; Android 11) Chrome/141.0.7390.1 Mobile")
    assertEquals("141", major)
    val agent = WhatsAppWeb.desktopUserAgent("141")
    assertTrue(agent.contains("Chrome/141.0.0.0"))
    assertFalse(agent.contains("Android"))
  }

  @Test
  fun `injects every script once and only on WhatsApp Web`() {
    val script = WhatsAppWeb.injectionScript(listOf("uno()", "dos()"))
    assertTrue(script.startsWith("if (location.origin === 'https://web.whatsapp.com'"))
    assertTrue(script.indexOf("uno()") < script.indexOf("dos()"))
  }
}
