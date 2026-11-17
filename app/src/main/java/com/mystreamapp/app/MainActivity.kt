package com.mystreamapp.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

/**
 * Pantalla principal: muestra la interfaz web que está en assets/www
 * dentro de un WebView y le da acceso a funciones de Android mediante [Puente].
 */
class MainActivity : ComponentActivity() {

    private lateinit var raiz: FrameLayout
    private lateinit var web: WebView
    private var vistaCompleta: View? = null
    private var callbackCompleta: WebChromeClient.CustomViewCallback? = null
    private var ultimoAtras = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        raiz = FrameLayout(this).apply { setBackgroundColor(COLOR_FONDO) }
        web = WebView(this).apply {
            setBackgroundColor(COLOR_FONDO)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        raiz.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(raiz)

        // La app dibuja de borde a borde: dejamos espacio para las barras del sistema y el teclado.
        WindowCompat.getInsetsController(window, raiz).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(barras.left, barras.top, barras.right, maxOf(barras.bottom, teclado.bottom))
            WindowInsetsCompat.CONSUMED
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        web.addJavascriptInterface(Puente(this, web), "Nativo")
        web.webViewClient = Cliente()
        web.webChromeClient = ClienteChrome()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = atras()
        })

        if (savedInstanceState == null) {
            web.loadUrl(INICIO)
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    /** Primero le pregunta a la interfaz web si puede volver atrás; si no, sale con doble toque. */
    private fun atras() {
        if (vistaCompleta != null) {
            salirPantallaCompleta()
            return
        }
        web.evaluateJavascript("(window.App && App.atras) ? App.atras() : false") { r ->
            if (r != "true") {
                val ahora = System.currentTimeMillis()
                if (ahora - ultimoAtras < 2000) {
                    finish()
                } else {
                    ultimoAtras = ahora
                    Toast.makeText(this, "Presiona atrás otra vez para salir", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun salirPantallaCompleta() {
        val v = vistaCompleta ?: return
        (window.decorView as ViewGroup).removeView(v)
        vistaCompleta = null
        callbackCompleta?.onCustomViewHidden()
        callbackCompleta = null
        WindowCompat.getInsetsController(window, raiz).show(WindowInsetsCompat.Type.systemBars())
    }

    private inner class Cliente : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            // Los marcos internos (iframes) navegan libremente; la página principal nunca se cambia.
            if (!request.isForMainFrame) return false
            if (url.startsWith("file:///android_asset/")) return false
            // Cualquier otro intento de cambiar la página principal se abre fuera de la app.
            Puente.abrirFuera(this@MainActivity, Uri.parse(url))
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val host = request.url.host?.lowercase() ?: return null
            if (BLOQUEADOS.any { host == it || host.endsWith(".$it") }) {
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }
    }

    private inner class ClienteChrome : WebChromeClient() {
        // Pantalla completa para videos dentro de un iframe
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (vistaCompleta != null) {
                callback.onCustomViewHidden()
                return
            }
            vistaCompleta = view
            callbackCompleta = callback
            view.setBackgroundColor(Color.BLACK)
            (window.decorView as ViewGroup).addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
            WindowCompat.getInsetsController(window, raiz).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        override fun onHideCustomView() = salirPantallaCompleta()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val INICIO = "file:///android_asset/www/index.html"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private val COLOR_FONDO = Color.parseColor("#0A0D12")

        /** Dominios de anuncios y rastreo que nunca se cargan. */
        private val BLOQUEADOS = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
            "googletagmanager.com", "adservice.google.com", "adnxs.com", "adsrvr.org", "taboola.com",
            "outbrain.com", "exoclick.com", "popads.net", "popcash.net", "propellerads.com",
            "adsterra.com", "a-ads.com", "adcash.com", "hilltopads.net", "juicyads.com",
            "trafficjunky.net", "onclickads.net", "mgid.com", "revcontent.com", "zedo.com",
            "scorecardresearch.com", "facebook.net", "hotjar.com", "yandex.ru", "mc.yandex.ru"
        )
    }
}
