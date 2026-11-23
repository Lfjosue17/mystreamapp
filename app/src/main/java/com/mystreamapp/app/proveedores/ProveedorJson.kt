package com.mystreamapp.app.proveedores

import android.app.Activity
import org.jsoup.Jsoup
import java.util.regex.Pattern

/**
 * Motor genérico: ejecuta los `pasos` de un [ReglasProveedor] con Jsoup
 * (selectores CSS) y/o Regex, sin tener que recompilar la app.
 *
 * Si el resultado final no es un video directo y `usarSniffer` está activo,
 * se pasa por [Sniffer] para conseguir el enlace real antes de devolverlo.
 */
class ProveedorJson(
    private val reglas: ReglasProveedor,
    private val act: Activity
) : MotorProveedor {

    override suspend fun buscar(info: InfoContenido): List<ResultadoProveedor> {
        if (!reglas.enabled || reglas.pasos.isEmpty()) return emptyList()

        val comodines = info.comodines().toMutableMap()
        var html: String? = null
        var resultados = listOf<String>()

        for (paso in reglas.pasos) {
            if (paso.url != null) {
                comodines["anterior"] = resultados.firstOrNull() ?: ""
                val url = rellenar(paso.url, comodines)
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
        val ultimoPaso = reglas.pasos.last()
        val finales = if (ultimoPaso.todos) resultados else resultados.take(1)
        val puntaje = if (reglas.score > 0) reglas.score else puntajeIdioma(reglas.language, reglas.subtitles).toDouble()
        val nombre = reglas.nombre.ifBlank { reglas.id }

        return finales.map { u ->
            var urlFinal = u
            var cabecerasFinal = reglas.cabeceras
            var subtituloFinal = ""
            if (reglas.usarSniffer && !esVideoDirecto(u)) {
                Sniffer.buscar(act, u, reglas.cabeceras)?.let {
                    urlFinal = it.url
                    cabecerasFinal = it.headers
                    subtituloFinal = it.subtitleUrl ?: ""
                }
            }
            ResultadoProveedor(
                providerName = nombre,
                url = urlFinal,
                headers = cabecerasFinal,
                language = reglas.language,
                subtitles = reglas.subtitles || subtituloFinal.isNotBlank(),
                score = puntaje,
                subtitleUrl = subtituloFinal
            )
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

    companion object {
        private val COMODIN = Regex("\\{(tmdb|imdb|mal|temporada|episodio|titulo|tipo|anterior)\\}")

        fun rellenar(texto: String, valores: Map<String, String>): String =
            COMODIN.replace(texto) { m -> codificarURI(valores[m.groupValues[1]] ?: "") }
    }
}
