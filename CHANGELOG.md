# Cambios

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
