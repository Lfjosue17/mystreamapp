package com.mystreamapp.app.proveedores

/**
 * Proveedor de plantilla de enlace: es exactamente lo que hoy existe en
 * Ajustes → Fuentes (`plantilla()` en app.js), convertido en una implementación
 * más de [MotorProveedor]. Comodines: {tmdb} {imdb} {mal} {temporada} {episodio}
 * {titulo} {tipo}.
 */
class ProveedorPlantilla(
    override val nombre: String,
    private val pelicula: String?,
    private val serie: String?,
    private val anime: String?
) : MotorProveedor {

    override fun soporta(info: InfoContenido): Boolean = plantillaDe(info) != null

    override suspend fun buscar(info: InfoContenido): List<FuenteEncontrada> {
        val t = plantillaDe(info) ?: return emptyList()
        val url = rellenar(t, info.comodines())
        return listOf(FuenteEncontrada(nombre = nombre, url = url, origen = nombre))
    }

    private fun plantillaDe(info: InfoContenido): String? = when (info.tipo) {
        "movie" -> pelicula
        "anime" -> anime
        else -> serie
    }?.takeIf { it.isNotBlank() }

    companion object {
        private val COMODIN = Regex("\\{(tmdb|imdb|mal|temporada|episodio|titulo|tipo)\\}")

        fun rellenar(texto: String, valores: Map<String, String>): String =
            COMODIN.replace(texto) { m -> codificarURI(valores[m.groupValues[1]] ?: "") }
    }
}
