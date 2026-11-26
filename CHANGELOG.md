# Cambios

## [0.5.0] - 2026-11-26

### Agregado
- Anime: el catálogo (MyAnimeList/Jikan) ya no se pide directo desde el teléfono — pasa por el proxy nuevo del servidor (`/api/jikan/*`, mystream-api v1.9.0) cuando el servidor remoto está activo en Ajustes. El pedido directo a `api.jikan.moe` fallaba seguido (confirmado en wifi y datos móviles); el servidor sí le habla bien. Sin servidor remoto activo, sigue pidiendo directo como antes.

## [0.4.0] - 2026-11-23

### Agregado
- Buscar: filtro "Todos / Películas / Series" que ahora sí filtra los resultados de `/search/multi`
  (antes los chips de tipo solo afectaban a "Populares" sin texto escrito).
- TV en vivo: cuando hay más de una lista M3U activa, aparecen chips para filtrar por cuál la trajo
  (además de los filtros existentes por grupo, favoritos y búsqueda por nombre).
- Reproductor: subtítulos automáticos en español. `Sniffer.kt` ahora captura `.vtt`/`.srt` que vea
  pasar mientras busca el video; si no encuentra ninguno, se consulta Wyzie Subs (`sub.wyzie.ru`) como
  respaldo cuando hay TMDB ID (película o serie). La búsqueda tiene un tope de 4s para no demorar el
  inicio del video; si el idioma detectado de la fuente no parece doblaje en español (latino/castellano),
  el subtítulo se activa solo; si ya es doblaje en español, queda disponible mudo y se puede activar
  desde el engrane de ajustes de ExoPlayer.
- `Sniffer.kt`: filtrado de peticiones interceptadas antes de aceptar un enlace como video real —
  descarta por extensión (HTML/JS/CSS/imágenes/fuentes/JSON) y por lista de ~20 dominios de anuncios
  conocidos, y verifica el Content-Type real (con una petición `Range: bytes=0-0`, sin bajar el archivo
  completo) para evitar falsos positivos de URLs que "parecen" `.m3u8`/`.mp4` pero en realidad
  devuelven una página de error (causa de `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`). Si la
  verificación falla por red, se deja pasar el enlace (no se descarta por error).
- `Sniffer.kt`: simula un clic (y `video.play()`) 1.5s después de cargar la página, para sitios que
  solo sueltan el stream real tras una interacción del usuario. Cabeceras reales (Referer) se toman de
  la petición interceptada cuando están disponibles. Registro con `Log.d` (tag "Sniffer") de cada
  decisión para depurar con Logcat.

### Cambiado
- Reproductor: la detección de HLS ahora reconoce "m3u8", "hls" o "master" en cualquier parte de la
  URL (antes solo detectaba la extensión `.m3u8` o la ruta `/hls/`), para evitar
  `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` en streams sin extensión.
- `Puente.reproducir()` (nativo) ahora recibe también `tmdb`, `tipoContenido`, `temporada`, `episodio`
  e `idioma` (vacíos cuando no aplica, como canales de TV o enlaces de deportes).

### Nota
- `versionCode`/`versionName` de `app/build.gradle.kts` habían quedado en 0.2.0 (no se actualizaron
  en la entrega anterior); este cambio los sincroniza y salta a 0.4.0 para no repetir el "0.3.0" ya
  documentado abajo.
- Las mejoras de `Sniffer.kt` listadas arriba no se pudieron compilar aquí (no hay compilador de
  Kotlin en este entorno); quedan a la espera de que compiles tú con el comando de abajo.

## [0.3.0] - 2026-11-19

### Agregado
- Motor de proveedores configurable por JSON (Ajustes → Motor de proveedores): reglas de extracción
  (Jsoup/Regex, pasos encadenados) sin recompilar la app, con Media Sniffer (WebView oculto) para
  sitios que generan el enlace con JavaScript.
- Servidor remoto opcional (Ajustes → Servidor remoto): conecta tu propio TMDB-Embed-API (URL + API Key)
  para sumar sus fuentes a las del motor local.
- "Ver" ahora consulta el motor local y el servidor remoto, combina los resultados con tus plantillas
  de Fuentes y los ordena por idioma (español latino → España → subtitulado/original).

### Cambiado
- El puente nativo (`Puente.kt`) expone `consultarProveedor`/`validarProveedores` en vez de la función
  anterior `buscarFuentes`.

## [0.2.0] - 2026-11-17

### Agregado
- Sección **Anime** con catálogo de MyAnimeList (Jikan): temporada actual, al aire, mejor valorados,
  búsqueda y detalle con lista de episodios. No necesita clave.
- Las fuentes ahora aceptan un enlace para **anime** con el comodín `{mal}` (ID de MyAnimeList).
- El anime aparece también en "Seguir viendo" del Inicio.

## [0.1.0] - 2026-11-17

### Agregado
- Interfaz web dentro de la app (WebView) con Inicio, Buscar, Detalle, Deportes, TV en vivo y Ajustes.
- Catálogo de películas y series con TMDB: tendencias, populares, géneros, búsqueda, reparto, tráiler,
  temporadas/episodios y “Dónde verla” por país.
- Deportes: agenda del día por liga con TheSportsDB, filtros por día y deporte, y agendas propias en JSON
  que se juntan con la agenda automática.
- TV en vivo: listas M3U (iptv-org Honduras y Español por defecto), grupos, búsqueda, favoritos y caché sin internet.
- Ajustes: agregar/probar/apagar/borrar listas de canales, agendas y fuentes; claves; respaldo e importación.
- Reproductor nativo ExoPlayer (HLS, DASH, MP4) con User-Agent y Referer.
- Visor web sin ventanas emergentes y bloqueo de dominios de anuncios.
- Soporte para Android TV: menú lateral, navegación con control remoto y banner.
- Clave de TMDB y firma de release leídas desde `.env`.
- Documentación en `docs/`.
