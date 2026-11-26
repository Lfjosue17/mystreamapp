package com.mystreamapp.app.proveedores

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente HTTP hacia tu instancia real de TMDB-Embed-API (Parte B).
 * Llama al endpoint agregado `GET /api/streams/:type/:tmdbId` (devuelve las
 * fuentes de TODOS los proveedores habilitados ya combinadas), mandando la
 * API Key en la cabecera `X-API-Key` (ver `utils/auth.js` → `requireApiKey`
 * en el servidor).
 *
 * `:type` ahí solo acepta "movie" o "series" (no "anime"): el anime se manda
 * como "series". Nota: para anime, `info.tmdb` en realidad trae el ID de
 * MyAnimeList (así lo manda `ver()` en app.js), no un ID de TMDB, así que el
 * servidor normalmente no encontrará nada para esa categoría salvo que tú
 * mismo agregues un proveedor que entienda ese ID.
 *
 * Forma de cada resultado en `streams[]` (ver README.md del servidor,
 * "Stream Object Schema"): `{ name, title, url, quality, provider, headers,
 * subtitles: [{url, lang}] }`. Los campos `language` (código corto, ej.
 * "es-419") y `originalSubtitled` (true/false) son una convención agregada
 * para ordenar por idioma (ver `utils/languageSort.js` del servidor); son
 * opcionales, así que un proveedor que no los declare simplemente queda al
 * final del orden.
 */
class ClienteEmbed(
    private val urlBase: String,
    private val apiKey: String
) : MotorProveedor {

    override suspend fun buscar(info: InfoContenido): List<ResultadoProveedor> {
        if (urlBase.isBlank() || apiKey.isBlank() || info.tmdb.isBlank()) return emptyList()
        val base = urlBase.trimEnd('/')
        val tipo = if (info.tipo == "movie") "movie" else "series"
        val id = URLEncoder.encode(info.tmdb, "UTF-8")
        val q = if (tipo == "series") "?season=${info.temporada}&episode=${info.episodio}" else ""
        val conexion = (URL("$base/api/streams/$tipo/$id$q").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 25_000
            setRequestProperty("X-API-Key", apiKey)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val codigo = conexion.responseCode
            if (codigo !in 200..299) emptyList()
            else parsear(conexion.inputStream.bufferedReader(Charsets.UTF_8).readText())
        } catch (e: Exception) {
            emptyList()
        } finally {
            conexion.disconnect()
        }
    }

    private fun parsear(texto: String): List<ResultadoProveedor> {
        val raiz = JSONObject(texto)
        if (!raiz.optBoolean("success", false)) return emptyList()
        val arr: JSONArray = raiz.optJSONArray("streams") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val idioma = o.optString("language")
            val subtitulado = o.optBoolean("originalSubtitled", false)
            val cabeceras = mutableMapOf<String, String>()
            o.optJSONObject("headers")?.let { h -> h.keys().forEach { k -> cabeceras[k] = h.optString(k) } }
            val nombre = o.optString("name").ifBlank { o.optString("provider") }.ifBlank { "Servidor remoto" }
            ResultadoProveedor(
                providerName = nombre,
                url = url,
                headers = cabeceras,
                language = idioma,
                subtitles = subtitulado,
                score = puntajeIdioma(idioma, subtitulado).toDouble()
            )
        }
    }
}
