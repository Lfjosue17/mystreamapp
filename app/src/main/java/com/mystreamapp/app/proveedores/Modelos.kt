package com.mystreamapp.app.proveedores

/**
 * Lo que se quiere ver. Viene de la ficha (TMDB/Jikan) en la interfaz web,
 * con los mismos datos que hoy usa `plantilla()` en app.js.
 */
data class InfoContenido(
    val tipo: String,              // "movie" | "tv" | "anime"
    val tmdb: String = "",
    val imdb: String = "",
    val mal: String = "",
    val temporada: Int = 1,
    val episodio: Int = 1,
    val titulo: String = ""
) {
    /** Comodines disponibles para plantillas de URL y reglas JSON. */
    fun comodines(): Map<String, String> = mapOf(
        "tmdb" to tmdb,
        "imdb" to imdb,
        "mal" to mal.ifBlank { tmdb },
        "temporada" to temporada.toString(),
        "episodio" to episodio.toString(),
        "titulo" to titulo,
        "tipo" to when (tipo) {
            "movie" -> "pelicula"
            "anime" -> "anime"
            else -> "serie"
        }
    )
}

/** Un enlace de video encontrado, listo para App.reproducir / ReproductorActivity. */
data class FuenteEncontrada(
    val nombre: String,
    val url: String,
    val ua: String = "",
    val referer: String = "",
    val idioma: String = "",   // "es-LA", "es-ES", "und" = idioma original
    val calidad: String = "",
    val origen: String = ""
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
