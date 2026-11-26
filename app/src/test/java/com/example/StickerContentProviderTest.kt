package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.stickers.StickerPackStore
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Comprueba el contrato que WhatsApp lee del proveedor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StickerContentProviderTest {

  private lateinit var provider: StickerContentProvider
  private lateinit var base: Uri

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val authority = "${context.packageName}.stickercontentprovider"
    base = Uri.parse("content://$authority")

    val dir = File(File(context.filesDir, StickerPackStore.ROOT_DIR), "pack_test")
    dir.deleteRecursively()
    dir.mkdirs()
    File(dir, "tray.png").writeBytes(byteArrayOf(1))
    val stickers = JSONArray()
    for (i in 1..3) {
      File(dir, "sticker_$i.webp").writeBytes(byteArrayOf(i.toByte()))
      stickers.put(JSONObject().put("image_file", "sticker_$i.webp").put("emojis", "🔊"))
    }
    File(dir, "metadata.json")
      .writeText(
        JSONObject()
          .put("identifier", "pack_test")
          .put("name", "Prueba")
          .put("publisher", "AudioStickers")
          .put("tray_image_file", "tray.png")
          .put("image_data_version", "1")
          .put("stickers", stickers)
          .toString()
      )
    provider =
      Robolectric.buildContentProvider(StickerContentProvider::class.java).create(authority).get()
  }

  @Test
  fun `metadata uses the column names WhatsApp expects`() {
    provider.query(Uri.withAppendedPath(base, "metadata"), null, null, null, null).use { cursor ->
      assertEquals(
        listOf(
          "sticker_pack_identifier",
          "sticker_pack_name",
          "sticker_pack_publisher",
          "sticker_pack_icon",
          "android_play_store_link",
          "ios_app_download_link",
          "sticker_pack_publisher_email",
          "sticker_pack_publisher_website",
          "sticker_pack_privacy_policy_website",
          "sticker_pack_license_agreement_website",
          "image_data_version",
          "whatsapp_will_not_cache_stickers",
          "animated_sticker_pack",
        ),
        cursor.columnNames.toList(),
      )
      assertEquals(1, cursor.count)
    }
  }

  @Test
  fun `reports whether a pack is animated`() {
    fun animatedFlag(): Int =
      provider.query(Uri.withAppendedPath(base, "metadata"), null, null, null, null).use {
        it.moveToFirst()
        it.getInt(it.getColumnIndexOrThrow("animated_sticker_pack"))
      }
    assertEquals(0, animatedFlag())

    val context = ApplicationProvider.getApplicationContext<Context>()
    val metadata = File(context.filesDir, "${StickerPackStore.ROOT_DIR}/pack_test/metadata.json")
    metadata.writeText(JSONObject(metadata.readText()).put("animated_sticker_pack", true).toString())
    assertEquals(1, animatedFlag())
  }

  @Test
  fun `lists stickers and serves only listed files`() {
    provider.query(Uri.parse("$base/stickers/pack_test"), null, null, null, null).use {
      assertEquals(3, it.count)
    }
    val bytes =
      provider.openAssetFile(Uri.parse("$base/stickers_asset/pack_test/sticker_2.webp"), "r")
        .createInputStream()
        .use { it.readBytes() }
    assertArrayEquals(byteArrayOf(2), bytes)
    assertThrows(FileNotFoundException::class.java) {
      provider.openAssetFile(Uri.parse("$base/stickers_asset/pack_test/metadata.json"), "r")
    }
  }
}
