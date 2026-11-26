package com.example.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerProjectDraftTest {
  @Test
  fun videoCanKeepOriginalAudioAsOneProject() {
    val draft = StickerProjectDraft(
      title = "Clip",
      visualSourceType = VisualSourceType.VIDEO,
      visualSourceUri = "content://video/1",
      visualDurationSeconds = 8.0,
      audioMode = AudioMode.KEEP_VIDEO_AUDIO,
    )
    assertTrue(draft.includesAudio)
    assertTrue(draft.keepsOriginalVideoAudio)
    assertTrue(draft.validate().isEmpty())
  }

  @Test
  fun imageCanUseReplacementAudio() {
    val draft = StickerProjectDraft(
      title = "Photo sound sticker",
      visualSourceType = VisualSourceType.IMAGE,
      visualSourceUri = "content://image/1",
      audioMode = AudioMode.REPLACEMENT_FILE,
      audioSourceUri = "content://audio/1",
      audioDurationSeconds = 4.0,
    )
    assertTrue(draft.includesAudio)
    assertEquals(false, draft.keepsOriginalVideoAudio)
    assertTrue(draft.validate().isEmpty())
  }

  @Test
  fun imageCannotKeepVideoAudio() {
    val draft = StickerProjectDraft(
      title = "Invalid",
      visualSourceType = VisualSourceType.IMAGE,
      visualSourceUri = "content://image/1",
      audioMode = AudioMode.KEEP_VIDEO_AUDIO,
    )
    assertTrue(draft.validate().contains("Solo un vídeo puede conservar su audio original."))
  }

  @Test
  fun replacementAudioRequiresSource() {
    val draft = StickerProjectDraft(
      title = "Missing sound",
      visualSourceType = VisualSourceType.EMOJI,
      audioMode = AudioMode.REPLACEMENT_FILE,
    )
    assertTrue(draft.validate().contains("Selecciona o graba el audio que se usará."))
  }

  @Test
  fun trimRangeMustBeValidAndInsideSource() {
    val draft = StickerProjectDraft(
      title = "Bad trim",
      visualSourceType = VisualSourceType.VIDEO,
      visualSourceUri = "content://video/1",
      visualDurationSeconds = 5.0,
      visualTrimStartSeconds = 2.0,
      visualTrimEndSeconds = 7.0,
    )
    assertTrue(draft.validate().contains("El recorte visual excede la duración del archivo."))
  }
}
