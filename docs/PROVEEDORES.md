# Proveedores de video (motor genérico)

Se configura en **Ajustes → Motor de proveedores (JSON)**. A diferencia de una
**Fuente** (plantilla simple con comodines, Ajustes → Fuentes), un **proveedor**
describe paso a paso cómo encontrar el enlace en un sitio: a qué URL ir, qué
selector CSS o expresión regular usar para sacar el enlace de ahí, y a qué
otra URL ir con ese resultado. Todo se guarda como texto JSON: puedes agregar
o actualizar sitios sin recompilar la app.

> Nota: antes existió un borrador con otro formato (`urlTemplate` + `extract`,
> en `docs/PROVEEDORES_JSON.md`). Ese formato no llegó a implementarse: el
> motor real usa el esquema de `pasos[]` de este documento, porque permite
> encadenar varias páginas (buscar → episodio → enlace final), algo que un
> solo `urlTemplate` no puede hacer. `PROVEEDORES_JSON.md` quedó obsoleto;
> puedes borrarlo.

## Plantilla vacía

```json
{ "version": 1, "providers": [] }
```

## Un proveedor

```json
{
  "id": "mi-proveedor",
  "nombre": "Mi proveedor",
  "category": "movie",
  "enabled": true,
  "cabeceras": { "User-Agent": "Mozilla/5.0", "Referer": "https://mi-sitio.com/" },
  "usarSniffer": false,
  "language": "es-LA",
  "subtitles": false,
  "score": 0,
  "pasos": [
    { "url": "https://mi-sitio.com/buscar?q={titulo}" },
    { "selector": "a.resultado", "atributo": "href" },
    { "selector": "a.ver-enlace", "atributo": "href" }
  ]
}
```

## Campos

| Campo | Para qué sirve |
|---|---|
| `id` | Identificador único (letras, números, `-`, `_`). Lo usa `Nativo.consultarProveedor(id, …)`. |
| `nombre` | Se muestra en "Elige una fuente" (`providerName`). |
| `category` | `movie`, `series` o `anime`. Decide a qué pantallas aplica. |
| `enabled` | Activar/desactivar sin borrar. |
| `cabeceras` | Cabeceras HTTP para pedir la página y, si no hay otras, para el enlace final (p. ej. `User-Agent`, `Referer`). |
| `usarSniffer` | `true`: si el último paso deja una página web (no un `.m3u8`/`.mpd`/`.mp4` directo), se abre en el Media Sniffer (WebView oculto) para sacar el enlace real generado por JavaScript. |
| `language` | Idioma declarado (`es-LA`, `es-ES`, `en`, …). Se usa para ordenar: español latino primero, España después, y subtitulado/original al final. |
| `subtitles` | `true` si es el idioma original con subtítulos. |
| `score` | Opcional. Si lo pones (> 0), se usa en vez de calcularlo desde `language`/`subtitles`. |
| `pasos` | Lista de pasos (ver abajo). El último paso define el/los resultado(s) final(es). |

### Cada paso de `pasos`

| Campo | Para qué sirve |
|---|---|
| `url` | Página a visitar. Comodines: `{tmdb}` `{imdb}` `{mal}` `{temporada}` `{episodio}` `{titulo}` `{tipo}`, y `{anterior}` con el resultado del paso de antes. |
| `selector` + `atributo` | Selector CSS (Jsoup) para sacar uno o varios valores del HTML. `atributo`: `href`, `src`, `text`, etc. |
| `regex` + `grupo` | Expresión regular para sacar un valor del HTML (o del resultado del selector, si van en el mismo paso). `grupo` es el grupo de captura `(...)`. |
| `todos` | `true` para quedarte con **todos** los resultados de ese paso, no solo el primero (útil en el último paso si quieres que "Elige una fuente" te dé varias opciones de este proveedor). |

Un paso puede traer `url` solo (solo descarga), `selector`/`regex` solo (saca
algo del HTML que bajó el paso anterior), o ambos.

## Servidor remoto (tu TMDB-Embed-API)

En **Ajustes → Servidor remoto (opcional)** activas tu propio servidor (URL +
API Key). Cuando está activo, se agrega automáticamente a la consulta junto
con tus proveedores JSON (id reservado `servidor-remoto`, lo agrega
`nucleo.js`); sus resultados se combinan y se ordenan igual, por idioma. El
formato exacto de lo que responde tu servidor (`/api/fuentes?...`) se define
en la Parte B (servidor TMDB-Embed-API).

## Validación

`Ajustes → Validar y guardar proveedores` llama a `Nativo.validarProveedores(json)`
(revisa la forma del JSON, sin red) antes de guardar. Si algo falta, te dice
qué proveedor y qué campo.
