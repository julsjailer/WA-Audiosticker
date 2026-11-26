package com.example

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONObject

class StickerContentProvider : ContentProvider() {

  companion object {
    const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
    const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
    const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
    const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
    const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
    const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
    const val PUBLISHER_EMAIL = "publisher_email"
    const val PUBLISHER_WEBSITE = "publisher_website"
    const val PRIVACY_POLICY_WEBSITE = "privacy_policy_website"
    const val LICENSE_AGREEMENT_WEBSITE = "license_agreement_website"
    const val IMAGE_DATA_VERSION = "image_data_version"
    const val AVOID_CACHE = "avoid_cache"
    const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

    const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
    const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"

    private const val METADATA_CODE = 1
    private const val METADATA_CODE_FOR_SINGLE_PACK = 2
    private const val STICKERS_CODE = 3
    private const val STICKERS_ASSET_CODE = 4
  }

  private lateinit var matcher: UriMatcher

  override fun onCreate(): Boolean {
    val ctx = context ?: return false
    val authority = "${ctx.packageName}.stickercontentprovider"
    matcher =
      UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(authority, "metadata", METADATA_CODE)
        addURI(authority, "metadata/*", METADATA_CODE_FOR_SINGLE_PACK)
        addURI(authority, "stickers/*", STICKERS_CODE)
        addURI(authority, "stickers_asset/*/*", STICKERS_ASSET_CODE)
      }
    return true
  }

  private fun packsRootDir(): File? =
    context?.let { File(it.filesDir, "sticker_packs").apply { mkdirs() } }

  private fun loadAllPackJsons(): List<JSONObject> {
    val root = packsRootDir() ?: return emptyList()
    val dirs = root.listFiles { file -> file.isDirectory } ?: return emptyList()
    return dirs.mapNotNull { dir ->
      val metaFile = File(dir, "metadata.json")
      if (!metaFile.exists()) return@mapNotNull null
      try {
        JSONObject(metaFile.readText(Charsets.UTF_8))
      } catch (_: Exception) {
        null
      }
    }
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor {
    val ctx = context ?: throw IllegalStateException("Context missing")
    return when (matcher.match(uri)) {
      METADATA_CODE -> {
        buildPackCursor(uri, loadAllPackJsons()).apply {
          setNotificationUri(ctx.contentResolver, uri)
        }
      }
      METADATA_CODE_FOR_SINGLE_PACK -> {
        val identifier = uri.lastPathSegment.orEmpty()
        val matched = loadAllPackJsons().filter { it.optString("identifier") == identifier }
        buildPackCursor(uri, matched).apply {
          setNotificationUri(ctx.contentResolver, uri)
        }
      }
      STICKERS_CODE -> {
        val identifier = uri.lastPathSegment.orEmpty()
        val pack = loadAllPackJsons().firstOrNull { it.optString("identifier") == identifier }
        buildStickersCursor(pack).apply { setNotificationUri(ctx.contentResolver, uri) }
      }
      else -> throw IllegalArgumentException("Unknown URI: $uri")
    }
  }

  private fun buildPackCursor(uri: Uri, packs: List<JSONObject>): Cursor {
    val cols =
      arrayOf(
        STICKER_PACK_IDENTIFIER_IN_QUERY,
        STICKER_PACK_NAME_IN_QUERY,
        STICKER_PACK_PUBLISHER_IN_QUERY,
        STICKER_PACK_ICON_IN_QUERY,
        ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
        IOS_APP_DOWNLOAD_LINK_IN_QUERY,
        PUBLISHER_EMAIL,
        PUBLISHER_WEBSITE,
        PRIVACY_POLICY_WEBSITE,
        LICENSE_AGREEMENT_WEBSITE,
        IMAGE_DATA_VERSION,
        AVOID_CACHE,
        ANIMATED_STICKER_PACK,
      )
    val cursor = MatrixCursor(cols)
    for (pack in packs) {
      cursor.addRow(
        arrayOf<Any>(
          pack.optString("identifier"),
          pack.optString("name", "AudioStickers"),
          pack.optString("publisher", "AudioStickers"),
          pack.optString("tray_image_file", "tray.png"),
          "",
          "",
          "",
          "",
          "",
          "",
          pack.optString("image_data_version", "1"),
          1,
          0,
        )
      )
    }
    return cursor
  }

  private fun buildStickersCursor(pack: JSONObject?): Cursor {
    val cursor = MatrixCursor(arrayOf(STICKER_FILE_NAME_IN_QUERY, STICKER_FILE_EMOJI_IN_QUERY))
    val stickers: JSONArray = pack?.optJSONArray("stickers") ?: return cursor
    for (i in 0 until stickers.length()) {
      val s = stickers.optJSONObject(i) ?: continue
      cursor.addRow(
        arrayOf<Any>(s.optString("image_file"), s.optString("emojis", "🔊"))
      )
    }
    return cursor
  }

  override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
    if (matcher.match(uri) != STICKERS_ASSET_CODE) {
      throw FileNotFoundException("Invalid asset URI: $uri")
    }
    val segments = uri.pathSegments
    if (segments.size != 3) throw FileNotFoundException("Invalid segments: $uri")
    val identifier = segments[1].replace(Regex("[^a-zA-Z0-9_-]"), "")
    val fileName = segments[2].replace(Regex("[^a-zA-Z0-9._-]"), "")
    val root = packsRootDir() ?: throw FileNotFoundException("Storage unavailable")
    val file = File(File(root, identifier), fileName)
    if (!file.exists()) throw FileNotFoundException("Missing sticker asset: $fileName")
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
  }

  override fun getType(uri: Uri): String =
    when (matcher.match(uri)) {
      METADATA_CODE -> "vnd.android.cursor.dir/vnd.${context?.packageName}.stickercontentprovider.metadata"
      METADATA_CODE_FOR_SINGLE_PACK ->
        "vnd.android.cursor.item/vnd.${context?.packageName}.stickercontentprovider.metadata"
      STICKERS_CODE -> "vnd.android.cursor.dir/vnd.${context?.packageName}.stickercontentprovider.stickers"
      STICKERS_ASSET_CODE ->
        if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
      else -> throw IllegalArgumentException("Unknown URI: $uri")
    }

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int = 0
}
