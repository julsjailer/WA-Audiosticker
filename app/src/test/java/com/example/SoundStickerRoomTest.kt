package com.example

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AudioStickersDatabase
import com.example.data.SoundStickerRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoundStickerRoomTest {

  private lateinit var db: AudioStickersDatabase
  private lateinit var tempDir: File
  private lateinit var repository: SoundStickerRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AudioStickersDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    tempDir = File(context.cacheDir, "test_room_stickers_${System.nanoTime()}").apply { mkdirs() }
    repository = SoundStickerRepository(db.soundStickerDao(), tempDir)
  }

  @After
  fun tearDown() {
    db.close()
    tempDir.deleteRecursively()
  }

  @Test
  fun saveReadRenameUpdateAndDeleteSticker() = runTest {
    val visualB64 = Base64.encodeToString("webp-bytes-v1".toByteArray(), Base64.NO_WRAP)
    val audioB64 = Base64.encodeToString("wav-bytes-v1".toByteArray(), Base64.NO_WRAP)

    val saved = repository.saveSticker(
      id = "snd_101",
      title = "Risa Epica",
      durationSec = 2.5,
      createdAt = 1000L,
      visualBase64 = visualB64,
      audioBase64 = audioB64,
    )

    assertEquals("snd_101", saved.id)
    assertTrue(File(saved.visualFilePath).exists())
    assertTrue(File(saved.audioFilePath).exists())

    val listAfterInsert = repository.allStickers.first()
    assertEquals(1, listAfterInsert.size)
    assertEquals("Risa Epica", listAfterInsert[0].title)
    assertEquals(visualB64, repository.readVisualBase64(listAfterInsert[0]))
    assertEquals(audioB64, repository.readAudioBase64("snd_101"))

    // Rename
    repository.renameSticker("snd_101", "Risa Nueva")
    val renamed = repository.getStickerById("snd_101")
    assertNotNull(renamed)
    assertEquals("Risa Nueva", renamed?.title)

    // Edit/Overwrite existing sticker
    val visualV2 = Base64.encodeToString("webp-bytes-v2".toByteArray(), Base64.NO_WRAP)
    val audioV2 = Base64.encodeToString("wav-bytes-v2".toByteArray(), Base64.NO_WRAP)
    repository.saveSticker(
      id = "snd_101",
      title = "Risa Editada",
      durationSec = 3.2,
      createdAt = 2000L,
      visualBase64 = visualV2,
      audioBase64 = audioV2,
    )

    val listAfterUpdate = repository.allStickers.first()
    assertEquals(1, listAfterUpdate.size)
    assertEquals("Risa Editada", listAfterUpdate[0].title)
    assertEquals(3.2, listAfterUpdate[0].durationSec, 0.001)
    assertEquals(visualV2, repository.readVisualBase64(listAfterUpdate[0]))
    assertEquals(audioV2, repository.readAudioBase64("snd_101"))

    // Delete cleans up both Room row and files
    repository.deleteSticker("snd_101")
    assertNull(repository.getStickerById("snd_101"))
    assertEquals(0, repository.allStickers.first().size)
    assertFalse(File(saved.visualFilePath).exists())
    assertFalse(File(saved.audioFilePath).exists())
  }
}
