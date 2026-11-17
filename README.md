# MyStream (mystreamapp)

App Android (teléfono y Android TV) para explorar películas y series, ver la agenda deportiva
del día y reproducir canales en vivo desde **listas que tú configuras**.

## Qué hace

- **Inicio y Buscar:** catálogo de películas y series de TMDB (tendencias, populares, géneros, búsqueda).
- **Anime:** catálogo de MyAnimeList (Jikan): temporada actual, al aire, mejor valorados y búsqueda.
- **Detalle:** sinopsis, reparto, tráiler (YouTube), temporadas y episodios, y **dónde verla** en Honduras.
- **Deportes:** partidos del día por liga (TheSportsDB) + tus propias agendas con enlaces.
- **TV en vivo:** canales de listas M3U (trae listas públicas de iptv-org), con grupos, búsqueda y favoritos.
- **Ajustes:** agregar, probar, apagar o borrar listas de canales, agendas y fuentes; respaldo de la configuración.
- Reproductor nativo (ExoPlayer) para HLS/DASH/MP4, con User-Agent y Referer por canal.
- Control remoto de Android TV, sin anuncios ni rastreadores.

## Documentación

- [docs/INSTALACION.md](docs/INSTALACION.md): cómo compilar e instalar.
- [docs/GUIA-DE-USO.md](docs/GUIA-DE-USO.md): cómo se usa cada pantalla.
- [docs/LISTAS.md](docs/LISTAS.md): formato de las listas M3U, agendas y fuentes.
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md): cómo está hecho por dentro.
- [CHANGELOG.md](CHANGELOG.md): cambios por versión.

## Datos de terceros

Este producto usa la API de TMDB pero no está avalado ni certificado por TMDB.
Agenda deportiva: TheSportsDB. “Dónde verla”: JustWatch (vía TMDB). Listas públicas: iptv-org.
