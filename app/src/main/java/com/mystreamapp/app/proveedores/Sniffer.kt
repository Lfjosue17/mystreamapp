package com.mystreamapp.app.proveedores

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mystreamapp.app.Puente
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Lo que encontró el Sniffer: el enlace directo, sus cabeceras y, si vio pasar uno, un subtítulo. */
data class ResultadoSniff(
    val url: String,
    val headers: Map<String, String>,
    val subtitleUrl: String? = null
)

/**
 * Carga una página en un WebView oculto y mira las peticiones de red hasta
 * encontrar un enlace de video directo (.m3u8/.mpd/.mp4). Sirve para sitios
 * que generan el enlace real con JavaScript y no aparece en el HTML crudo
 * (por eso el motor JSON con Jsoup/Regex solo no alcanza en esos casos).
 */
object Sniffer {
    private const val TAG = "Sniffer"

    private val PATRON_VIDEO = Regex("\\.(m3u8|mpd|mp4)(\\?|$)", RegexOption.IGNORE_CASE)
    private val PATRON_SUBTITULO = Regex("\\.(vtt|srt)(\\?|$)", RegexOption.IGNORE_CASE)

    // Recursos que nunca son el stream real: se descartan sin gastar una petición extra de verificación.
    private val EXTENSIONES_IGNORADAS = Regex(
        "\\.(html?|php[0-9]?|aspx?|jsp|js|mjs|css|png|jpe?g|gif|webp|bmp|svg|ico|woff2?|ttf|eot|json|map|txt)(\\?|#|$)",
        RegexOption.IGNORE_CASE
    )
    private val DOMINIOS_ANUNCIOS = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagmanager.com", "adservice.google.com", "adnxs.com", "adsrvr.org", "taboola.com",
        "outbrain.com", "exoclick.com", "popads.net", "popcash.net", "propellerads.com",
        "adsterra.com", "a-ads.com", "adcash.com", "hilltopads.net", "juicyads.com",
        "trafficjunky.net", "onclickads.net", "mgid.com", "revcontent.com"
    )

    private fun esAnuncio(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return DOMINIOS_ANUNCIOS.any { h == it || h.endsWith(".$it") }
    }

    /** true si el Content-Type ya deja claro que esto NO es un video (HTML/JSON/XML de error, etc). */
    private fun esClaramenteNoVideo(tipo: String?): Boolean {
        if (tipo == null) return false
        return tipo.startsWith("text/") || tipo == "application/json" || tipo.startsWith("application/xml")
    }

    /**
     * Pide solo el primer byte (Range) para leer el Content-Type real sin bajar el archivo entero.
     * Si la petición falla (bloqueo, timeout, red), devuelve null: se asume "no claro" y se deja pasar
     * (evita descartar por error un enlace que sí era válido).
     */
    private fun contentTypeReal(url: String, cabeceras: Map<String, String>): String? = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            instanceFollowRedirects = true
            setRequestProperty("Range", "bytes=0-0")
            cabeceras.forEach { (k, v) -> setRequestProperty(k, v) }
        }.let { c ->
            try {
                c.contentType?.substringBefore(';')?.trim()?.lowercase()
            } finally {
                c.disconnect()
            }
        }
    } catch (e: Exception) {
        null
    }

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
            var subtituloVisto: String? = null

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
                    override fun onPageFinished(view: WebView, cargada: String?) {
                        super.onPageFinished(view, cargada)
                        // Algunos servidores solo sueltan el stream real tras una interacción del
                        // usuario (clic en el botón de play). Se simula uno, dando tiempo a que la
                        // página termine de armar su reproductor.
                        view.postDelayed({
                            if (!resuelto.get()) {
                                view.evaluateJavascript(
                                    "(function(){try{" +
                                        "var cx=window.innerWidth/2, cy=window.innerHeight/2;" +
                                        "var el=document.elementFromPoint(cx,cy);" +
                                        "if(el){['mousedown','mouseup','click'].forEach(function(t){" +
                                        "try{el.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window,clientX:cx,clientY:cy}));}catch(e){}" +
                                        "});}" +
                                        "var v=document.querySelector(" +
                                        "'video,.jw-icon-playback,.vjs-big-play-button,.plyr__control--overlaid,#play,.play-button,.play,[class*=play-btn]');" +
                                        "if(v){if(v.tagName==='VIDEO'){try{v.muted=true;v.play();}catch(e){}} else if(v.click){try{v.click();}catch(e){}}}" +
                                        "}catch(e){}})();",
                                    null
                                )
                            }
                        }, 1_500)
                    }

                    // Atención: Android llama esto en un hilo que NO es el principal.
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        if (resuelto.get()) return null
                        val u = request.url.toString()
                        val host = request.url.host

                        if (PATRON_SUBTITULO.containsMatchIn(u)) {
                            subtituloVisto = u
                            Log.d(TAG, "Subtítulo visto: $u")
                        }

                        if (esAnuncio(host) || EXTENSIONES_IGNORADAS.containsMatchIn(u)) return null
                        if (!PATRON_VIDEO.containsMatchIn(u)) return null

                        // Verifica el Content-Type real antes de dar el enlace por bueno: evita el
                        // falso positivo de una URL que "parece" .m3u8/.mp4 pero en realidad devuelve
                        // una página de error en HTML o un JSON (causa típica de
                        // ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED en ExoPlayer).
                        val tipo = contentTypeReal(u, request.requestHeaders)
                        if (esClaramenteNoVideo(tipo)) {
                            Log.d(TAG, "Descartado (Content-Type $tipo, no es video): $u")
                            return null
                        }

                        Log.d(TAG, "Encontrado: $u (Content-Type: $tipo)")
                        val refererPeticion = request.requestHeaders.entries
                            .firstOrNull { it.key.equals("Referer", true) }?.value
                        val headersFinal = mapOf(
                            "User-Agent" to (ua ?: Puente.UA),
                            "Referer" to (refererPeticion ?: cabeceras["Referer"] ?: url)
                        )
                        terminar(ResultadoSniff(u, headersFinal, subtituloVisto))
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
