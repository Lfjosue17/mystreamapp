# Proveedores de video (motor genérico, Etapa 2)

> Esta pantalla todavía no existe en Ajustes. Este documento describe el formato
> que va a usar cuando la conectemos, para que puedas empezar a preparar tus
> proveedores desde ahora.

A diferencia de una **Fuente** (plantilla simple con comodines, lo que ya existe
hoy en Ajustes → Fuentes), un **proveedor del motor genérico** describe, paso a
paso, cómo encontrar el enlace en un sitio: a qué URL ir, qué selector o qué
expresión regular usar para sacar el enlace de ahí, y a qué otra URL ir con ese
resultado. Todo esto se guarda como un texto JSON, así que puedes agregar o
actualizar tus propios sitios sin recompilar la app.

## Formato de cada paso

| Campo | Para qué sirve |
|---|---|
| `url` | Página a visitar. Acepta los comodines `{tmdb}` `{imdb}` `{mal}` `{temporada}` `{episodio}` `{titulo}` `{tipo}`, y además `{anterior}` con el resultado del paso de antes. |
| `selector` + `atributo` | Selector CSS (como los de jQuery) para sacar uno o varios valores del HTML. `atributo` puede ser `href`, `src`, `text`, etc. |
| `regex` + `grupo` | Expresión regular para sacar un valor del HTML (o del resultado del selector, si van en el mismo paso). `grupo` es el grupo de captura `(...)` que quieres usar. |
| `todos` | `true` para quedarte con **todos** los resultados de ese paso, no solo el primero (útil en el último paso si quieres que te pregunte "Elige una fuente"). |

Un paso puede traer `url` solo (solo descarga), `selector`/`regex` solo (saca
algo del HTML que bajó el paso anterior), o ambos (descarga y saca algo de una
vez).

## Plantilla vacía

```json
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
```

- Deja `"pelicula"`, `"serie"` o `"anime"` como `[]` (lista vacía) si ese
  proveedor no sirve para ese tipo.
- `"usarSniffer": true` le dice al motor que, si el último paso te deja en una
  página web (no en un `.m3u8`/`.mpd`/`.mp4` directo), abra esa página en el
  Media Sniffer para encontrar el enlace real antes de mandarlo al reproductor.

## Servidor remoto (tu TMDB-Embed-API)

Otro campo nuevo en la configuración, `servidorRemoto`, también sin pantalla
todavía:

```json
{ "activo": true, "url": "https://tu-servidor.onrender.com", "apiKey": "..." }
```

El formato exacto de lo que responde tu servidor (`/api/fuentes?...`) lo
definimos juntos en la Parte B.
