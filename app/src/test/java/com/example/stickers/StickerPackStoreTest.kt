package com.example.stickers

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
  fun `animated packs get their own identifier`() {
    assertEquals(
      StickerPackStore.identifierFor("Mis AudioStickers") + "_a",
      StickerPackStore.identifierFor("Mis AudioStickers", animated = true),
    )
  }

  @Test
  fun `reads the size and frame durations of an animated webp`() {
    val bytes = animatedWebp(listOf(100, 83, 300))
    val animation = StickerPackStore.readAnimation(bytes)!!
    assertEquals(StickerPackStore.Animation(512, 512, listOf(100, 83, 300)), animation)
    assertArrayEquals(bytes, StickerPackStore.checkAnimatedSticker(bytes, animation, 1))
  }

  @Test
  fun `still images are not animations`() {
    assertNull(StickerPackStore.readAnimation(ByteArray(100)))
    assertNull(StickerPackStore.readAnimation(byteArrayOf(1, 2, 3)))
    val still = animatedWebp(listOf(100, 100)).also { it[20] = 0x10 }
    assertNull(StickerPackStore.readAnimation(still))
  }

  @Test
  fun `rejects animations WhatsApp would not accept`() {
    val invalid =
      listOf(
        animatedWebp(listOf(500)), // un solo cuadro
        animatedWebp(listOf(100, 7)), // cuadro demasiado corto
        animatedWebp(listOf(6000, 4001)), // más de 10 s
        animatedWebp(listOf(100, 100), side = 256),
        animatedWebp(listOf(100, 100), frameBytes = 300 * 1024), // más de 500 KB
      )
    for (bytes in invalid) {
      assertThrows(IllegalArgumentException::class.java) {
        StickerPackStore.checkAnimatedSticker(bytes, StickerPackStore.readAnimation(bytes)!!, 1)
      }
    }
  }

  @Test
  fun `rejects a truncated animation`() {
    val bytes = animatedWebp(listOf(100, 100))
    assertThrows(IllegalArgumentException::class.java) {
      StickerPackStore.readAnimation(bytes.copyOf(bytes.size - 4))
    }
  }

  /** WebP animado mínimo: cabecera VP8X, ANIM y un bloque ANMF por cuadro. */
  private fun animatedWebp(durations: List<Int>, side: Int = 512, frameBytes: Int = 4): ByteArray {
    fun little(value: Int, count: Int) = ByteArray(count) { (value shr (8 * it)).toByte() }
    fun chunk(tag: String, data: ByteArray) = tag.toByteArray() + little(data.size, 4) + data

    var body = chunk("VP8X", byteArrayOf(0x02, 0, 0, 0) + little(side - 1, 3) + little(side - 1, 3))
    body += chunk("ANIM", ByteArray(6))
    for (duration in durations) {
      val header = ByteArray(12) + little(duration, 3) + byteArrayOf(0x02)
      body += chunk("ANMF", header + ByteArray(frameBytes))
    }
    return "RIFF".toByteArray() + little(body.size + 4, 4) + "WEBP".toByteArray() + body
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
