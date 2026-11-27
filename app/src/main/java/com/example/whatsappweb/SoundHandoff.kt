package com.example.whatsappweb

import android.content.Context
import java.io.File

/** Sticker armado en el editor que espera a ser abierto en la pantalla de WhatsApp Web. */
object SoundHandoff {
  /** Muy por encima de lo que pesa un video de 10 segundos a 512 px. */
  const val MAX_BYTES = 20 * 1024 * 1024

  private val MIME_TYPES = setOf("video/mp4", "video/webm")
  private val SHAPES = setOf("rounded", "circle", "square")

  fun file(context: Context): File = File(File(context.cacheDir, "sound_handoff"), "sticker")

  /** Devuelve el tipo de video sin los códecs, o null si no es uno admitido. */
  fun videoType(mimeType: String): String? =
    mimeType.substringBefore(';').trim().lowercase().takeIf { it in MIME_TYPES }

  /** Forma del sticker elegida en el editor; "square" si no se reconoce. */
  fun shape(value: String?): String = value?.takeIf { it in SHAPES } ?: "square"

  /** Instrucción que entrega el video a la página; ningún valor admitido necesita escaparse. */
  fun deliveryScript(base64: String, videoType: String, shape: String): String {
    require(videoType in MIME_TYPES && shape in SHAPES)
    require(base64.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' })
    return "!!(window.__waasMovil && window.__waasMovil.receive('$base64', '$videoType', '$shape'))"
  }
}
