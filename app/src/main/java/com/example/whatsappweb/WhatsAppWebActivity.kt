package com.example.whatsappweb

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.R

/**
 * Experimento no oficial: WhatsApp Web con el código de la extensión "Stickers con sonido".
 *
 * Esta pantalla contiene la sesión de WhatsApp del usuario, por eso no expone ningún puente
 * JavaScript hacia Android, no permite navegar fuera de web.whatsapp.com y no escribe la consola
 * en el registro del sistema.
 */
class WhatsAppWebActivity : ComponentActivity() {

  private var webView: WebView? = null
  private var pendingFiles: ValueCallback<Array<Uri>>? = null
  private var deliveryScript: String? = null
  private val handler = Handler(Looper.getMainLooper())

  // Pregunta a la página si ya abrió el sticker; hasta entonces la copia se conserva.
  private val checkDelivery =
    object : Runnable {
      override fun run() {
        val web = webView ?: return
        if (deliveryScript == null) return
        web.evaluateJavascript("!!(window.__waasMovil && window.__waasMovil.delivered)") {
          if (it == "true") discardSticker()
        }
        handler.postDelayed(this, DELIVERY_CHECK_MS)
      }
    }

  private val pickFile =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      pendingFiles?.onReceiveValue(uri?.let { arrayOf(it) })
      pendingFiles = null
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val bars = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B141A"))
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    // La sesión no debe poder inspeccionarse por USB, ni en compilaciones de prueba.
    WebView.setWebContentsDebuggingEnabled(false)

