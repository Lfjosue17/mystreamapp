package com.mystreamapp.app.proveedores

import android.app.Activity
import org.json.JSONObject

/**
 * Resuelve UNA consulta de proveedor por `id`, como la manda
 * `Nativo.consultarProveedor(id, parametros, callbackId)` desde app.js. La
 * lista de a quién preguntar (categoría, activo/no) ya la decide
 * `buscarCategoria()` en nucleo.js llamando una vez por cada id; esta clase
 * solo necesita resolver uno.
 *
 * Hay un id reservado, [ID_SERVIDOR_REMOTO], que `buscarCategoria()` agrega
 * a la lista cuando tu servidor remoto está activo: en ese caso se usa
 * [ClienteEmbed] en vez de buscar el id dentro de `config.proveedores.providers`.
 */
class RepositorioProveedores(private val act: Activity) {

    suspend fun resolver(config: JSONObject, id: String, info: InfoContenido): List<ResultadoProveedor> {
        if (id == ID_SERVIDOR_REMOTO) return consultarServidorRemoto(config, info)
        return consultarProveedorLocal(config, id, info)
    }

    private suspend fun consultarProveedorLocal(config: JSONObject, id: String, info: InfoContenido): List<ResultadoProveedor> {
        val arr = config.optJSONObject("proveedores")?.optJSONArray("providers") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            if (p.optString("id") == id) {
                val reglas = ReglasProveedor.desdeJson(p)
                return ProveedorJson(reglas, act).buscar(info)
            }
        }
        return emptyList()
    }

    private suspend fun consultarServidorRemoto(config: JSONObject, info: InfoContenido): List<ResultadoProveedor> {
        val s = config.optJSONObject("servidorRemoto") ?: return emptyList()
        if (!s.optBoolean("activo", false)) return emptyList()
        return ClienteEmbed(s.optString("url"), s.optString("apiKey")).buscar(info)
    }

    companion object {
        /** Debe coincidir exactamente con el mismo texto usado en nucleo.js. */
        const val ID_SERVIDOR_REMOTO = "servidor-remoto"
    }
}
