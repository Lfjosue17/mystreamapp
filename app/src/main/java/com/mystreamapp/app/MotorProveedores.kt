package com.mystreamapp.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URL
import java.util.regex.Pattern

/** Motor local de proveedores configurados por JSON. No necesita servidor propio. */
class MotorProveedores {
    private val proveedorHtml: ProveedorContenido = ProveedorHtmlReglas()

    fun consultar(configuracion: String, proveedorId: String, parametrosJson: String): String {
        val config = JSONObject(configuracion)
        require(config.optInt("version", 1) == 1) { "Versión de configuración de proveedores no compatible" }
        val lista = config.optJSONArray("providers") ?: JSONArray()
        val definicion = (0 until lista.length()).asSequence()
            .map { lista.optJSONObject(it) }
            .filterNotNull()
            .firstOrNull { it.optString("id") == proveedorId }
            ?: throw IllegalArgumentException("No existe el proveedor: $proveedorId")
        if (!definicion.optBoolean("enabled", true)) throw IllegalStateException("El proveedor está desactivado")
        val resultados = proveedorHtml.consultar(definicion, JSONObject(parametrosJson))
        return JSONArray().apply { resultados.forEach { put(it.aJson()) } }.toString()
    }

    /** Valida la estructura antes de guardarla desde Ajustes. */
    fun validar(configuracion: String): String {
        val root = JSONObject(configuracion)
        require(root.optInt("version", 1) == 1) { "La versión debe ser 1" }
        val providers = root.optJSONArray("providers") ?: throw IllegalArgumentException("Falta el arreglo providers")
        val ids = mutableSetOf<String>()
        for (i in 0 until providers.length()) {
            val p = providers.optJSONObject(i) ?: throw IllegalArgumentException("providers[$i] no es un objeto")
            val id = p.optString("id").trim()
            require(id.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) { "ID inválido en providers[$i]" }
            require(ids.add(id)) { "ID duplicado: $id" }
            require(p.optString("name").isNotBlank()) { "Falta name en $id" }
            require(p.optString("type", "html") == "html") { "Solo se admite type=html por ahora ($id)" }
            require(p.optString("category") in setOf("movie", "series", "anime", "sports")) { "category debe ser movie, series, anime o sports ($id)" }
            val url = p.optString("urlTemplate")
            require(url.startsWith("https://") || url.startsWith("http://")) { "urlTemplate debe comenzar con http(s) ($id)" }
            val extract = p.optJSONObject("extract") ?: throw IllegalArgumentException("Falta extract en $id")
            require(extract.optString("mode") in setOf("css", "regex")) { "extract.mode debe ser css o regex ($id)" }
            if (extract.optString("mode") == "css") require(extract.optString("selector").isNotBlank()) { "Falta selector CSS en $id" }
            if (extract.optString("mode") == "regex") require(extract.optString("pattern").isNotBlank()) { "Falta pattern en $id" }
        }
        return "OK: ${providers.length()} proveedor(es)"
    }
}

