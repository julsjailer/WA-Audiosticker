package com.example.sounds

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Muestra el sitio MyInstants tal como es. Cuando la persona toca «Descargar» en un sonido, ese
 * audio se entrega al editor. La app no busca ni recorre el sitio por su cuenta, y esta pantalla no
 * tiene ningún puente JavaScript hacia Android.
 */
class SoundLibraryActivity : ComponentActivity() {

  private var webView: WebView? = null
  private var downloading = false

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val bars = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B141A"))
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)

    val web = WebView(this)
    webView = web
    web.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      allowFileAccess = false
      allowContentAccess = false
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      setSupportMultipleWindows(false)
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)

    web.webViewClient =
      object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
          view: WebView?,
          request: WebResourceRequest?,
        ): Boolean {
          if (request == null || !request.isForMainFrame) return false
          val url = request.url.toString()
          if (SoundLibrary.soundName(url) != null) {
            download(url)
            return true
          }
          // Los anuncios y enlaces externos se quedan sin abrir.
          return !SoundLibrary.isLibraryPage(url)
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
          finish()
          return true
        }
      }
    web.setDownloadListener { url, _, _, _, _ -> download(url) }

    val hint =
      TextView(this).apply {
        setText(R.string.sounds_hint)
        setTextColor(android.graphics.Color.WHITE)
        textSize = 13f
        setPadding(24, 16, 24, 16)
      }
    val toolbar =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(
          hint,
          LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
          Button(context).apply {
            setText(R.string.web_close)
            isAllCaps = false
            setOnClickListener { finish() }
          }
        )
      }
    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(android.graphics.Color.parseColor("#0B141A"))
        addView(toolbar)
        addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
      }
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
      val area =
        insets.getInsets(
          WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime()
        )
      view.setPadding(area.left, area.top, area.right, area.bottom)
      WindowInsetsCompat.CONSUMED
    }
    setContentView(root)

    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (web.canGoBack()) web.goBack() else finish()
        }
      },
    )

    web.loadUrl(SoundLibrary.URL)
  }

  private fun download(url: String) {
    val name = SoundLibrary.soundName(url)
    if (name == null) {
      toast(R.string.sounds_only_audio)
      return
    }
    if (downloading) return
    downloading = true
    toast(R.string.sounds_downloading)
    val userAgent = webView?.settings?.userAgentString
    lifecycleScope.launch {
      try {
        withContext(Dispatchers.IO) { downloadedFile(this@SoundLibraryActivity).save(url, userAgent) }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_NAME, name))
        finish()
      } catch (_: IOException) {
        downloading = false
        toast(R.string.sounds_failed)
      }
    }
  }

  private fun File.save(url: String, userAgent: String?) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 15000
      connection.readTimeout = 15000
      if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent)
      CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
      if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Respuesta no válida")
      val bytes = ByteArrayOutputStream()
      connection.inputStream.use { input ->
        val buffer = ByteArray(16 * 1024)
        while (true) {
          val read = input.read(buffer)
          if (read < 0) break
          if (bytes.size() + read > SoundLibrary.MAX_BYTES) throw IOException("Audio demasiado grande")
          bytes.write(buffer, 0, read)
        }
      }
      if (bytes.size() == 0) throw IOException("Audio vacío")
      parentFile?.mkdirs()
      writeBytes(bytes.toByteArray())
    } finally {
      connection.disconnect()
    }
  }

  private fun toast(message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  }

  override fun onDestroy() {
    webView?.let { web ->
      (web.parent as? ViewGroup)?.removeView(web)
      web.destroy()
    }
    webView = null
    super.onDestroy()
  }

  companion object {
    /** Nombre original del audio descargado. */
    const val EXTRA_NAME = "sound_name"

    fun downloadedFile(context: android.content.Context): File =
      File(File(context.cacheDir, "sound_library"), "sound")
  }
}
