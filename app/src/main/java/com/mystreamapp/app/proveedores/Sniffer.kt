package com.mystreamapp.app.proveedores

import android.app.Activity
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mystreamapp.app.Puente
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Lo que encontró el Sniffer: el enlace directo y las cabeceras para pedirlo. */
data class ResultadoSniff(val url: String, val headers: Map<String, String>)

/**
 * Carga una página en un WebView oculto y mira las peticiones de red hasta
 * encontrar un enlace de video directo (.m3u8/.mpd/.mp4). Sirve para sitios
 * que generan el enlace real con JavaScript y no aparece en el HTML crudo
 * (por eso el motor JSON con Jsoup/Regex solo no alcanza en esos casos).
 */
object Sniffer {
    private val PATRON_VIDEO = Regex("\\.(m3u8|mpd|mp4)(\\?|$)", RegexOption.IGNORE_CASE)

    /** Debe llamarse desde cualquier hilo; internamente usa el hilo principal para el WebView. */
    suspend fun buscar(
        act: Activity,
        url: String,
        cabeceras: Map<String, String>,
        tiempoMaximoMs: Long = 12_000
    ): ResultadoSniff? = withTimeoutOrNull(tiempoMaximoMs) {
        suspendCancellableCoroutine { cont ->
            val resuelto = AtomicBoolean(false)
            var web: WebView? = null

            fun terminar(r: ResultadoSniff?) {
                if (!resuelto.compareAndSet(false, true)) return
                act.runOnUiThread {
                    web?.let { w ->
                        w.stopLoading()
                        (w.parent as? ViewGroup)?.removeView(w)
                        w.destroy()
                    }
                }
                if (cont.isActive) cont.resume(r)
            }

            cont.invokeOnCancellation { terminar(null) }

            act.runOnUiThread {
                val w = WebView(act)
                web = w
                w.settings.javaScriptEnabled = true
                w.settings.domStorageEnabled = true
                w.settings.mediaPlaybackRequiresUserGesture = false
                val ua = cabeceras["User-Agent"]
                if (!ua.isNullOrBlank()) w.settings.userAgentString = ua

                w.webViewClient = object : WebViewClient() {
                    // Atención: Android llama esto en un hilo que NO es el principal.
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val u = request.url.toString()
                        if (PATRON_VIDEO.containsMatchIn(u)) {
                            val refererPeticion = request.requestHeaders.entries
                                .firstOrNull { it.key.equals("Referer", true) }?.value
                            val headersFinal = mapOf(
                                "User-Agent" to (ua ?: Puente.UA),
                                "Referer" to (refererPeticion ?: cabeceras["Referer"] ?: url)
                            )
                            terminar(ResultadoSniff(u, headersFinal))
                        }
                        return null
                    }
                }

                // El WebView necesita estar "adjunto" a una ventana para cargar de forma confiable.
                // Se usa un tamaño de 1x1 (en vez de 0x0) para evitar que alguna página no cargue
                // recursos por pensar que el WebView no es visible.
                w.visibility = android.view.View.INVISIBLE
                val raiz = act.window.decorView as ViewGroup
                raiz.addView(w, 1, 1)
                val headersCarga = cabeceras.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
                w.loadUrl(url, headersCarga)
            }
        }
    }
}
