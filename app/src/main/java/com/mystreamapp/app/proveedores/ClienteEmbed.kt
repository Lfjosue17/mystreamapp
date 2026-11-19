package com.mystreamapp.app.proveedores

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente HTTP hacia tu instancia de TMDB-Embed-API (Parte B, servidor propio).
 * Manda la API Key en cabeceras y espera un JSON con las fuentes, ya separadas
 * y ordenadas por idioma por el servidor.
 *
 * El endpoint y la forma exacta de la respuesta son un punto de partida: hay
 * que ajustarlos cuando definamos el contrato de la Parte B.
 */
class ClienteEmbed(
    private val urlBase: String,
    private val apiKey: String
) : MotorProveedor {

    override val nombre: String = "Mi servidor"

    override fun soporta(info: InfoContenido): Boolean = urlBase.isNotBlank() && apiKey.isNotBlank()

    override suspend fun buscar(info: InfoContenido): List<FuenteEncontrada> {
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

    private fun parsear(texto: String): List<FuenteEncontrada> {
        val raiz = JSONObject(texto)
        val arr: JSONArray = raiz.optJSONArray("fuentes") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FuenteEncontrada(
                nombre = o.optString("nombre", nombre),
                url = url,
                ua = o.optString("ua"),
                referer = o.optString("referer"),
                idioma = o.optString("idioma"),
                calidad = o.optString("calidad"),
                origen = nombre
            )
        }
    }
}
