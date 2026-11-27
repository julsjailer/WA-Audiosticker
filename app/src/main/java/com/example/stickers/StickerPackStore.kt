package com.example.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda paquetes de stickers fijos o animados cumpliendo los límites de WhatsApp:
 * https://github.com/WhatsApp/stickers/tree/main/Android
 *
 * Los paquetes de WhatsApp no admiten audio: solo viajan las imágenes.
 */
object StickerPackStore {
  const val ROOT_DIR = "sticker_packs"
  const val METADATA_FILE = "metadata.json"
  const val TRAY_FILE = "tray.png"

  const val MIN_STICKERS = 3
  const val MAX_STICKERS = 30
  const val MAX_PACKS = 10
  const val STICKER_SIDE = 512
  const val TRAY_SIDE = 96
  const val MAX_STICKER_BYTES = 100 * 1024
  const val MAX_TRAY_BYTES = 50 * 1024
  const val MAX_ANIMATED_STICKER_BYTES = 500 * 1024
  const val MIN_FRAME_MS = 8
  const val MAX_ANIMATION_MS = 10_000
  const val ANIMATED_KEY = "animated_sticker_pack"
  private const val MAX_TEXT = 128

  private val QUALITIES = intArrayOf(95, 85, 75, 65, 55, 45, 35, 25, 15, 5)

  /** Datos de un WebP animado, leídos de su cabecera y de la de cada cuadro. */
  data class Animation(val width: Int, val height: Int, val frameDurationsMs: List<Int>)

