package com.mystreamapp.app

import android.graphics.Color
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
                ".m3u8" in u || "/hls/" in u -> setMimeType(MimeTypes.APPLICATION_M3U8)
                ".mpd" in u -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
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
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
