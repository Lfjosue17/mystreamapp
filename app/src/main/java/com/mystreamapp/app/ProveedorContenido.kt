package com.mystreamapp.app

/**
 * Contrato común para proveedores de fuentes multimedia.
 * Cada implementación recibe la definición JSON y los identificadores del contenido.
 * Las listas M3U y las agendas deportivas conservan sus lectores actuales en JavaScript.
 */
interface ProveedorContenido {
    fun consultar(definicion: org.json.JSONObject, parametros: org.json.JSONObject): List<FuenteEncontrada>
}

data class FuenteEncontrada(
    val proveedorId: String,
    val proveedorNombre: String,
    val categoria: String,
    val url: String,
    val cabeceras: Map<String, String>,
    val idioma: String,
    val subtitulos: Boolean,
    val puntuacion: Int
) {
    fun aJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("providerId", proveedorId)
        put("providerName", proveedorNombre)
        put("category", categoria)
        put("url", url)
        put("headers", org.json.JSONObject(cabeceras))
        put("language", idioma)
        put("subtitles", subtitulos)
        put("score", puntuacion)
    }
}
