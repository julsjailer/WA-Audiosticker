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
 * Guarda paquetes de stickers estáticos cumpliendo los límites de WhatsApp:
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
  private const val MAX_TEXT = 128

  private val QUALITIES = intArrayOf(95, 85, 75, 65, 55, 45, 35, 25, 15, 5)

  /** El mismo nombre produce el mismo identificador, así reexportar actualiza el paquete. */
  fun identifierFor(packName: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(packName.trim().toByteArray(Charsets.UTF_8))
    return "pack_" + digest.take(8).joinToString("") { "%02x".format(it) }
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
    val identifier = identifierFor(name)

    // Se codifica todo antes de tocar el disco para no dejar paquetes a medias.
    val trayBytes = toTrayPng(tray)
    val encoded = stickers.mapIndexed { index, bytes -> toStickerWebp(bytes, index + 1) }

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
      val complete =
        json.getString("identifier") == dir.name &&
          stickers.length() in MIN_STICKERS..MAX_STICKERS &&
          fits(File(dir, json.getString("tray_image_file")), MAX_TRAY_BYTES) &&
          (0 until stickers.length()).all {
            fits(File(dir, stickers.getJSONObject(it).getString("image_file")), MAX_STICKER_BYTES)
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
