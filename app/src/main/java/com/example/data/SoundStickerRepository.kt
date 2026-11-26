package com.example.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SoundStickerRepository(
  private val dao: SoundStickerDao,
  private val storageDir: File,
) {
  constructor(
    context: Context
  ) : this(
    dao = AudioStickersDatabase.getInstance(context).soundStickerDao(),
    storageDir = File(context.applicationContext.filesDir, "room_stickers").apply { mkdirs() },
  )

  val allStickers: Flow<List<SoundStickerEntity>> = dao.getAllStickers()

  suspend fun saveSticker(
    id: String,
    title: String,
    durationSec: Double,
    createdAt: Long,
    visualBase64: String,
    audioBase64: String,
  ): SoundStickerEntity =
    withContext(Dispatchers.IO) {
      storageDir.mkdirs()
      val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "stk_${System.currentTimeMillis()}" }
      val visualFile = File(storageDir, "${safeId}.webp")
      val audioFile = File(storageDir, "${safeId}.wav")

      val visualBytes = Base64.decode(visualBase64, Base64.DEFAULT)
      val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
      FileOutputStream(visualFile).use { it.write(visualBytes) }
      FileOutputStream(audioFile).use { it.write(audioBytes) }

      val entity =
        SoundStickerEntity(
          id = safeId,
          title = title,
          durationSec = durationSec,
          createdAt = createdAt,
          visualFilePath = visualFile.absolutePath,
          audioFilePath = audioFile.absolutePath,
        )
      dao.insertSticker(entity)
      entity
    }

  suspend fun getStickerById(id: String): SoundStickerEntity? =
    withContext(Dispatchers.IO) { dao.getStickerById(id) }

  suspend fun renameSticker(id: String, newTitle: String) =
    withContext(Dispatchers.IO) { dao.renameSticker(id, newTitle) }

  suspend fun deleteSticker(id: String) =
    withContext(Dispatchers.IO) {
      val existing = dao.getStickerById(id)
      dao.deleteStickerById(id)
      if (existing != null) {
        runCatching { File(existing.visualFilePath).delete() }
        runCatching { File(existing.audioFilePath).delete() }
      }
    }

  suspend fun readVisualBase64(entity: SoundStickerEntity): String =
    withContext(Dispatchers.IO) {
      val f = File(entity.visualFilePath)
      if (!f.exists()) return@withContext ""
      Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }

  suspend fun readAudioBase64(id: String): String =
    withContext(Dispatchers.IO) {
      val entity = dao.getStickerById(id) ?: return@withContext ""
      val f = File(entity.audioFilePath)
      if (!f.exists()) return@withContext ""
      Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }
}