  /**
   * El mismo nombre produce el mismo identificador, así reexportar actualiza el paquete. Los
   * animados llevan otro identificador porque WhatsApp no admite que un paquete cambie de tipo.
   */
  fun identifierFor(packName: String, animated: Boolean = false): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(packName.trim().toByteArray(Charsets.UTF_8))
    val suffix = if (animated) "_a" else ""
    return "pack_" + digest.take(8).joinToString("") { "%02x".format(it) } + suffix
  }

  /** Devuelve null si [bytes] no es un WebP animado. */
  fun readAnimation(bytes: ByteArray): Animation? {
    fun tag(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
    fun little(at: Int, count: Int) =
      (0 until count).fold(0L) { acc, i -> acc or ((bytes[at + i].toLong() and 0xff) shl (8 * i)) }

    val animated =
      bytes.size >= 30 && tag(0) == "RIFF" && tag(8) == "WEBP" && tag(12) == "VP8X" &&
        bytes[20].toInt() and 0x02 != 0
    if (!animated) return null

    val durations = mutableListOf<Int>()
    var at = 12L
    while (at + 8 <= bytes.size) {
      val start = at.toInt()
      val size = little(start + 4, 4)
      require(at + 8 + size <= bytes.size) { "Un sticker animado está dañado." }
      if (tag(start) == "ANMF") {
        require(size >= 16) { "Un sticker animado está dañado." }
        durations += little(start + 8 + 12, 3).toInt()
      }
      at += 8 + size + (size and 1L)
    }
    return Animation(little(24, 3).toInt() + 1, little(27, 3).toInt() + 1, durations)
  }

  /** Los animados se guardan tal cual (volver a codificarlos quitaría el movimiento). */
  fun checkAnimatedSticker(bytes: ByteArray, animation: Animation, number: Int): ByteArray {
    require(animation.width == STICKER_SIDE && animation.height == STICKER_SIDE) {
      "El sticker animado $number no mide 512×512."
    }
    require(animation.frameDurationsMs.size >= 2) { "El sticker animado $number no tiene movimiento." }
    require(animation.frameDurationsMs.all { it >= MIN_FRAME_MS }) {
      "El sticker animado $number tiene cuadros demasiado cortos."
    }
    require(animation.frameDurationsMs.sum() <= MAX_ANIMATION_MS) {
      "El sticker animado $number dura más de 10 segundos."
    }
    require(bytes.size <= MAX_ANIMATED_STICKER_BYTES) { "El sticker animado $number supera 500 KB." }
    return bytes
  }

  /** Devuelve la primera codificación que cabe en [maxBytes], bajando la calidad. */
  fun encodeWithinLimit(maxBytes: Int, encode: (quality: Int) -> ByteArray): ByteArray? {
    for (quality in QUALITIES) {
      val bytes = encode(quality)
      if (bytes.isNotEmpty() && bytes.size <= maxBytes) return bytes
    }
    return null
  }

  /**
   * Valida y escribe el paquete. [stickers] son imágenes en cualquier formato que Android pueda
   * decodificar. Lanza [IllegalArgumentException] con un mensaje apto para mostrar al usuario.
   */
  fun savePack(
    filesDir: File,
    packName: String,
    publisher: String,
    tray: ByteArray,
    stickers: List<ByteArray>,
    version: Long = System.currentTimeMillis(),
  ): String {
    require(stickers.size >= MIN_STICKERS) { "WhatsApp requiere mínimo $MIN_STICKERS stickers por paquete." }
    require(stickers.size <= MAX_STICKERS) { "WhatsApp admite máximo $MAX_STICKERS stickers por paquete." }

    val name = packName.trim().take(MAX_TEXT).ifBlank { "AudioStickers" }
    val author = publisher.trim().take(MAX_TEXT).ifBlank { "AudioStickers" }
    val animations = stickers.map { readAnimation(it) }
    val animated = animations.any { it != null }
    require(!animated || animations.all { it != null }) {
      "WhatsApp no permite mezclar stickers animados y fijos en un mismo paquete."
    }
    val identifier = identifierFor(name, animated)

    // Se codifica todo antes de tocar el disco para no dejar paquetes a medias.
    val trayBytes = toTrayPng(tray)
    val encoded =
      stickers.mapIndexed { index, bytes ->
        val animation = animations[index]
        if (animation != null) checkAnimatedSticker(bytes, animation, index + 1)
        else toStickerWebp(bytes, index + 1)
      }

    val root = File(filesDir, ROOT_DIR).apply { mkdirs() }
    val packDir = File(root, identifier)
    packDir.deleteRecursively()
    require(packDir.mkdirs()) { "No se pudo crear la carpeta del paquete." }

    File(packDir, TRAY_FILE).writeBytes(trayBytes)
    val list = JSONArray()
    encoded.forEachIndexed { index, bytes ->
      val fileName = "sticker_${index + 1}.webp"
      File(packDir, fileName).writeBytes(bytes)
      list.put(JSONObject().put("image_file", fileName).put("emojis", "🔊"))
    }
    val metadata =
      JSONObject()
        .put("identifier", identifier)
        .put("name", name)
        .put("publisher", author)
        .put("tray_image_file", TRAY_FILE)
        .put("image_data_version", version.toString())
        .put(ANIMATED_KEY, animated)
        .put("stickers", list)
    File(packDir, METADATA_FILE).writeText(metadata.toString(), Charsets.UTF_8)

    pruneOldPacks(root, keep = identifier)
    return identifier
  }

  /** Solo devuelve paquetes completos, ordenados del más reciente al más antiguo. */
  fun loadPacks(filesDir: File): List<JSONObject> {
    val dirs = File(filesDir, ROOT_DIR).listFiles { file -> file.isDirectory } ?: return emptyList()
    return dirs
      .mapNotNull { dir -> readValidPack(dir) }
      .sortedByDescending { it.optString("image_data_version").toLongOrNull() ?: 0L }
      .take(MAX_PACKS)
  }

  private fun readValidPack(dir: File): JSONObject? =
    try {
      val json = JSONObject(File(dir, METADATA_FILE).readText(Charsets.UTF_8))
      val stickers = json.getJSONArray("stickers")
      val limit = if (json.optBoolean(ANIMATED_KEY)) MAX_ANIMATED_STICKER_BYTES else MAX_STICKER_BYTES
      val complete =
        json.getString("identifier") == dir.name &&
          stickers.length() in MIN_STICKERS..MAX_STICKERS &&
          fits(File(dir, json.getString("tray_image_file")), MAX_TRAY_BYTES) &&
          (0 until stickers.length()).all {
            fits(File(dir, stickers.getJSONObject(it).getString("image_file")), limit)
          }
      if (complete) json else null
    } catch (_: Exception) {
      null
    }

  private fun fits(file: File, maxBytes: Int) = file.isFile && file.length() in 1..maxBytes.toLong()

  private fun pruneOldPacks(root: File, keep: String) {
    val valid = loadPacks(root.parentFile ?: return).map { it.getString("identifier") }.toSet()
    root.listFiles { file -> file.isDirectory }
      ?.filter { it.name != keep && it.name !in valid }
      ?.forEach { it.deleteRecursively() }
  }

  private fun scaled(bytes: ByteArray, side: Int, label: String): Bitmap {
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    requireNotNull(source) { "No se pudo leer la imagen de $label." }
    if (source.width == side && source.height == side) return source
    return Bitmap.createScaledBitmap(source, side, side, true)
  }

  private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    return if (bitmap.compress(format, quality, out)) out.toByteArray() else ByteArray(0)
  }

  @Suppress("DEPRECATION")
  private fun webpFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
    else Bitmap.CompressFormat.WEBP

  private fun toStickerWebp(bytes: ByteArray, number: Int): ByteArray {
    val bitmap = scaled(bytes, STICKER_SIDE, "el sticker $number")
    val result = encodeWithinLimit(MAX_STICKER_BYTES) { compress(bitmap, webpFormat(), it) }
    return requireNotNull(result) { "El sticker $number no cabe en 100 KB." }
  }

  private fun toTrayPng(bytes: ByteArray): ByteArray {
    val png = compress(scaled(bytes, TRAY_SIDE, "el icono"), Bitmap.CompressFormat.PNG, 100)
    require(png.isNotEmpty() && png.size <= MAX_TRAY_BYTES) { "El icono del paquete supera 50 KB." }
    return png
  }
}
