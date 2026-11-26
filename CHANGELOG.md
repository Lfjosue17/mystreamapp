# Cambios

## [0.7.0] - 2026-11-26

### Agregado
- Deportes: nueva fuente automática de partidos en vivo, sin configurar nada. Si tienes activado
  "Servidor remoto" (lo mismo que ya usan Jikan y los proveedores), la app ahora también trae partidos
  de esa misma dirección, que da varios enlaces de transmisión por partido (no solo uno) y no tiene el
  límite de TheSportsDB. Se mezcla con lo que ya salía: si un partido aparece en ambas fuentes, junta
  los enlaces en vez de duplicar la tarjeta.
- La app ahora puede cargar su interfaz (los archivos que antes solo iban dentro del APK) desde el
  servidor, con la configuración de "Servidor remoto" ya guardada. Si el servidor no responde en 4
  segundos o falla, usa automáticamente la copia local que trae el APK — no se queda pegada esperando.
  Razón: a partir de este APK, un arreglo que sea solo de la interfaz (no de este archivo nativo) se
  sube con un `git push` al servidor y llega a tu teléfono sin tener que instalar un APK nuevo.

## [0.6.2] - 2026-11-26

### Arreglado
- Listas de canales (M3U): al agregar, activar/apagar o eliminar una lista en Ajustes, la pantalla TV
  seguía mostrando los canales viejos de memoria hasta tocar "Actualizar canales" o reiniciar la app.
  Ahora esas acciones limpian esa memoria, así que la lista nueva (o el cambio) sale en la próxima vez
  que entras a TV, sin pasos extra.
- Anime: el proxy de Jikan del servidor a veces daba HTTP 502 de forma persistente (la salida de Render
  hacia esa API puede ser lenta o quedar bloqueada). Ahora, si el camino por el servidor falla, se
  reintenta directo desde el teléfono en vez de mostrar el error — el catálogo ya no depende de un
  solo camino de red.

### Nota (sin cambio de código)
- Deportes: con la clave gratis de TheSportsDB (`123`) es normal ver solo un puñado de partidos reales
  por día, no el calendario completo — esa clave limita cuántos resultados devuelve por diseño (no es
  un error de la app). Para ver más partidos se necesitaría una clave personal de pago de TheSportsDB
  (desde unos \$3-9/mes); si no quieres pagar, se queda así.

## [0.6.1] - 2026-11-26

### Arreglado
- Deportes: a veces la lista de partidos salía vacía aunque sí había eventos ese día. Era TheSportsDB
  (la clave gratis `123` es compartida por todo el mundo y a veces responde con error o se demora) y el
  código lo tragaba en silencio sin avisar. Ahora: 1) si falla, reintenta una vez; 2) si aun así falla,
  se nota con un mensaje debajo de la lista (antes solo decía "No hay eventos" sin distinguir si de
  verdad no había o si fue un fallo de red); 3) se guarda en caché 90s por día/deporte para no repetir
  peticiones de más al cambiar de pestaña, que es lo que más dispara el límite de la clave compartida.

## [0.6.0] - 2026-11-26

### Agregado
- Deportes: botón **Estadísticas** en los eventos que vienen de TheSportsDB (liga, estado, sede, marcador
  y línea de tiempo de goles/tarjetas con minuto y jugador), usando solo `lookupevent.php`/`lookuptimeline.php`
  con la clave gratis sin registro. No incluye el gráfico de posesión/ataques en vivo (eso es de la API
  de pago de TheSportsDB o de otros servicios que piden registro, y no se quiso usar ninguno de pago).
  Los eventos que solo vienen de tus agendas propias (sin ID de TheSportsDB) no muestran este botón.
- Agendas: los enlaces ahora aceptan un campo opcional `"modo": "web"` para forzar que se abran en el
  visor web en vez del reproductor nativo (necesario para enlaces tipo iframe/embed sin extensión de
  video, como los de la nueva agenda de partidos en vivo de mystream-api v1.10.0, `/api/sports/agenda`).
  Sin ese campo, el comportamiento es el mismo de siempre.

## [0.5.1] - 2026-11-26

### Arreglado
- `ClienteEmbed.kt`: `readTimeout` de 20s a 25s, para que empareje con el nuevo tope de 15s por proveedor del servidor (mystream-api v1.9.1) y no corte la respuesta antes de que el servidor termine de juntar todos los proveedores.

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
