package com.mystreamapp.app.proveedores

/**
 * Contrato común para resolver UN proveedor (ya identificado por `id`) a una
 * lista de enlaces. La fila sobre qué proveedores existen y a cuáles
 * preguntar (categoría, activo/no) ya la resuelve `buscarCategoria()` en
 * nucleo.js; aquí solo se ejecuta uno a la vez.
 *
 * Implementaciones: [ProveedorJson] (motor Jsoup/Regex local) y [ClienteEmbed]
 * (tu servidor TMDB-Embed-API).
 */
interface MotorProveedor {
    suspend fun buscar(info: InfoContenido): List<ResultadoProveedor>
}
