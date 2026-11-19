package com.mystreamapp.app.proveedores

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente HTTP hacia tu instancia de TMDB-Embed-API (Parte B, servidor propio).
 * Manda la API Key en cabeceras y espera un JSON con las fuentes, ya en la
 * misma forma que usa el resto del motor: `{ providerName, url, headers,
 * language, subtitles, score }`.
 *
 * El endpoint y la forma exacta de la respuesta son un punto de partida: hay
 * que ajustarlos cuando definamos el contrato de la Parte B.
 */
class ClienteEmbed(
    private val urlBase: String,
    private val apiKey: String
) : MotorProveedor {

    override suspend fun buscar(info: InfoContenido): List<ResultadoProveedor> {
        if (urlBase.isBlank() || apiKey.isBlank()) return emptyList()
        val base = urlBase.trimEnd('/')
        val q = buildString {
            append("tipo=").append(URLEncoder.encode(info.tipo, "UTF-8"))
            append("&tmdb=").append(URLEncoder.encode(info.tmdb, "UTF-8"))
            if (info.tipo != "movie") {
                append("&temporada=").append(info.temporada)
                append("&episodio=").append(info.episodio)
            }
            if (info.mal.isNotBlank()) append("&mal=").append(URLEncoder.encode(info.mal, "UTF-8"))
        }
        val conexion = (URL("$base/api/fuentes?$q").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
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
        val arr: JSONArray = raiz.optJSONArray("fuentes") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val idioma = o.optString("language")
            val subtitulado = o.optBoolean("subtitles", false)
            val cabeceras = mutableMapOf<String, String>()
            o.optJSONObject("headers")?.let { h -> h.keys().forEach { k -> cabeceras[k] = h.optString(k) } }
            ResultadoProveedor(
                providerName = o.optString("providerName", "Mi servidor"),
                url = url,
                headers = cabeceras,
                language = idioma,
                subtitles = subtitulado,
                score = o.optDouble("score", 0.0).let { if (it > 0) it else puntajeIdioma(idioma, subtitulado).toDouble() }
            )
        }
    }
}
