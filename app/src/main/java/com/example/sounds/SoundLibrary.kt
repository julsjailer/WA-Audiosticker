package com.example.sounds

import java.net.URI
import java.net.URLDecoder

/** Reglas de la pantalla que muestra MyInstants. */
object SoundLibrary {
  const val URL = "https://www.myinstants.com/es/index/us/"

  /** Un sonido corto pesa muy poco; el límite evita cargar archivos enormes en el editor. */
  const val MAX_BYTES = 5 * 1024 * 1024

  private val HOSTS = setOf("www.myinstants.com", "myinstants.com")
  private const val SOUNDS_PATH = "/media/sounds/"
  private val EXTENSIONS = listOf(".mp3", ".wav", ".ogg", ".m4a")

  private fun parse(url: String?): URI? {
    val uri = runCatching { URI(url.orEmpty()) }.getOrNull() ?: return null
    return uri.takeIf { it.scheme == "https" && it.host in HOSTS && it.userInfo == null }
  }

  fun isLibraryPage(url: String?): Boolean = parse(url) != null

  /** Nombre del archivo si la dirección es un audio de la biblioteca; si no, null. */
  fun soundName(url: String?): String? {
    val path = parse(url)?.rawPath ?: return null
    if (!path.startsWith(SOUNDS_PATH)) return null
    val encoded = path.removePrefix(SOUNDS_PATH)
    val name =
      runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8") }.getOrNull()
        ?: return null
    return name.takeIf { n -> '/' !in n && EXTENSIONS.any { n.lowercase().endsWith(it) } }
  }
}
