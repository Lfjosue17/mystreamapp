package com.mystreamapp.app

import android.app.Activity
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.mystreamapp.app.proveedores.InfoContenido
import com.mystreamapp.app.proveedores.ReglasProveedor
import com.mystreamapp.app.proveedores.RepositorioProveedores
import com.mystreamapp.app.proveedores.ResultadoProveedor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Funciones de Android que la interfaz web puede usar con `Nativo.<función>()`.
 * Todo lo que se guarda queda en la memoria interna de la app (no sale del teléfono).
 */
class Puente(private val act: Activity, private val web: WebView) {

    private val hilos = Executors.newFixedThreadPool(4)
    private val carpeta = File(act.filesDir, "datos").apply { mkdirs() }

    // Para el motor de proveedores: corre en segundo plano (IO).
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---------------- Guardado local ----------------

    private fun archivo(clave: String) = File(carpeta, clave.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    @JavascriptInterface
    fun leer(clave: String): String? {
        val f = archivo(clave)
        return if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    @JavascriptInterface
    fun guardar(clave: String, valor: String) {
        val f = archivo(clave)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(valor, Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    @JavascriptInterface
    fun borrar(clave: String) {
        archivo(clave).delete()
    }

    // ---------------- Información ----------------

    @JavascriptInterface
    fun tmdbKey(): String = BuildConfig.TMDB_API_KEY

    @JavascriptInterface
    fun version(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun esTV(): Boolean {
        val um = act.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return um.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            !act.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    @JavascriptInterface
    fun aviso(texto: String) {
        act.runOnUiThread { Toast.makeText(act, texto, Toast.LENGTH_SHORT).show() }
    }

    // ---------------- Red ----------------

    /**
     * Descarga un texto (lista M3U, JSON...) sin las restricciones de CORS del navegador.
     * Cuando termina llama a `App.red._fin(id, exito, contenido)` en la interfaz web.
     */
    @JavascriptInterface
    fun descargar(id: String, url: String, cabecerasJson: String?) {
        hilos.execute {
            val (ok, contenido) = try {
                true to bajar(url, leerCabeceras(cabecerasJson))
            } catch (e: Exception) {
                false to (e.message ?: e.javaClass.simpleName)
            }
            val js = "App.red._fin(${JSONObject.quote(id)}, $ok, ${JSONObject.quote(contenido)})"
            act.runOnUiThread { web.evaluateJavascript(js, null) }
        }
    }

    private fun bajar(direccion: String, cab: Map<String, String>): String {
        var actual = direccion
        repeat(6) {
            val c = (URL(actual).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", cab["User-Agent"] ?: UA)
                setRequestProperty("Accept", "*/*")
                cab.forEach { (k, v) -> if (!k.equals("User-Agent", true)) setRequestProperty(k, v) }
            }
            try {
                val codigo = c.responseCode
                if (codigo in 300..399) {
                    val destino = c.getHeaderField("Location") ?: throw IOException("Redirección sin destino")
                    actual = URL(URL(actual), destino).toString()
                    return@repeat
                }
                if (codigo !in 200..299) throw IOException("HTTP $codigo")
                return c.inputStream.use { leerLimitado(it) }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Demasiadas redirecciones")
    }

    private fun leerLimitado(entrada: InputStream): String {
        val salida = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = entrada.read(buf)
            if (n < 0) break
            total += n
            if (total > LIMITE_BYTES) throw IOException("El archivo es demasiado grande (más de 40 MB)")
            salida.write(buf, 0, n)
        }
        return salida.toString(Charsets.UTF_8.name())
    }

    // ---------------- Reproducción ----------------

    /** Abre el reproductor nativo (ExoPlayer) a pantalla completa. */
    @JavascriptInterface
    fun reproducir(url: String, titulo: String?, cabecerasJson: String?) {
        val i = Intent(act, ReproductorActivity::class.java).apply {
            putExtra(ReproductorActivity.EXTRA_URL, url)
            putExtra(ReproductorActivity.EXTRA_TITULO, titulo ?: "")
            putExtra(ReproductorActivity.EXTRA_CABECERAS, cabecerasJson ?: "{}")
        }
        act.runOnUiThread { act.startActivity(i) }
    }

    /** Abre un enlace en otra app (YouTube, navegador...). */
    @JavascriptInterface
    fun abrirExterno(url: String) {
        act.runOnUiThread { abrirFuera(act, Uri.parse(url)) }
    }

    // ---------------- Proveedores de video ----------------

    /**
     * Busca fuentes para UN proveedor (por `id`): el motor JSON local, o tu
     * servidor remoto si `id` es [RepositorioProveedores.ID_SERVIDOR_REMOTO].
     * `parametrosJson` es `{ tmdb, imdb, mal, season, episode, title, type }`,
     * igual a lo que ya manda `ver()` en app.js. Responde a
     * `App.proveedores._fin(callbackId, ok, resultadoJson)`, donde cada
     * resultado es `{ providerName, url, headers, language, subtitles, score }`.
     */
    @JavascriptInterface
    fun consultarProveedor(id: String, parametrosJson: String, callbackId: String) {
        alcance.launch {
            val (ok, resultado) = try {
                val info = infoDesdeParametros(parametrosJson)
                val config = JSONObject(leer("config") ?: "{}")
                val resultados = RepositorioProveedores(act).resolver(config, id, info)
                true to resultadosAJson(resultados)
            } catch (e: Exception) {
                false to (e.message ?: e.javaClass.simpleName)
            }
            val js = "App.proveedores._fin(${JSONObject.quote(callbackId)}, $ok, ${JSONObject.quote(resultado)})"
            withContext(Dispatchers.Main) { web.evaluateJavascript(js, null) }
        }
    }

    /**
     * Valida (sin red) el JSON que se pega en Ajustes → Motor de proveedores.
     * Devuelve "OK" si está bien, o "ERROR: <motivo>" si no (así lo espera
     * `guardar-proveedores` en app.js).
     */
    @JavascriptInterface
    fun validarProveedores(json: String): String {
        return try {
            val o = JSONObject(json)
            if (o.optInt("version", 0) != 1) return "ERROR: falta \"version\": 1"
            val arr = o.optJSONArray("providers") ?: return "ERROR: falta la lista \"providers\""
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: return "ERROR: el proveedor #${i + 1} no es un objeto válido"
                val id = p.optString("id")
                if (id.isBlank()) return "ERROR: el proveedor #${i + 1} no tiene \"id\""
                if (p.optString("category").isBlank()) return "ERROR: el proveedor \"$id\" no tiene \"category\""
                val pasos = p.optJSONArray("pasos")
                if (pasos == null || pasos.length() == 0) return "ERROR: el proveedor \"$id\" no tiene \"pasos\""
                for (j in 0 until pasos.length()) {
                    val paso = pasos.optJSONObject(j) ?: return "ERROR: el paso ${j + 1} de \"$id\" no es un objeto válido"
                    val regex = paso.optString("regex")
                    if (regex.isNotBlank()) {
                        try { Regex(regex) } catch (e: Exception) {
                            return "ERROR: el regex del paso ${j + 1} de \"$id\" no es válido"
                        }
                    }
                }
                // Si pasa esto, ReglasProveedor también debería poder leerlo sin romperse.
                ReglasProveedor.desdeJson(p)
            }
            "OK"
        } catch (e: Exception) {
            "ERROR: " + (e.message ?: "JSON no válido")
        }
    }

    private fun infoDesdeParametros(json: String): InfoContenido {
        val o = JSONObject(json)
        return InfoContenido(
            tipo = o.optString("type", "movie"),
            tmdb = o.optString("tmdb"),
            imdb = o.optString("imdb"),
            mal = o.optString("mal"),
            temporada = o.optInt("season", 1),
            episodio = o.optInt("episode", 1),
            titulo = o.optString("title")
        )
    }

    private fun resultadosAJson(resultados: List<ResultadoProveedor>): String {
        val arr = JSONArray()
        resultados.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("providerName", r.providerName)
                    put("url", r.url)
                    put("headers", JSONObject(r.headers))
                    put("language", r.language)
                    put("subtitles", r.subtitles)
                    put("score", r.score)
                }
            )
        }
        return arr.toString()
    }

    companion object {
        private const val LIMITE_BYTES = 40 * 1024 * 1024
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

        fun leerCabeceras(json: String?): Map<String, String> {
            if (json.isNullOrBlank()) return emptyMap()
            return try {
                val o = JSONObject(json)
                o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotBlank() }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        fun abrirFuera(ctx: Context, uri: Uri) {
            val esquema = uri.scheme?.lowercase()
            if (esquema != "http" && esquema != "https") return
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(ctx, "No hay una app para abrir este enlace", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
