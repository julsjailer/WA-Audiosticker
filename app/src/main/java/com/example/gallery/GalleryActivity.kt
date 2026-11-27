package com.example.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Muestra las fotos o los videos del teléfono, del más nuevo al más antiguo, y devuelve uno. */
class GalleryActivity : ComponentActivity() {

  private data class Item(val id: Long, val uri: Uri, val added: Long, val durationMs: Long?)

  private lateinit var kind: MediaGallery.Kind
  private var access by mutableStateOf(MediaGallery.Access.NONE)
  private var items by mutableStateOf<List<Item>?>(null)
  private var loading: Job? = null

  private val thumbnails =
    object : LruCache<Uri, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
      override fun sizeOf(key: Uri, value: Bitmap): Int = value.byteCount / 1024
    }

  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    kind =
      MediaGallery.Kind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) }
        ?: MediaGallery.Kind.VIDEOS
    val bars = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B141A"))
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    setContent { MyApplicationTheme(darkTheme = true, dynamicColor = false) { Screen() } }
    if (savedInstanceState == null && currentAccess() == MediaGallery.Access.NONE) askPermission()
  }

  // También cubre el regreso desde los ajustes del teléfono.
  override fun onResume() {
    super.onResume()
    refresh()
  }

  private fun currentAccess(): MediaGallery.Access =
    MediaGallery.access(Build.VERSION.SDK_INT, kind) {
      ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

  private fun askPermission() {
    permissionLauncher.launch(MediaGallery.permissions(Build.VERSION.SDK_INT, kind))
  }

  private fun openSettings() {
    startActivity(
      Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
      )
    )
  }

  private fun refresh() {
    access = currentAccess()
    loading?.cancel()
    if (access == MediaGallery.Access.NONE) {
      items = null
      return
    }
    loading = lifecycleScope.launch { items = withContext(Dispatchers.IO) { query() } }
  }

  private fun query(): List<Item> {
    val videos = kind == MediaGallery.Kind.VIDEOS
    val collection =
      if (videos) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
      else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val columns =
      listOfNotNull(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DATE_ADDED,
        if (videos) MediaStore.Video.VideoColumns.DURATION else null,
      )
    val found = mutableListOf<Item>()
    runCatching {
      contentResolver
        .query(
          collection,
          columns.toTypedArray(),
          null,
          null,
          "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )
        ?.use { cursor ->
          while (cursor.moveToNext() && found.size < MAX_ITEMS) {
            val id = cursor.getLong(0)
            found.add(
              Item(
                id,
                ContentUris.withAppendedId(collection, id),
                cursor.getLong(1),
                if (videos) cursor.getLong(2) else null,
              )
            )
          }
        }
    }
    return found
  }

  @Suppress("DEPRECATION")
  private fun thumbnail(resolver: ContentResolver, item: Item): Bitmap? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          resolver.loadThumbnail(item.uri, Size(THUMBNAIL_PX, THUMBNAIL_PX), null)
        } else if (item.durationMs != null) {
          MediaStore.Video.Thumbnails.getThumbnail(
            resolver,
            item.id,
            MediaStore.Video.Thumbnails.MINI_KIND,
            null,
          )
        } else {
          MediaStore.Images.Thumbnails.getThumbnail(
            resolver,
            item.id,
            MediaStore.Images.Thumbnails.MINI_KIND,
            null,
          )
        }
      }
      .getOrNull()

  private fun choose(uri: Uri) {
    setResult(RESULT_OK, Intent().setData(uri))
    finish()
  }

  private fun useSystemPicker() {
    setResult(RESULT_OTHER_FILES)
    finish()
  }

  @Composable
  private fun Screen() {
    Column(
      Modifier.fillMaxSize()
        .background(Color(0xFF0B141A))
        .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          stringResource(
            if (kind == MediaGallery.Kind.VIDEOS) R.string.gallery_videos
            else R.string.gallery_photos
          ),
          Modifier.weight(1f).padding(start = 8.dp),
          color = Color.White,
          fontSize = 18.sp,
        )
        TextButton(onClick = { useSystemPicker() }) {
          Text(stringResource(R.string.gallery_other_files))
        }
        TextButton(onClick = { finish() }) { Text(stringResource(R.string.web_close)) }
      }
      if (access == MediaGallery.Access.PARTIAL) {
        TextButton(onClick = { askPermission() }, modifier = Modifier.padding(horizontal = 8.dp)) {
          Text(stringResource(R.string.gallery_allow_more))
        }
      }
      val list = items
      when {
        access == MediaGallery.Access.NONE -> PermissionNeeded()
        list == null -> Message(stringResource(R.string.gallery_loading))
        list.isEmpty() -> Message(stringResource(R.string.gallery_empty))
        else ->
          LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(list, key = { it.id }) { Cell(it) }
          }
      }
    }
  }

  @Composable
  private fun Cell(item: Item) {
    val bitmap by
      produceState<Bitmap?>(initialValue = thumbnails.get(item.uri), item.uri) {
        if (value == null) {
          value =
            withContext(Dispatchers.IO) { thumbnail(contentResolver, item) }
              ?.also { thumbnails.put(item.uri, it) }
        }
      }
    val description =
      stringResource(
        if (item.durationMs != null) R.string.gallery_video_item else R.string.gallery_photo_item
      )
    Box(
      Modifier.aspectRatio(1f)
        .background(Color(0xFF202C33))
        .clickable { choose(item.uri) }
        .semantics { contentDescription = description }
    ) {
      bitmap?.let {
        Image(
          it.asImageBitmap(),
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
      }
      item.durationMs?.let {
        Text(
          MediaGallery.durationLabel(it),
          Modifier.align(Alignment.BottomEnd)
            .background(Color(0xB0000000))
            .padding(horizontal = 5.dp, vertical = 1.dp),
          color = Color.White,
          fontSize = 12.sp,
        )
      }
    }
  }

  @Composable
  private fun Message(text: String) {
    Text(
      text,
      Modifier.fillMaxWidth().padding(32.dp),
      color = Color.White,
      textAlign = TextAlign.Center,
    )
  }

  @Composable
  private fun PermissionNeeded() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Message(stringResource(R.string.gallery_permission))
      Button(onClick = { askPermission() }) { Text(stringResource(R.string.gallery_allow)) }
      TextButton(onClick = { openSettings() }) { Text(stringResource(R.string.gallery_settings)) }
    }
  }

  companion object {
    /** Nombre de un [MediaGallery.Kind]. */
    const val EXTRA_KIND = "gallery_kind"
    /** La persona prefiere el selector de archivos del sistema. */
    const val RESULT_OTHER_FILES = android.app.Activity.RESULT_FIRST_USER
    private const val MAX_ITEMS = 5000
    private const val THUMBNAIL_PX = 320
  }
}