    val preferences = getSharedPreferences("whatsapp_web", MODE_PRIVATE)
    if (preferences.getBoolean(KEY_ACCEPTED, false)) {
      showWhatsAppWeb()
    } else {
      AlertDialog.Builder(this)
        .setTitle(R.string.web_risk_title)
        .setMessage(R.string.web_risk_message)
        .setPositiveButton(R.string.web_risk_accept) { _, _ ->
          preferences.edit().putBoolean(KEY_ACCEPTED, true).apply()
          showWhatsAppWeb()
        }
        .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
        .setOnCancelListener { finish() }
        .show()
    }
  }

  private fun loadPendingSticker() {
    val type = SoundHandoff.videoType(intent.getStringExtra(EXTRA_STICKER_TYPE).orEmpty()) ?: return
    val file = SoundHandoff.file(this)
    if (!file.isFile || file.length() > SoundHandoff.MAX_BYTES) return
    val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    val shape = SoundHandoff.shape(intent.getStringExtra(EXTRA_STICKER_SHAPE))
    deliveryScript = SoundHandoff.deliveryScript(base64, type, shape)
    toast(R.string.web_sticker_waiting)
    handler.postDelayed(checkDelivery, DELIVERY_CHECK_MS)
  }

  private fun discardSticker() {
    deliveryScript = null
    handler.removeCallbacks(checkDelivery)
    SoundHandoff.file(this).delete()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun showWhatsAppWeb() {
    val script =
      WhatsAppWeb.injectionScript(
        WhatsAppWeb.SCRIPT_ASSETS.map { path ->
          assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
      )

    val web = WebView(this)
    webView = web
    CookieManager.getInstance().setAcceptCookie(true)
    loadPendingSticker()
    web.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      allowFileAccess = false
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      mediaPlaybackRequiresUserGesture = false
      useWideViewPort = true
      loadWithOverviewMode = true
      builtInZoomControls = true
      displayZoomControls = false
    }
    presentAsDesktop(web.settings)

    web.webViewClient =
      object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
          view: WebView?,
          request: WebResourceRequest?,
        ): Boolean {
          if (request == null || !request.isForMainFrame) return false
          if (WhatsAppWeb.isWhatsAppWeb(request.url.toString())) return false
          if (request.url.scheme == "https") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
          }
          return true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          if (!WhatsAppWeb.isWhatsAppWeb(url)) return
          view?.evaluateJavascript(script, null)
          // Se repite en cada carga: al vincular la cuenta la página puede recargarse.
          deliveryScript?.let { view?.evaluateJavascript(it, null) }
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
          toast(R.string.web_crashed)
          finish()
          return true
        }
      }

    web.webChromeClient =
      object : WebChromeClient() {
        override fun onShowFileChooser(
          webView: WebView?,
          filePathCallback: ValueCallback<Array<Uri>>?,
          fileChooserParams: FileChooserParams?,
        ): Boolean {
          pendingFiles?.onReceiveValue(null)
          pendingFiles = filePathCallback
          val accept = fileChooserParams?.acceptTypes.orEmpty().joinToString(",")
          val types =
            listOf("image", "video", "audio").filter { it in accept }.map { "$it/*" }
          return try {
            pickFile.launch(types.ifEmpty { listOf("*/*") }.toTypedArray())
            true
          } catch (_: Exception) {
            pendingFiles = null
            false
          }
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
      }

    web.setDownloadListener { _, _, _, _, _ -> toast(R.string.web_no_downloads) }

    val toolbar =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(
          toolbarButton(R.string.web_create_sticker) {
            web.evaluateJavascript("!!(window.__waasMovil && window.__waasMovil.toggle())") {
              if (it != "true") toast(R.string.web_not_ready)
            }
          }
        )
        addView(toolbarButton(R.string.web_reload) { web.loadUrl(WhatsAppWeb.URL) })
        addView(toolbarButton(R.string.web_close) { finish() })
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
          web.evaluateJavascript("!!(window.__waasMovil && window.__waasMovil.back())") {
            if (it != "true") finish()
          }
        }
      },
    )

    // A propósito no se pausa el WebView al salir: hay que ir a WhatsApp a escribir el código
    // de vinculación y la página debe seguir activa.
    web.loadUrl(WhatsAppWeb.URL)
  }

  private fun presentAsDesktop(settings: WebSettings) {
    val major = WhatsAppWeb.chromeMajor(settings.userAgentString) ?: return
    settings.userAgentString = WhatsAppWeb.desktopUserAgent(major)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
      val brands =
        listOf("Chromium", "Google Chrome").map { brand ->
          UserAgentMetadata.BrandVersion.Builder()
            .setBrand(brand)
            .setMajorVersion(major)
            .setFullVersion("$major.0.0.0")
            .build()
        }
      WebSettingsCompat.setUserAgentMetadata(
        settings,
        UserAgentMetadata.Builder()
          .setBrandVersionList(brands)
          .setFullVersion("$major.0.0.0")
          .setPlatform("Linux")
          .setMobile(false)
          .build(),
      )
    }
  }

  private fun toolbarButton(label: Int, onClick: () -> Unit): Button =
    Button(this).apply {
      setText(label)
      isAllCaps = false
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      setOnClickListener { onClick() }
    }

  private fun toast(message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
  }

  // La sesión se guarda sola; esto obliga a escribirla antes de que el sistema pueda cerrar la app.
  override fun onPause() {
    CookieManager.getInstance().flush()
    super.onPause()
  }

  override fun onStop() {
    CookieManager.getInstance().flush()
    super.onStop()
  }

  override fun onDestroy() {
    handler.removeCallbacks(checkDelivery)
    if (isFinishing) discardSticker()
    pendingFiles?.onReceiveValue(null)
    pendingFiles = null
    webView?.let { web ->
      (web.parent as? ViewGroup)?.removeView(web)
      web.destroy()
    }
    webView = null
    super.onDestroy()
  }

  companion object {
    /** Tipo del video que dejó el editor en [SoundHandoff]. */
    const val EXTRA_STICKER_TYPE = "sticker_type"
    const val EXTRA_STICKER_SHAPE = "sticker_shape"
    private const val KEY_ACCEPTED = "risk_accepted"
    private const val DELIVERY_CHECK_MS = 3000L
  }
}
