package com.mystreamapp.app.proveedores

/**
 * Lo que se quiere ver. Viene de los `parametros` que ya manda `ver()` en
 * app.js a `Nativo.consultarProveedor(id, parametros, callbackId)`:
 * `{ tmdb, imdb, mal, season, episode, title, type }`.
 */
data class InfoContenido(
    val tipo: String,              // "movie" | "series" | "anime" (igual que "category")
    val tmdb: String = "",
    val imdb: String = "",
    val mal: String = "",
    val temporada: Int = 1,
    val episodio: Int = 1,
    val titulo: String = ""
) {
    /** Comodines disponibles en las URLs de `pasos` (ver docs/PROVEEDORES.md). */
    fun comodines(): Map<String, String> = mapOf(
        "tmdb" to tmdb,
        "imdb" to imdb,
        "mal" to mal.ifBlank { tmdb },
        "temporada" to temporada.toString(),
        "episodio" to episodio.toString(),
        "titulo" to titulo,
        "tipo" to tipo
    )
}

/**
 * Un enlace de video encontrado. La forma es la que ya espera `ver()` en
 * app.js para cada resultado: `{ providerName, url, headers, language,
 * subtitles, score }`.
 */
data class ResultadoProveedor(
    val providerName: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val language: String = "",
    val subtitles: Boolean = false,
    val score: Double = 0.0,
    /** URL de un .vtt/.srt visto por el Sniffer mientras buscaba el video (vacío si no vio ninguno). */
    val subtitleUrl: String = ""
)

/**
 * Codifica un texto como lo hace `encodeURIComponent` en JavaScript
 * (deja sin escapar A-Z a-z 0-9 - _ . ! ~ * ' ( ), todo lo demás va en %XX).
 * `java.net.URLEncoder` no sirve igual: convierte espacios en "+" y escapa
 * caracteres que encodeURIComponent no escapa.
 */
internal fun codificarURI(texto: String): String {
    val sinEscapar = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '_', '.', '!', '~', '*', '\'', '(', ')')
    val sb = StringBuilder()
    for (b in texto.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xFF
        if (c < 128 && c.toChar() in sinEscapar) sb.append(c.toChar())
        else sb.append('%').append("%02X".format(c))
    }
    return sb.toString()
}

/** true si la URL parece un video directo (m3u8/mpd/mp4/...) y no una página web. */
internal fun esVideoDirecto(url: String): Boolean =
    Regex("\\.(m3u8|mpd|mp4|mkv|webm|ts)(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)

/**
 * Puntaje por defecto según idioma, para que "Elige una fuente" ordene con
 * español latino primero, luego español de España, y el idioma original
 * subtitulado como alternativa. Un proveedor puede fijar su propio `score`
 * en el JSON para no depender de esto.
 */
internal fun puntajeIdioma(idioma: String, subtitulado: Boolean): Int {
    val i = idioma.trim().lowercase()
    return when {
        i == "es-es" || i == "es_es" || i == "es-esp" -> 70
        i.startsWith("es") -> 100   // es, es-la, es-419, es-mx, es-ar... (español ≠ España)
        subtitulado -> 40            // idioma original con subtítulos
        else -> 10
    }
}
