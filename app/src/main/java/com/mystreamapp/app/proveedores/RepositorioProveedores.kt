package com.mystreamapp.app.proveedores

import android.app.Activity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Junta todos los proveedores configurados (las plantillas de siempre, los
 * motores JSON y tu servidor remoto) y les pregunta a todos en paralelo.
 *
 * Lee la configuración del mismo objeto `config` que ya guarda la interfaz web
 * (clave "config" en el puente), para no duplicar almacenamiento:
 *  - `fuentes`: igual que hoy (plantillas).
 *  - `proveedoresJson`: nueva lista de textos JSON (uno por proveedor del motor
 *    genérico). Aún no hay pantalla en Ajustes para editarla.
 *  - `servidorRemoto`: nuevo objeto { "activo", "url", "apiKey" }. Tampoco tiene
 *    pantalla en Ajustes todavía.
 */
class RepositorioProveedores(private val act: Activity) {

    suspend fun buscar(info: InfoContenido, config: JSONObject): List<FuenteEncontrada> = coroutineScope {
        val proveedores = construir(config).filter { it.soporta(info) }
        val tareas = proveedores.map { p -> async { seguro(p, info) } }
        tareas.flatMap { it.await() }
    }

    private suspend fun seguro(p: MotorProveedor, info: InfoContenido): List<FuenteEncontrada> =
        try {
            p.buscar(info)
        } catch (e: Exception) {
            emptyList()
        }

    private fun construir(config: JSONObject): List<MotorProveedor> {
        val lista = mutableListOf<MotorProveedor>()

        config.optJSONArray("fuentes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                if (f.optBoolean("activa", true)) {
                    lista += ProveedorPlantilla(
                        nombre = f.optString("nombre", "Fuente"),
                        pelicula = f.optString("pelicula"),
                        serie = f.optString("serie"),
                        anime = f.optString("anime")
                    )
                }
            }
        }

        config.optJSONArray("proveedoresJson")?.let { arr ->
            for (i in 0 until arr.length()) {
                val texto = arr.optString(i)
                try {
                    val reglas = ReglasProveedor.desdeJson(texto)
                    if (reglas.activo) lista += ProveedorJson(reglas, act)
                } catch (e: Exception) {
                    // Configuración inválida: se ignora ese proveedor en vez de romper los demás.
                }
            }
        }

        config.optJSONObject("servidorRemoto")?.let { s ->
            if (s.optBoolean("activo", false)) {
                lista += ClienteEmbed(s.optString("url"), s.optString("apiKey"))
            }
        }

        return lista
    }
}
