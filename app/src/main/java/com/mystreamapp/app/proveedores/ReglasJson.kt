package com.mystreamapp.app.proveedores

import org.json.JSONObject

/**
 * Un paso del motor genérico. Cada paso puede:
 *  - `url`: pedir una página nueva (acepta comodines, incluido `{anterior}`
 *    con el resultado del paso previo).
 *  - `selector` + `atributo`: sacar uno o varios valores del HTML con un
 *    selector CSS (Jsoup). `atributo` puede ser "href", "src", "text", etc.
 *  - `regex` + `grupo`: sacar valores del HTML (o del resultado del selector,
 *    si ambos están en el mismo paso) con una expresión regular.
 *  - `todos`: true para quedarse con TODOS los resultados de este paso en vez
 *    de solo el primero (útil en el último paso si quieres varias opciones).
 */
data class PasoRegla(
    val url: String? = null,
    val selector: String? = null,
    val atributo: String = "href",
    val regex: String? = null,
    val grupo: Int = 1,
    val todos: Boolean = false
) {
    companion object {
        fun desdeJson(p: JSONObject): PasoRegla = PasoRegla(
            url = p.optString("url").takeIf { p.has("url") && it.isNotBlank() },
            selector = p.optString("selector").takeIf { p.has("selector") && it.isNotBlank() },
            atributo = p.optString("atributo", "href"),
            regex = p.optString("regex").takeIf { p.has("regex") && it.isNotBlank() },
            grupo = p.optInt("grupo", 1),
            todos = p.optBoolean("todos", false)
        )
    }
}

/**
 * Un proveedor dentro de `config.proveedores.providers[]` (lo que ya edita el
 * panel "Motor de proveedores (JSON)" en Ajustes). Los campos `id`,
 * `category` y `enabled` ya los usa app.js/nucleo.js para decidir a quién
 * preguntar; el resto (`cabeceras`, `usarSniffer`, `language`, `subtitles`,
 * `score`, `pasos`) solo los usa este motor en Kotlin.
 */
data class ReglasProveedor(
    val id: String = "",
    val nombre: String = "",
    val category: String = "",     // "movie" | "series" | "anime"
    val enabled: Boolean = true,
    val cabeceras: Map<String, String> = emptyMap(),
    /** Si el último paso da una página web (no un video directo), usar el Media Sniffer. */
    val usarSniffer: Boolean = false,
    val language: String = "",     // ej. "es-LA", "es-ES", "" = sin declarar
    val subtitles: Boolean = false,
    /** 0 = calcular automáticamente desde `language` (ver [puntajeIdioma]). */
    val score: Double = 0.0,
    val pasos: List<PasoRegla> = emptyList()
) {
    companion object {
        fun desdeJson(o: JSONObject): ReglasProveedor {
            val cabeceras = mutableMapOf<String, String>()
            o.optJSONObject("cabeceras")?.let { c -> c.keys().forEach { k -> cabeceras[k] = c.optString(k) } }
            val pasosArr = o.optJSONArray("pasos")
            val pasos = pasosArr?.let { a -> (0 until a.length()).map { PasoRegla.desdeJson(a.getJSONObject(it)) } } ?: emptyList()
            return ReglasProveedor(
                id = o.optString("id"),
                nombre = o.optString("nombre", o.optString("id")),
                category = o.optString("category"),
                enabled = o.optBoolean("enabled", true),
                cabeceras = cabeceras,
                usarSniffer = o.optBoolean("usarSniffer", false),
                language = o.optString("language"),
                subtitles = o.optBoolean("subtitles", false),
                score = o.optDouble("score", 0.0),
                pasos = pasos
            )
        }

        /** Un proveedor de ejemplo, vacío, para pegar dentro de `providers: [...]` en Ajustes. */
        fun ejemploVacio(): String = """
            {
              "id": "mi-proveedor",
              "nombre": "Mi proveedor",
              "category": "movie",
              "enabled": true,
              "cabeceras": { "User-Agent": "Mozilla/5.0", "Referer": "https://mi-sitio.com/" },
              "usarSniffer": false,
              "language": "es-LA",
              "subtitles": false,
              "score": 0,
              "pasos": [
                { "url": "https://mi-sitio.com/buscar?q={titulo}" },
                { "selector": "a.resultado", "atributo": "href" },
                { "selector": "a.ver-enlace", "atributo": "href" }
              ]
            }
        """.trimIndent()
    }
}
