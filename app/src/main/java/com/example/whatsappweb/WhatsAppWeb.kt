package com.example.whatsappweb

import java.net.URI

/** Reglas de la pantalla experimental de WhatsApp Web. */
object WhatsAppWeb {
  const val URL = "https://web.whatsapp.com/"
  private const val HOST = "web.whatsapp.com"

  /** Orden de carga: el código de la extensión y después la adaptación al teléfono. */
  val SCRIPT_ASSETS =
    listOf("logger", "convert", "wa", "ui", "main", "movil").map { "waas/$it.js" }

  fun isWhatsAppWeb(url: String?): Boolean {
    val uri = runCatching { URI(url.orEmpty()) }.getOrNull() ?: return false
    return uri.scheme == "https" && uri.host == HOST && uri.userInfo == null
  }

  /** WhatsApp Web solo funciona si el navegador se presenta como de escritorio. */
  fun desktopUserAgent(chromeMajor: String): String =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
      "Chrome/$chromeMajor.0.0.0 Safari/537.36"

  fun chromeMajor(userAgent: String): String? =
    Regex("Chrome/(\\d+)").find(userAgent)?.groupValues?.get(1)

  /** Une los scripts y evita ejecutarlos fuera de WhatsApp Web o dos veces. */
  fun injectionScript(sources: List<String>): String =
    "if (location.origin === 'https://$HOST' && !window.__waasInjected) {\n" +
      "window.__waasInjected = true;\n" +
      sources.joinToString("\n;\n") +
      "\n}"
}
