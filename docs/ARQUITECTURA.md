# Arquitectura

La app tiene dos partes:

1. **Parte nativa (Kotlin):** abre la interfaz, guarda datos, descarga listas y reproduce video.
2. **Interfaz web (HTML/CSS/JS):** todas las pantallas. Está en `app/src/main/assets/www` y se
   puede cambiar sin tocar Kotlin.

```
┌─────────────────────── MainActivity (WebView) ───────────────────────┐
│  assets/www/index.html                                               │
│   ├─ config-predeterminada.js  listas que trae la app la 1.ª vez     │
│   ├─ nucleo.js     datos, red, TMDB, M3U, canales, deportes          │
│   ├─ navegacion-tv.js  flechas del control remoto                    │
│   └─ app.js        pantallas y botones                               │
│            │  window.Nativo.*  (Puente.kt)                           │
└────────────┼─────────────────────────────────────────────────────────┘
             ▼
  Puente.kt ── guardar/leer archivos ── filesDir/datos/*.json
           ── descargar(url)  (sin CORS, sigue redirecciones)
           ── reproducir(url) ──► ReproductorActivity (ExoPlayer)
           ── abrirExterno(url) ──► YouTube / navegador
```

## Archivos

| Archivo | Qué hace |
|---|---|
| `MainActivity.kt` | WebView a pantalla completa, botón Atrás, pantalla completa de iframes, bloqueo de dominios de anuncios (`BLOQUEADOS`). |
| `Puente.kt` | Funciones que la web llama con `Nativo.<función>()`. |
| `ReproductorActivity.kt` | Reproductor ExoPlayer (HLS, DASH, MP4) con cabeceras User-Agent/Referer. |
| `res/xml/network_security_config.xml` | Permite enlaces `http://` (muchas listas los usan). |
| `res/drawable/banner_tv.xml` | Imagen de la app en Android TV. |
| `keepRules/rules.keep` | Evita que R8 borre las funciones del Puente en release. |
| `assets/www/*` | Interfaz. |

## Funciones del Puente (`Nativo`)

| Función | Uso |
|---|---|
| `leer(clave)` / `guardar(clave, texto)` / `borrar(clave)` | Guardado local en archivos internos. |
| `descargar(id, url, cabecerasJson)` | Descarga en segundo plano y responde a `App.red._fin(id, ok, contenido)`. Límite 40 MB. |
| `reproducir(url, titulo, cabecerasJson)` | Abre el reproductor nativo. |
| `abrirExterno(url)` | Abre otra app (solo http/https). |
| `tmdbKey()` / `version()` / `esTV()` / `aviso(texto)` | Información y avisos. |

## Datos guardados (en el teléfono)

| Clave | Contenido |
|---|---|
| `config` | Claves, listas de canales, agendas y fuentes. |
| `milista`, `recientes` | Mi lista y Seguir viendo. |
| `fav_canales` | IDs de canales favoritos (hash de la URL). |
| `cache_lista_<id>` | Copia de cada lista M3U (6 horas). |

## Servicios externos

| Servicio | Para qué | Clave |
|---|---|---|
| TMDB (`api.themoviedb.org`) | Catálogo, imágenes, tráileres, “Dónde verla” | `.env` `TMDB_API_KEY` o Ajustes |
| TheSportsDB | Agenda deportiva | `123` (gratuita) o la tuya |
| Jikan (`api.jikan.moe`) | Catálogo de anime (de MyAnimeList) | — |
| iptv-org | Listas públicas por defecto | — |

## Reglas de la interfaz

- Todo texto que viene de listas o internet pasa por `esc()` antes de ponerse en pantalla.
- Navegación con una pila (`App.ir`, `App.atras`); Android llama `App.atras()` con el botón Atrás.
- Cómo se abre un enlace (`App.reproducir`): YouTube → app externa; página web → visor (`#marco`,
  iframe sin ventanas emergentes); video → reproductor nativo.
- Modo TV: `html.tv`; menú lateral: `html.riel` (TV o pantalla de 900 px o más).

## Motor de proveedores (`proveedores/`)

Paquete nuevo, en construcción: busca fuentes de video para películas, series y
anime con varios "motores" que comparten la interfaz `MotorProveedor`:

| Archivo | Qué hace |
|---|---|
| `MotorProveedor.kt` | Contrato común (`soporta`, `buscar`). |
| `ProveedorPlantilla.kt` | Las 3 plantillas de hoy (Ajustes → Fuentes), como una implementación más. |
| `ReglasJson.kt` / `ProveedorJson.kt` | Motor genérico configurable por JSON (Jsoup + Regex), sin recompilar. Ver `docs/PROVEEDORES.md`. |
| `Sniffer.kt` | WebView oculto que detecta enlaces de video generados por JavaScript. |
| `ClienteEmbed.kt` | Cliente HTTP hacia un servidor TMDB-Embed-API propio (opcional). |
| `RepositorioProveedores.kt` | Junta todos los proveedores configurados y les pregunta en paralelo. |

Se llama desde la web con `Nativo.buscarFuentes(id, infoJson)` → responde a
`App.motores._fin(id, ok, fuentesJson)`. Por ahora solo existe el lado Kotlin;
falta conectar Ajustes y la pantalla "Ver" en la interfaz web.

## Versiones

`app/build.gradle.kts` → `versionCode` (+1 en cada entrega) y `versionName` (`X.Y.Z`). Ver `CHANGELOG.md`.
