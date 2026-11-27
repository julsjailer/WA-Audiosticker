package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.sounds.SoundLibraryActivity
import com.example.stickers.StickerPackStore
import com.example.ui.SoundStickerViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.whatsappweb.SoundHandoff
import com.example.whatsappweb.WhatsAppWebActivity
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {

  private val viewModel: SoundStickerViewModel by viewModels()
  private var webViewRef: WebView? = null
  private var pendingPermissionRequest: PermissionRequest? = null
  private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
  private var pendingSaveBytes: ByteArray? = null

  private val micPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      val request = pendingPermissionRequest
      pendingPermissionRequest = null
      if (request != null) {
        if (granted) {
          request.grant(request.resources)
        } else {
          request.deny()
          Toast.makeText(this, getString(R.string.mic_permission_required), Toast.LENGTH_SHORT)
            .show()
        }
      }
    }

  private val openMediaLauncher =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      val callback = pendingFileCallback
      pendingFileCallback = null
      if (callback != null) {
        callback.onReceiveValue(if (uri != null) arrayOf(uri) else null)
      }
    }

  private val addStickerPackLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        Toast.makeText(this, getString(R.string.pack_added), Toast.LENGTH_SHORT).show()
      } else {
        // WhatsApp explica aquí por qué rechazó el paquete.
        val reason = result.data?.getStringExtra("validation_error")
        if (reason == null) {
          Toast.makeText(this, getString(R.string.pack_not_added), Toast.LENGTH_LONG).show()
        } else {
          Log.e("AudioStickers", "WhatsApp validation error: $reason")
          AlertDialog.Builder(this)
            .setTitle(R.string.pack_rejected_title)
            .setMessage(reason)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        }
      }
    }

  private val soundLibraryLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val name = result.data?.getStringExtra(SoundLibraryActivity.EXTRA_NAME)
      if (result.resultCode == RESULT_OK && name != null) deliverLibrarySound(name)
    }

  /** Pasa a la página el audio descargado de la biblioteca. */
  private fun deliverLibrarySound(name: String) {
    lifecycleScope.launch {
      val base64 =
        withContext(Dispatchers.IO) {
          val file = SoundLibraryActivity.downloadedFile(this@MainActivity)
          val bytes = if (file.isFile) file.readBytes() else null
          file.delete()
          bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } ?: return@launch
      webViewRef?.evaluateJavascript(
        "if(window.__onLibrarySound){window.__onLibrarySound(${JSONObject.quote(name)},'$base64');}",
        null,
      )
    }
  }

  private val saveFileLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val bytes = pendingSaveBytes
      pendingSaveBytes = null
      val uri = result.data?.data
      if (result.resultCode == RESULT_OK && uri != null && bytes != null) {
        try {
          contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
          }
          Toast.makeText(this, getString(R.string.save_success), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
          Toast.makeText(this, getString(R.string.save_error), Toast.LENGTH_SHORT).show()
        }
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#111B21")),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B141A")),
    )

    fun readAsset(path: String) = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    val htmlContent = LocalPage.compose(readAsset("audiostickers.html"), readAsset("animacion.js"))

    setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        val roomStickers by viewModel.stickers.collectAsStateWithLifecycle()
        var isPageReady by remember { mutableStateOf(false) }

        LaunchedEffect(roomStickers, isPageReady) {
          if (isPageReady) {
            val payload = viewModel.buildStickersPayloadJson(roomStickers)
            val quoted = JSONObject.quote(payload)
            webViewRef?.evaluateJavascript(
              "if(window.__onRoomStickersUpdated){window.__onRoomStickersUpdated($quoted);}",
              null,
            )
          }
        }

        BackHandler {
          val wv = webViewRef
          if (wv != null) {
            wv.evaluateJavascript(
              "(function(){ return window.__audioStickersHandleBack ? window.__audioStickersHandleBack() : false; })();"
            ) { result ->
              if (result != "true") {
                finish()
              }
            }
          } else {
            finish()
          }
        }

        AudioStickersWebHost(
          htmlContent = htmlContent,
          onWebViewCreated = { webViewRef = it },
          onPageReady = { isPageReady = true },
          modifier =
            Modifier.fillMaxSize()
              .background(Color(0xFF0B141A))
              .windowInsetsPadding(WindowInsets.safeDrawing)
              .testTag("audiostickers_webview"),
        )
      }
    }
  }

  override fun onDestroy() {
    pendingPermissionRequest?.deny()
    pendingPermissionRequest = null
    pendingFileCallback?.onReceiveValue(null)
    pendingFileCallback = null
    webViewRef?.destroy()
    webViewRef = null
    super.onDestroy()
  }

  /** "video/mp4;codecs=..." no es un tipo válido para MediaStore ni para compartir. */
  private fun baseMimeType(mimeType: String): String = mimeType.substringBefore(';').trim()

  private fun resolveMimeTypes(params: WebChromeClient.FileChooserParams?): Array<String> {
    val raw = params?.acceptTypes.orEmpty().joinToString(",").lowercase()
    val types = mutableListOf<String>()
    if (raw.contains("image")) types.add("image/*")
    if (raw.contains("video")) types.add("video/*")
    if (
      raw.contains("audio") ||
        raw.contains(".mp3") ||
        raw.contains(".wav") ||
        raw.contains(".ogg") ||
        raw.contains(".m4a")
    ) {
      types.add("audio/*")
    }
    return if (types.isNotEmpty()) types.toTypedArray() else arrayOf("*/*")
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Composable
  private fun AudioStickersWebHost(
    htmlContent: String,
    onWebViewCreated: (WebView) -> Unit,
    onPageReady: () -> Unit,
    modifier: Modifier = Modifier,
  ) {
    Box(modifier = modifier) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          WebView(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#0B141A"))
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              mediaPlaybackRequiresUserGesture = false
              allowContentAccess = true
              allowFileAccess = false
              mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
              useWideViewPort = true
              loadWithOverviewMode = true
            }

            addJavascriptInterface(AudioStickersBridge(), "AndroidBridge")

            webViewClient =
              object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                  view: WebView?,
                  request: WebResourceRequest?,
                ): Boolean = true // La página local nunca navega: así el puente no queda expuesto.

                override fun onPageFinished(view: WebView?, url: String?) {
                  super.onPageFinished(view, url)
                  onPageReady()
                }
              }

            webChromeClient =
              object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                  if (request == null) return
                  runOnUiThread {
                    val wantsAudio =
                      request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    if (!wantsAudio) {
                      request.deny()
                      return@runOnUiThread
                    }
                    val hasMic =
                      ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO,
                      ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                      request.grant(request.resources)
                    } else {
                      pendingPermissionRequest?.deny()
                      pendingPermissionRequest = request
                      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                  }
                }

                override fun onShowFileChooser(
                  webView: WebView?,
                  filePathCallback: ValueCallback<Array<Uri>>?,
                  fileChooserParams: FileChooserParams?,
                ): Boolean {
                  pendingFileCallback?.onReceiveValue(null)
                  pendingFileCallback = filePathCallback
                  return try {
                    openMediaLauncher.launch(resolveMimeTypes(fileChooserParams))
                    true
                  } catch (_: Exception) {
                    pendingFileCallback = null
                    false
                  }
                }
              }

            loadDataWithBaseURL(
              "https://audiostickers.app/",
              htmlContent,
              "text/html",
              "UTF-8",
              null,
            )
            onWebViewCreated(this)
          }
        },
      )
    }
  }

  private inner class AudioStickersBridge {
    @JavascriptInterface
    fun openSoundStickerWeb() {
      runOnUiThread {
        startActivity(Intent(this@MainActivity, WhatsAppWebActivity::class.java))
      }
    }

    @JavascriptInterface
    fun openSoundLibrary() {
      runOnUiThread {
        soundLibraryLauncher.launch(Intent(this@MainActivity, SoundLibraryActivity::class.java))
      }
    }

    @JavascriptInterface
    fun sendWithSound(base64Data: String, mimeType: String, shape: String) {
      lifecycleScope.launch {
        val type = SoundHandoff.videoType(mimeType)
        val saved =
          type != null &&
            withContext(Dispatchers.IO) {
              runCatching {
                  val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                  require(bytes.size <= SoundHandoff.MAX_BYTES)
                  val file = SoundHandoff.file(this@MainActivity)
                  file.parentFile?.mkdirs()
                  file.writeBytes(bytes)
                }
                .isSuccess
            }
        if (saved) {
          startActivity(
            Intent(this@MainActivity, WhatsAppWebActivity::class.java)
              .putExtra(WhatsAppWebActivity.EXTRA_STICKER_TYPE, type)
              .putExtra(WhatsAppWebActivity.EXTRA_STICKER_SHAPE, shape)
          )
        } else {
          Toast.makeText(this@MainActivity, getString(R.string.share_error), Toast.LENGTH_SHORT)
            .show()
        }
      }
    }

    private fun installedWhatsAppPackages(): List<String> =
      listOf("com.whatsapp", "com.whatsapp.w4b").filter { pkg ->
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
          } else {
            @Suppress("DEPRECATION") packageManager.getPackageInfo(pkg, 0)
          }
          true
        } catch (_: Exception) {
          false
        }
      }

    @JavascriptInterface
    fun shareToWhatsAppDirect(base64Data: String, fileName: String, mimeType: String, title: String) {
      runOnUiThread {
        try {
          val bytes = Base64.decode(base64Data, Base64.DEFAULT)
          val exportDir = File(cacheDir, "shared_stickers").apply { mkdirs() }
          val safeName =
            fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "AudioSticker.wav" }
          val outFile = File(exportDir, safeName)
          FileOutputStream(outFile).use { it.write(bytes) }

          val uri =
            FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", outFile)
          val resolvedMime = baseMimeType(mimeType).ifBlank { "audio/*" }
          val waPackage = installedWhatsAppPackages().firstOrNull()

          val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
              type = resolvedMime
              putExtra(Intent.EXTRA_STREAM, uri)
              if (title.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, title)
              }
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

          if (waPackage != null) {
            grantUriPermission(waPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareIntent.setPackage(waPackage)
            try {
              startActivity(shareIntent)
              return@runOnUiThread
            } catch (_: android.content.ActivityNotFoundException) {
              shareIntent.setPackage(null)
            }
          } else {
            Toast.makeText(
                this@MainActivity,
                "WhatsApp no está instalado; abriendo opciones para compartir.",
                Toast.LENGTH_SHORT,
              )
              .show()
          }

          startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        } catch (_: Exception) {
          Toast.makeText(this@MainActivity, getString(R.string.share_error), Toast.LENGTH_SHORT)
            .show()
        }
      }
    }

    @JavascriptInterface
    fun shareFile(base64Data: String, fileName: String, mimeType: String, title: String) {
      shareToWhatsAppDirect(base64Data, fileName, mimeType, title)
    }

    @JavascriptInterface
    fun saveFile(base64Data: String, fileName: String, mimeType: String) {
      runOnUiThread {
        try {
          val bytes = Base64.decode(base64Data, Base64.DEFAULT)
          val safeName =
            fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "SoundSticker.mp4" }
          val cleanMime = baseMimeType(mimeType).ifBlank { "video/mp4" }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cleanMime.startsWith("video/")) {
            val values =
              ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
                put(MediaStore.Video.Media.MIME_TYPE, cleanMime)
                put(
                  MediaStore.Video.Media.RELATIVE_PATH,
                  "${Environment.DIRECTORY_MOVIES}/AudioStickers",
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
              }
            val collection =
              MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = contentResolver.insert(collection, values)
            if (uri != null) {
              contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
              }
              values.clear()
              values.put(MediaStore.Video.Media.IS_PENDING, 0)
              contentResolver.update(uri, values, null, null)
              Toast.makeText(
                  this@MainActivity,
                  "Guardado en Galería (Movies/AudioStickers)",
                  Toast.LENGTH_LONG,
                )
                .show()
              return@runOnUiThread
            }
          }

          pendingSaveBytes = bytes
          val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
              addCategory(Intent.CATEGORY_OPENABLE)
              type = cleanMime
              putExtra(Intent.EXTRA_TITLE, safeName)
            }
          saveFileLauncher.launch(intent)
        } catch (_: Exception) {
          pendingSaveBytes = null
          Toast.makeText(this@MainActivity, getString(R.string.save_error), Toast.LENGTH_SHORT)
            .show()
        }
      }
    }

    @JavascriptInterface
    fun exportStickerPackToWhatsApp(packJson: String) {
      lifecycleScope.launch {
        try {
          val (identifier, packName) = withContext(Dispatchers.IO) { savePackFromJson(packJson) }
          val authority = "$packageName.stickercontentprovider"
          contentResolver.notifyChange(Uri.parse("content://$authority/metadata"), null)
          launchAddStickerPack(identifier, authority, packName)
        } catch (e: IllegalArgumentException) {
          Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
          Log.e("AudioStickers", "Failed to export sticker pack", e)
          Toast.makeText(this@MainActivity, getString(R.string.share_error), Toast.LENGTH_SHORT)
            .show()
        }
      }
    }

    private fun savePackFromJson(packJson: String): Pair<String, String> {
      val obj = JSONObject(packJson)
      val packName = obj.optString("name").trim().ifBlank { "AudioStickers" }
      val items = obj.optJSONArray("stickers") ?: org.json.JSONArray()
      val stickers =
        (0 until items.length()).map {
          Base64.decode(items.getJSONObject(it).getString("base64"), Base64.DEFAULT)
        }
      val identifier =
        StickerPackStore.savePack(
          filesDir = filesDir,
          packName = packName,
          publisher = obj.optString("publisher"),
          tray = Base64.decode(obj.getString("trayBase64"), Base64.DEFAULT),
          stickers = stickers,
        )
      return identifier to packName.take(128)
    }

    private fun launchAddStickerPack(identifier: String, authority: String, packName: String) {
      val installed = installedWhatsAppPackages()
      if (installed.isEmpty()) {
        Toast.makeText(
            this@MainActivity,
            getString(R.string.whatsapp_not_installed),
            Toast.LENGTH_LONG,
          )
          .show()
        return
      }
      val intent =
        Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
          putExtra("sticker_pack_id", identifier)
          putExtra("sticker_pack_authority", authority)
          putExtra("sticker_pack_name", packName)
        }
      // WhatsApp exige startActivityForResult para saber qué app envía el paquete.
      try {
        addStickerPackLauncher.launch(
          if (installed.size == 1) intent.setPackage(installed.first())
          else Intent.createChooser(intent, getString(R.string.add_to_whatsapp))
        )
      } catch (_: ActivityNotFoundException) {
        Toast.makeText(this@MainActivity, getString(R.string.pack_not_supported), Toast.LENGTH_LONG)
          .show()
      }
    }

    @JavascriptInterface
    fun saveRoomSticker(
      id: String,
      title: String,
      durationSec: Double,
      createdAt: Long,
      visualBase64: String,
      audioBase64: String,
    ) {
      viewModel.saveSticker(id, title, durationSec, createdAt, visualBase64, audioBase64)
    }

    @JavascriptInterface
    fun renameRoomSticker(id: String, newTitle: String) {
      viewModel.renameSticker(id, newTitle)
    }

    @JavascriptInterface
    fun deleteRoomSticker(id: String) {
      viewModel.deleteSticker(id)
    }

    @JavascriptInterface
    fun requestRoomStickers() {
      lifecycleScope.launch {
        val payload = viewModel.buildStickersPayloadJson(viewModel.stickers.value)
        val quoted = JSONObject.quote(payload)
        runOnUiThread {
          webViewRef?.evaluateJavascript(
            "if(window.__onRoomStickersUpdated){window.__onRoomStickersUpdated($quoted);}",
            null,
          )
        }
      }
    }

    @JavascriptInterface
    fun requestRoomStickerAudio(id: String, requestId: String) {
      lifecycleScope.launch {
        val b64 = viewModel.getAudioBase64(id)
        val quotedId = JSONObject.quote(requestId)
        val quotedB64 = JSONObject.quote(b64)
        runOnUiThread {
          webViewRef?.evaluateJavascript(
            "if(window.__onRoomStickerAudioLoaded){window.__onRoomStickerAudioLoaded($quotedId,$quotedB64);}",
            null,
          )
        }
      }
    }

    @JavascriptInterface
    fun reShareRoomStickerToWhatsApp(id: String) {
      lifecycleScope.launch {
        val entity = viewModel.getStickerById(id) ?: return@launch
        val b64 = viewModel.getAudioBase64(id)
        if (b64.isNotBlank()) {
          val safeTitle =
            entity.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "AudioSticker" }
          shareToWhatsAppDirect(b64, "${safeTitle}.wav", "audio/wav", entity.title)
        }
      }
    }
  }
}