private class ProveedorHtmlReglas : ProveedorContenido {
    override fun consultar(definicion: JSONObject, parametros: JSONObject): List<FuenteEncontrada> {
        val url = expandir(definicion.getString("urlTemplate"), parametros)
        val uri = Uri.parse(url)
        require(uri.scheme == "http" || uri.scheme == "https") { "Solo se permiten URLs HTTP/HTTPS" }
        val cabeceras = objetoAMapa(definicion.optJSONObject("headers"))
        val timeout = definicion.optInt("timeoutMs", 15000).coerceIn(3000, 30000)
        val respuesta = Jsoup.connect(url)
            .userAgent(cabeceras["User-Agent"] ?: Puente.UA)
            .headers(cabeceras.filterKeys { !it.equals("User-Agent", true) })
            .timeout(timeout)
            .maxBodySize(2 * 1024 * 1024)
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .execute()
        require(respuesta.statusCode() in 200..299) { "HTTP ${respuesta.statusCode()}" }

        val base = respuesta.url().toString()
        val html = respuesta.body()
        val extract = definicion.getJSONObject("extract")
        val candidatos = when (extract.getString("mode")) {
            "css" -> {
                val doc = Jsoup.parse(html, base)
                val selector = extract.getString("selector")
                val atributo = extract.optString("attribute", "src").ifBlank { "src" }
                doc.select(selector).mapNotNull { elemento ->
                    val valor = if (atributo == "text") elemento.text() else elemento.attr(atributo)
                    valor.takeIf { it.isNotBlank() }
                }
            }
            "regex" -> extraerRegex(html, extract.getString("pattern"), extract.optInt("group", 1))
            else -> emptyList()
        }

        val patronFiltro = extract.optString("streamPattern")
            .ifBlank { "(?i)\\.(m3u8|mpd|mp4)(?:[?#].*)?$" }
        val filtro = Pattern.compile(patronFiltro)
        val reglasIdioma = definicion.optJSONObject("language")?.optJSONArray("rules") ?: JSONArray()
        val resultados = linkedMapOf<String, FuenteEncontrada>()
        for (bruto in candidatos) {
            val limpio = limpiarCandidato(bruto) ?: continue
            val absoluta = try { URL(URL(base), limpio).toString() } catch (_: Exception) { continue }
            if (!filtro.matcher(absoluta).find()) continue
            val textoPagina = Jsoup.parse(html).text()
            val idioma = detectarIdioma(bruto + " " + absoluta + " " + textoPagina, definicion.optJSONObject("language"), reglasIdioma)
            val subtitulos = definicion.optJSONObject("language")?.optString("subtitlesPattern")
                ?.takeIf { it.isNotBlank() }?.let { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(html) } ?: false
            val puntuacion = when (idioma) {
                "es-419" -> 300
                "es-ES" -> 200
                "original" -> 100 + if (subtitulos) 30 else 0
                else -> 0
            }
            resultados.putIfAbsent(absoluta, FuenteEncontrada(
                proveedorId = definicion.getString("id"),
                proveedorNombre = definicion.getString("name"),
                categoria = definicion.getString("category"),
                url = absoluta,
                cabeceras = cabeceras,
                idioma = idioma,
                subtitulos = subtitulos,
                puntuacion = puntuacion
            ))
        }
        return resultados.values.sortedByDescending { it.puntuacion }
    }

    private fun extraerRegex(texto: String, patron: String, grupo: Int): List<String> {
        val matcher = Pattern.compile(patron, Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(texto)
        val salida = mutableListOf<String>()
        while (matcher.find() && salida.size < 100) {
            val indice = grupo.coerceIn(0, matcher.groupCount())
            val valor = matcher.group(indice)
            if (!valor.isNullOrBlank()) salida.add(valor)
        }
        return salida
    }

    private fun limpiarCandidato(valor: String): String? {
        var v = valor.trim().replace("\\/", "/").replace("&amp;", "&")
        if (v.startsWith("//")) v = "https:$v"
        if (!(v.startsWith("http://") || v.startsWith("https://") || v.startsWith("/"))) return null
        return v
    }

    private fun detectarIdioma(texto: String, config: JSONObject?, reglas: JSONArray): String {
        for (i in 0 until reglas.length()) {
            val regla = reglas.optJSONObject(i) ?: continue
            val patron = regla.optString("pattern")
            if (patron.isNotBlank() && Regex(patron, RegexOption.IGNORE_CASE).containsMatchIn(texto)) {
                return regla.optString("code", "und")
            }
        }
        return config?.optString("default", "und") ?: "und"
    }

    private fun expandir(plantilla: String, p: JSONObject): String {
        val claves = listOf("tmdb", "imdb", "mal", "season", "episode", "title", "type", "event", "league", "date", "home", "away")
        var salida = plantilla
        claves.forEach { k -> salida = salida.replace("{$k}", Uri.encode(p.optString(k))) }
        return salida
    }

    private fun objetoAMapa(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val salida = linkedMapOf<String, String>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = o.optString(k)
            if (v.isNotBlank() && k.matches(Regex("[A-Za-z0-9-]{1,64}")) &&
                !k.equals("Host", true) && !k.equals("Content-Length", true) && !k.equals("Connection", true)) {
                salida[k] = v.take(2048)
            }
        }
        return salida
    }
}
