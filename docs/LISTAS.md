# Listas, agendas y fuentes

La app no trae contenido propio. Todo lo que se reproduce viene de **listas que tú agregas** en
**Ajustes**. Hay tres tipos de listas:

| Tipo | Para qué sirve | Formato | Dónde se ve |
|---|---|---|---|
| Lista de canales | Canales en vivo | M3U | TV en vivo |
| Agenda deportiva | Partidos del día con sus enlaces | JSON | Deportes |
| Fuente | Abrir películas y series del catálogo | Plantilla de enlace | Botón ▶ Ver en el detalle |

Cada una se puede agregar de dos formas:

- **Enlace (URL):** la app la descarga y la actualiza sola (canales cada 6 horas, agendas cada 10 minutos).
- **Pegar contenido:** pegas el texto completo en el cuadro. No se actualiza, pero no depende de internet para leerla.

> Para que la app traiga tus listas desde la primera vez que se instala, ponlas en
> `app/src/main/assets/www/config-predeterminada.js`.

---

## 1. Lista de canales (M3U)

Es el formato estándar de las listas IPTV. Ejemplo:

```m3u
#EXTM3U
#EXTINF:-1 tvg-logo="https://ejemplo.com/logo1.png" group-title="Noticias",Canal de Noticias
https://ejemplo.com/noticias/index.m3u8

#EXTINF:-1 tvg-logo="https://ejemplo.com/logo2.png" group-title="Deportes",Canal Deportivo
#EXTVLCOPT:http-user-agent=Mozilla/5.0
#EXTVLCOPT:http-referrer=https://ejemplo.com/
https://ejemplo.com/deportes/playlist.m3u8
```

- `group-title` → agrupa los canales en los botones de arriba. Si el grupo contiene “sport” o
  “deport”, el canal aparece también en el filtro **Deportes**.
- `tvg-logo` → logo del canal.
- `#EXTVLCOPT:http-user-agent` y `#EXTVLCOPT:http-referrer` → cabeceras que algunos servidores piden.
  También se acepta el estilo Kodi: `https://servidor/video.m3u8|User-Agent=...&Referer=...`
- Cómo se abre cada enlace:
  - `.m3u8`, `.mpd`, `.mp4`, `.ts` o sin extensión → reproductor de la app (ExoPlayer).
  - `.html`, `.php` o `/embed/` → visor web dentro de la app.
  - YouTube → app de YouTube.

