package com.example.stickers

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerPackStoreTest {

  @Test
  fun `same name keeps the same identifier`() {
    val first = StickerPackStore.identifierFor("Mis AudioStickers")
    assertEquals(first, StickerPackStore.identifierFor("  Mis AudioStickers "))
    assertNotEquals(first, StickerPackStore.identifierFor("Otro paquete"))
    assertTrue(first.matches(Regex("pack_[0-9a-f]{16}")))
  }

  @Test
  fun `lowers quality until the image fits`() {
    val tried = mutableListOf<Int>()
    val result =
      StickerPackStore.encodeWithinLimit(100) { quality ->
        tried += quality
        ByteArray(quality * 2)
      }
    assertArrayEquals(ByteArray(90), result)
    assertEquals(listOf(95, 85, 75, 65, 55, 45), tried)
  }

  @Test
  fun `gives up when nothing fits or encoding fails`() {
    assertNull(StickerPackStore.encodeWithinLimit(10) { ByteArray(11) })
    assertNull(StickerPackStore.encodeWithinLimit(10) { ByteArray(0) })
  }
}
