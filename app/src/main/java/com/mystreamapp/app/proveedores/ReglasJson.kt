package com.mystreamapp.app.proveedores

import org.json.JSONObject

/**
 * Un paso del motor genérico (Etapa 2). Cada paso puede:
 *  - `url`: pedir una página nueva (acepta comodines, incluido `{anterior}` con
 *    el resultado del paso previo).
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
)

data class ReglasTipo(val pasos: List<PasoRegla> = emptyList())

/** Configuración completa de un proveedor del motor genérico (un JSON por proveedor). */
data class ReglasProveedor(
    val id: String = "",
    val nombre: String = "",
    val activo: Boolean = true,
    val cabeceras: Map<String, String> = emptyMap(),
    /** Si el último paso da una página web (no un video directo), usar el Media Sniffer. */
    val usarSniffer: Boolean = false,
    val pelicula: ReglasTipo? = null,
    val serie: ReglasTipo? = null,
    val anime: ReglasTipo? = null
) {
    companion object {
        fun desdeJson(texto: String): ReglasProveedor {
            val o = JSONObject(texto)

            fun tipo(clave: String): ReglasTipo? {
                val a = o.optJSONArray(clave) ?: return null
                val pasos = (0 until a.length()).map { i ->
                    val p = a.getJSONObject(i)
                    PasoRegla(
                        url = p.optString("url").takeIf { p.has("url") && it.isNotBlank() },
                        selector = p.optString("selector").takeIf { p.has("selector") && it.isNotBlank() },
                        atributo = p.optString("atributo", "href"),
                        regex = p.optString("regex").takeIf { p.has("regex") && it.isNotBlank() },
                        grupo = p.optInt("grupo", 1),
                        todos = p.optBoolean("todos", false)
                    )
                }
                return ReglasTipo(pasos)
            }

            val cabeceras = mutableMapOf<String, String>()
            o.optJSONObject("cabeceras")?.let { c -> c.keys().forEach { k -> cabeceras[k] = c.optString(k) } }

            return ReglasProveedor(
                id = o.optString("id", o.optString("nombre")),
                nombre = o.optString("nombre", "Proveedor"),
                activo = o.optBoolean("activo", true),
                cabeceras = cabeceras,
                usarSniffer = o.optBoolean("usarSniffer", false),
                pelicula = tipo("pelicula"),
                serie = tipo("serie"),
                anime = tipo("anime")
            )
        }

        /**
         * Plantilla JSON vacía para que la pegues en Ajustes (cuando conectemos esa
         * pantalla) y la llenes con los selectores/regex de tu sitio.
         */
        fun plantillaVacia(): String = """
            {
              "id": "mi-proveedor",
              "nombre": "Mi proveedor",
              "activo": true,
              "cabeceras": { "User-Agent": "Mozilla/5.0", "Referer": "https://mi-sitio.com/" },
              "usarSniffer": false,
              "pelicula": [
                { "url": "https://mi-sitio.com/buscar?q={titulo}" },
                { "selector": "a.resultado", "atributo": "href" },
                { "selector": "a.ver-enlace", "atributo": "href" }
              ],
              "serie": [
                { "url": "https://mi-sitio.com/serie/{tmdb}/temporada-{temporada}/episodio-{episodio}" },
                { "selector": "a.ver-enlace", "atributo": "href" }
              ],
              "anime": []
            }
        """.trimIndent()
    }
}