**Listas públicas que trae la app** (de [iptv-org](https://github.com/iptv-org/iptv), canales
gratuitos y abiertos): Honduras, En español y Deportes (esta última apagada al inicio). Otras:

- Por país: `https://iptv-org.github.io/iptv/countries/XX.m3u` (XX = mx, gt, sv, us, es…)
- Por idioma: `https://iptv-org.github.io/iptv/languages/spa.m3u`
- Por categoría: `https://iptv-org.github.io/iptv/categories/news.m3u` (news, sports, kids, movies, music…)

---

## 2. Agenda deportiva (JSON)

La pantalla **Deportes** funciona como esas páginas de agenda: muestra los partidos del día
ordenados por hora y por liga, y al tocar uno salen sus **enlaces**. Los partidos vienen de dos lados:

1. **TheSportsDB** (automático): nombre, hora, liga, escudos y marcador. No trae enlaces.
2. **Tus agendas**: un archivo JSON con los partidos y los enlaces que tú quieras.

Si un partido de tu agenda es el mismo que uno de TheSportsDB (mismos equipos, hora parecida),
la app **junta los dos**: los datos de TheSportsDB y tus enlaces. Si no coincide, sale aparte.

### Formato

```json
{
  "nombre": "Mi agenda",
  "eventos": [
    {
      "titulo": "Kuwait vs Honduras",
      "local": "Kuwait",
      "visita": "Honduras",
      "liga": "Amistoso internacional",
      "deporte": "Fútbol",
      "inicio": "2026-11-17T12:00:00-06:00",
      "escudoLocal": "https://…/kuwait.png",
      "escudoVisita": "https://…/honduras.png",
      "enlaces": [
        { "nombre": "Opción 1", "url": "https://…/partido.m3u8" },
        { "nombre": "Opción 2 (web)", "url": "https://…/ver.html" },
        { "nombre": "Con cabeceras", "url": "https://…/x.m3u8", "ua": "Mozilla/5.0", "referer": "https://…/" }
      ]
    }
  ]
}
```

| Campo | Obligatorio | Nota |
|---|---|---|
| `inicio` | Sí | Fecha y hora con zona horaria. Honduras es `-06:00`. |
| `titulo` | Sí* | *O `local` + `visita`. |
| `local`, `visita` | No | Sirven para juntar con TheSportsDB. Escríbelos igual que en inglés si puedes (ej. “Spain”). |
| `deporte` | No | Fútbol, Baloncesto, Béisbol, Fútbol americano, Motor, Combate o Tenis. Para los filtros. |
| `liga` | No | Título del grupo en pantalla. |
| `enlaces` | No | Lista de `{nombre, url, ua, referer}` o solo textos con la URL. |

También se acepta directamente una lista `[ {…}, {…} ]` sin el `"eventos"`.

### Cómo mantener tu agenda al día (sin tocar la app)

1. Crea el archivo `agenda.json` en un **Gist secreto** de GitHub (gist.github.com) o en un repo público.
   Un repo privado no sirve: la app tiene que poder abrir el enlace sin iniciar sesión.
2. Copia el enlace **Raw** (empieza con `https://gist.githubusercontent.com/…` o
   `https://raw.githubusercontent.com/…`). Quítale la parte del commit para que siempre apunte a la última versión.
3. En la app: **Ajustes → Agendas deportivas → Agregar**, pega el enlace y toca **Probar**.
4. Cada vez que edites el archivo en GitHub, la app lo verá en menos de 10 minutos.

Si quieres automatizarlo, un script (Node.js, por ejemplo) puede generar el JSON cada día y
subirlo al Gist. La app solo necesita que el enlace devuelva ese formato.

---

## 3. Fuentes para películas y series

Una fuente es una **plantilla de enlace**. Cuando tocas ▶ Ver, la app cambia los comodines por los
datos del título y abre el enlace.

| Comodín | Se cambia por | Ejemplo |
|---|---|---|
| `{tmdb}` | ID de TMDB (películas y series) | `550` |
| `{imdb}` | ID de IMDb | `tt0137523` |
| `{mal}` | ID de MyAnimeList (anime) | `5114` |
| `{temporada}` | Número de temporada (series) | `1` |
| `{episodio}` | Número de episodio (series y anime) | `3` |
| `{titulo}` | Título (codificado para URL) | `El%20club%20de%20la%20pelea` |

Cada fuente tiene tres enlaces (películas, series y anime); puedes llenar solo los que quieras. El de
anime se usa en la sección Anime y recibe `{mal}` y `{episodio}`. Ejemplo: `https://mi-servidor.com/anime/{mal}/{episodio}`.

Ejemplo de fuente:

- Películas: `https://mi-servidor.com/pelicula/{tmdb}`
- Series: `https://mi-servidor.com/serie/{tmdb}/{temporada}/{episodio}`

Si el enlace final es un video directo (`.m3u8`, `.mp4`…) se abre en el reproductor de la app;
si es una página web, se abre en el visor web. Con varias fuentes activas, la app pregunta cuál usar.

El visor web **bloquea ventanas emergentes y redirecciones**, y la app bloquea dominios de anuncios
conocidos (lista en `MainActivity.kt`, `BLOQUEADOS`).

---

## Respaldo

En **Ajustes → Respaldo** está toda tu configuración como texto. Cópialo y guárdalo (por ejemplo en
una nota). Para restaurarla, pégalo en el mismo cuadro y toca **Importar lo pegado**.
No guardes ese texto en el repo si tiene tus claves.
