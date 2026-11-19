package com.mystreamapp.app.proveedores

/**
 * Contrato común para cualquier forma de buscar fuentes de video de una
 * película, serie o anime. Separa el "qué" (buscar fuentes) del "cómo".
 *
 * Implementaciones de hoy:
 *  - [ProveedorPlantilla]: las 3 plantillas de enlace que ya existen en
 *    Ajustes → Fuentes (comodines {tmdb} {temporada} {episodio}...).
 *  - [ProveedorJson]: motor genérico configurable por JSON (Jsoup + Regex),
 *    sin recompilar la app.
 *  - [ClienteEmbed]: cliente HTTP hacia tu propio servidor TMDB-Embed-API.
 */
interface MotorProveedor {
    /** Nombre que ve el usuario (p. ej. en el selector "Elige una fuente"). */
    val nombre: String

    /** true si este proveedor tiene algo configurado para este tipo de contenido. */
    fun soporta(info: InfoContenido): Boolean

    /** Busca fuentes. Hace red: se llama desde un hilo de fondo (corrutina IO). */
    suspend fun buscar(info: InfoContenido): List<FuenteEncontrada>
}
