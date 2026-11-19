package com.mystreamapp.app.proveedores

import android.app.Activity
import org.jsoup.Jsoup
import java.util.regex.Pattern

/**
 * Motor genérico (Etapa 2): ejecuta los pasos de [ReglasProveedor] con Jsoup
 * (selectores CSS) y/o Regex, sin tener que recompilar la app. Cada paso puede
 * pedir una URL nueva y/o extraer valores del HTML más reciente.
 *
 * Si el resultado final no es un video directo y `usarSniffer` está activo,
 * se pasa por [Sniffer] para conseguir el enlace real antes de devolverlo.
 */
class ProveedorJson(
    private val reglas: ReglasProveedor,
    private val act: Activity
) : MotorProveedor {

    override val nombre: String get() = reglas.nombre

    override fun soporta(info: InfoContenido): Boolean =
        reglas.activo && tipoDe(info)?.pasos?.isNotEmpty() == true

    override suspend fun buscar(info: InfoContenido): List<FuenteEncontrada> {
        val pasos = tipoDe(info)?.pasos ?: return emptyList()
        if (pasos.isEmpty()) return emptyList()

        val comodines = info.comodines().toMutableMap()
        var html: String? = null
        var resultados = listOf<String>()

        for (paso in pasos) {
            if (paso.url != null) {
                comodines["anterior"] = resultados.firstOrNull() ?: ""
                val url = ProveedorPlantilla.rellenar(paso.url, comodines)
                html = try {
                    Jsoup.connect(url).apply {
                        reglas.cabeceras.forEach { (k, v) -> header(k, v) }
                        timeout(15_000)
                        ignoreContentType(true)
                        ignoreHttpErrors(true)
                    }.get().outerHtml()
                } catch (e: Exception) {
                    return emptyList()
                }
                resultados = listOf(url)
            }

            if (paso.selector != null || paso.regex != null) {
                val base = html ?: return emptyList()
                val extraidos = extraer(base, paso)
                if (extraidos.isEmpty()) return emptyList()
                resultados = extraidos
            }
        }

        if (resultados.isEmpty()) return emptyList()
        val ultimoPaso = pasos.last()
        val finales = if (ultimoPaso.todos) resultados else resultados.take(1)

        val fuentes = finales.mapIndexed { i, u ->
            FuenteEncontrada(
                nombre = if (finales.size > 1) "${reglas.nombre} ${i + 1}" else reglas.nombre,
                url = u,
                ua = reglas.cabeceras["User-Agent"] ?: "",
                referer = reglas.cabeceras["Referer"] ?: "",
                origen = reglas.nombre
            )
        }

        if (!reglas.usarSniffer) return fuentes

        // Si hace falta, cada enlace que no sea video directo se resuelve con el Sniffer.
        return fuentes.map { f ->
            if (esVideoDirecto(f.url)) f
            else Sniffer.buscar(act, f.url, reglas.cabeceras) ?: f
        }
    }

    private fun extraer(html: String, paso: PasoRegla): List<String> {
        var candidatos = listOf(html)
        if (paso.selector != null) {
            val doc = Jsoup.parse(html)
            candidatos = doc.select(paso.selector).map {
                when (paso.atributo) {
                    "text" -> it.text()
                    else -> it.attr(paso.atributo)
                }
            }.filter { it.isNotBlank() }
        }
        if (paso.regex != null) {
            val patron = Pattern.compile(paso.regex)
            candidatos = candidatos.flatMap { texto ->
                val m = patron.matcher(texto)
                val res = mutableListOf<String>()
                while (m.find()) {
                    if (m.groupCount() >= paso.grupo) res.add(m.group(paso.grupo) ?: "")
                }
                res
            }.filter { it.isNotBlank() }
        }
        return if (paso.todos) candidatos else candidatos.take(1)
    }

    private fun tipoDe(info: InfoContenido): ReglasTipo? = when (info.tipo) {
        "movie" -> reglas.pelicula
        "anime" -> reglas.anime
        else -> reglas.serie
    }
}
