# Motor local de proveedores JSON (MyStream)

El motor se ejecuta dentro del APK mediante Kotlin + Jsoup. No requiere Node.js ni un servidor propio. La configuración se edita en **Ajustes → Motor de proveedores (JSON)** y se guarda en el almacenamiento privado de la aplicación.

## Plantilla vacía

```json
{
  "version": 1,
  "providers": []
}
```

## Estructura de cada proveedor

```json
{
  "version": 1,
  "providers": [
    {
      "id": "mi-proveedor",
      "name": "Mi proveedor autorizado",
      "enabled": true,
      "type": "html",
      "category": "movie",
      "urlTemplate": "https://mi-dominio.example/pelicula/{tmdb}",
      "timeoutMs": 15000,
      "headers": {
        "User-Agent": "Mozilla/5.0",
        "Referer": "https://mi-dominio.example/"
      },
      "extract": {
        "mode": "css",
        "selector": "video source",
        "attribute": "src",
        "streamPattern": "(?i)\\.(m3u8|mpd|mp4)(?:[?#].*)?$"
      },
      "language": {
        "default": "und",
        "rules": [
          { "code": "es-419", "pattern": "(?i)(latino|latam|es-419|spanish.?latin)" },
          { "code": "es-ES", "pattern": "(?i)(castellano|es-es|spanish.?spain)" },
          { "code": "original", "pattern": "(?i)(original|multi.?audio)" }
        ],
        "subtitlesPattern": "(?i)(subtitles|subtitulos|\.vtt|\.srt)"
      }
    }
  ]
}
```

El dominio `.example` es deliberadamente ficticio: sustituye la URL y las reglas por una fuente que tengas autorización para consultar y reproducir.

## Campos admitidos

- `id`: identificador único; letras, números, punto, guion y guion bajo.
- `name`: nombre visible en el selector de fuentes.
- `enabled`: permite activar/desactivar sin borrar la regla.
- `type`: por ahora `html`.
- `category`: `movie`, `series`, `anime` o `sports`. La categoría `sports` permite definir reglas para páginas de eventos; las agendas deportivas JSON actuales siguen funcionando sin cambios.
- `urlTemplate`: URL HTTP(S). Variables: `{tmdb}`, `{imdb}`, `{mal}`, `{season}`, `{episode}`, `{title}`, `{type}`, `{event}`, `{league}`, `{date}`, `{home}`, `{away}` (los últimos cinco para proveedores de deportes).
- `timeoutMs`: 3000 a 30000 ms; valor predeterminado 15000.
- `headers`: cabeceras HTTP opcionales, por ejemplo `User-Agent` y `Referer`.
- `extract.mode`: `css` o `regex`.
- `extract.selector`: selector CSS de Jsoup, por ejemplo `video source`.
- `extract.attribute`: atributo a leer; predeterminado `src`. Usa `text` para el texto del elemento.
- `extract.pattern` y `extract.group`: patrón Regex y grupo de captura (modo `regex`).
- `extract.streamPattern`: Regex que limita qué URLs se aceptan como streams. Por defecto reconoce `.m3u8`, `.mpd` y `.mp4`.
- `language.default`: `und`, `es-419`, `es-ES` u `original`.
- `language.rules`: reglas ordenadas; la primera coincidencia gana.
- `language.subtitlesPattern`: Regex para detectar indicios de subtítulos en el HTML.

## Prioridad de idioma

El motor ordena las fuentes encontradas así: español latino (`es-419`), español de España (`es-ES`), original con subtítulos y, finalmente, idioma no identificado. La detección se basa en los patrones que configures y en metadatos visibles; no puede verificar de forma infalible el idioma real de una pista de audio.

## Límites actuales

- Descarga como máximo 2 MB de HTML por consulta y aplica timeout.
- Solo acepta resultados HTTP 200–299 y URLs HTTP(S).
- Solo devuelve URLs que coincidan con `streamPattern`.
- No ejecuta JavaScript de la página, no resuelve captchas, DRM, tokens generados por scripts ni reproductores que requieran interacción.
- No incluye un proxy de medios: el teléfono reproduce directamente desde el origen.
- Las listas M3U y las agendas deportivas JSON existentes siguen usando sus implementaciones actuales; no se reemplazan.
- Una regla CSS/Regex no elimina publicidad incorporada en el stream ni garantiza que una fuente externa sea libre de anuncios.

Usa únicamente fuentes cuya consulta y reproducción estén autorizadas. No configures reglas para eludir DRM, autenticación, controles de acceso o medidas anti-bot.
