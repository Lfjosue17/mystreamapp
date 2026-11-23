package com.mystreamapp.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reproductor a pantalla completa para canales y enlaces directos (m3u8, mpd, mp4...).
 * Acepta cabeceras como User-Agent y Referer, que muchas listas M3U necesitan.
 */
@OptIn(UnstableApi::class)
class ReproductorActivity : ComponentActivity() {

    private var reproductor: ExoPlayer? = null
    private lateinit var vista: PlayerView
    private lateinit var mensaje: TextView
    private var reintentos = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val raiz = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        vista = PlayerView(this).apply {
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            keepScreenOn = true
        }
        mensaje = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            visibility = TextView.GONE
        }
        raiz.addView(vista, FrameLayout.LayoutParams(MATCH, MATCH))
        raiz.addView(mensaje, FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(raiz)

        WindowCompat.getInsetsController(window, raiz).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        iniciar()
    }

    override fun onStop() {
        super.onStop()
        reproductor?.release()
        reproductor = null
    }

    private fun iniciar() {
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) {
            mostrarMensaje("No hay enlace para reproducir")
            return
        }
        val cab = Puente.leerCabeceras(intent.getStringExtra(EXTRA_CABECERAS)).toMutableMap()
        val ua = cab.remove("User-Agent") ?: Puente.UA

        val tmdb = intent.getStringExtra(EXTRA_TMDB).orEmpty()
        val tipoContenido = intent.getStringExtra(EXTRA_TIPO_CONTENIDO).orEmpty()
        val temporada = intent.getIntExtra(EXTRA_TEMPORADA, 0)
        val episodio = intent.getIntExtra(EXTRA_EPISODIO, 0)
        val idioma = intent.getStringExtra(EXTRA_IDIOMA).orEmpty()
        val subtituloDirecto = intent.getStringExtra(EXTRA_SUBTITULO_URL).orEmpty()

        // Si el Sniffer ya vio pasar un .vtt/.srt para esta fuente, se usa de una vez; si no,
        // se consulta Wyzie Subs como respaldo (con tope de 4s para no demorar la reproducción).
        lifecycleScope.launch {
            val subtitulo = when {
                subtituloDirecto.isNotBlank() -> subtituloDesdeUrl(subtituloDirecto, idioma)
                tmdb.isNotBlank() -> buscarSubtituloWyzie(tmdb, tipoContenido, temporada, episodio, idioma)
                else -> null
            }
            prepararYReproducir(url, cab, ua, subtitulo)
        }
    }

    /** Arma la configuración de subtítulo para ExoPlayer a partir de un .vtt/.srt ya conocido. */
    private fun subtituloDesdeUrl(subUrl: String, idioma: String): MediaItem.SubtitleConfiguration {
        // Si ya parece doblaje en español, el subtítulo queda disponible pero no se activa solo;
        // si el audio parece inglés/original, se activa por defecto.
        val esDoblajeEspanol = Regex("latino|castellano|espa[nñ]ol", RegexOption.IGNORE_CASE)
            .containsMatchIn(idioma)
        val mime = if (subUrl.endsWith(".srt", ignoreCase = true)) {
            MimeTypes.APPLICATION_SUBRIP
        } else {
            MimeTypes.TEXT_VTT
        }
        return MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
            .setMimeType(mime)
            .setLanguage("es")
            .setLabel("Español")
            .setSelectionFlags(if (esDoblajeEspanol) 0 else C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    /** Consulta la API gratuita de Wyzie Subs y devuelve un subtítulo en español listo para ExoPlayer, o null. */
    private suspend fun buscarSubtituloWyzie(
        tmdb: String,
        tipo: String,
        temporada: Int,
        episodio: Int,
        idioma: String
    ): MediaItem.SubtitleConfiguration? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(4_000) {
            try {
                var url = "https://sub.wyzie.ru/search?id=" + Uri.encode(tmdb) + "&language=es"
                if (tipo == "tv" && temporada > 0 && episodio > 0) {
                    url += "&season=$temporada&episode=$episodio"
                }
                val con = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3_500
                    readTimeout = 3_500
                    setRequestProperty("User-Agent", Puente.UA)
                    setRequestProperty("Accept", "application/json")
                }
                val texto = try {
                    con.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    con.disconnect()
                }
                val arr = JSONArray(texto)
                if (arr.length() == 0) return@withTimeoutOrNull null
                val primero = arr.optJSONObject(0) ?: return@withTimeoutOrNull null
                val subUrl = primero.optString("url").ifBlank { primero.optString("link") }
                if (subUrl.isBlank()) return@withTimeoutOrNull null
                subtituloDesdeUrl(subUrl, idioma)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun prepararYReproducir(
        url: String,
        cab: MutableMap<String, String>,
        ua: String,
        subtitulo: MediaItem.SubtitleConfiguration?
    ) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(cab)
        val fabrica = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))

        val item = MediaItem.Builder().setUri(url).apply {
            val u = url.lowercase()
            when {
                "m3u8" in u || "hls" in u || "master" in u -> setMimeType(MimeTypes.APPLICATION_M3U8)
                ".mpd" in u -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
            if (subtitulo != null) setSubtitleConfigurations(listOf(subtitulo))
        }.build()

        val p = ExoPlayer.Builder(this).setMediaSourceFactory(fabrica).build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Las transmisiones en vivo a veces se quedan atrás: se reintenta desde el punto en vivo.
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW || reintentos < 1) {
                    reintentos++
                    p.seekToDefaultPosition()
                    p.prepare()
                    return
                }
                mostrarMensaje("No se pudo reproducir este enlace.\n(${error.errorCodeName})\nPresiona atrás para volver.")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) mensaje.visibility = TextView.GONE
            }
        })
        vista.player = p
        p.setMediaItem(item)
        p.playWhenReady = true
        p.prepare()
        reproductor = p

        val titulo = intent.getStringExtra(EXTRA_TITULO).orEmpty()
        if (titulo.isNotBlank()) {
            android.widget.Toast.makeText(this, titulo, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarMensaje(texto: String) {
        mensaje.text = texto
        mensaje.visibility = TextView.VISIBLE
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITULO = "titulo"
        const val EXTRA_CABECERAS = "cabeceras"
        const val EXTRA_TMDB = "tmdb"
        const val EXTRA_TIPO_CONTENIDO = "tipoContenido"
        const val EXTRA_TEMPORADA = "temporada"
        const val EXTRA_EPISODIO = "episodio"
        const val EXTRA_IDIOMA = "idioma"
        const val EXTRA_SUBTITULO_URL = "subtituloUrl"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
