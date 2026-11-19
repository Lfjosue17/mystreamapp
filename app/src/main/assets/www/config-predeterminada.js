// =====================================================================
// CONFIGURACIÓN PREDETERMINADA
// Esto es lo que trae la app la PRIMERA vez que se abre (o al
// presionar "Restablecer" en Ajustes). Después, todo se cambia
// desde la app en Ajustes → Mis listas, y se guarda en el teléfono.
//
// Formatos y ejemplos: docs/LISTAS.md
// =====================================================================
window.App = window.App || {};

App.PREDETERMINADO = {
  version: 1,

  // Claves (también se pueden poner en la app, en Ajustes → Claves)
  tmdbKey: '',          // vacío = usa la de .env (TMDB_API_KEY)
  sportsdbKey: '123',   // clave gratuita pública de TheSportsDB
  idioma: 'es-MX',
  region: 'HN',         // país para "Dónde verla" y estrenos

  // Listas de canales en formato M3U
  listasCanales: [
    { id: 'iptv-hn',     nombre: 'Honduras (iptv-org)',   url: 'https://iptv-org.github.io/iptv/countries/hn.m3u',      activa: true },
    { id: 'iptv-spa',    nombre: 'En español (iptv-org)', url: 'https://iptv-org.github.io/iptv/languages/spa.m3u',     activa: true },
    { id: 'iptv-sports', nombre: 'Deportes (iptv-org)',   url: 'https://iptv-org.github.io/iptv/categories/sports.m3u', activa: false }
  ],

  // Agendas de eventos deportivos (JSON). Vacío: agrega las tuyas.
  agendas: [
    // { id: 'mi-agenda', nombre: 'Mi agenda', url: 'https://…/agenda.json', activa: true }
  ],

  // Fuentes para películas y series (plantillas de enlace). Vacío: agrega las tuyas.
  // Comodines: {tmdb} {imdb} {temporada} {episodio} {titulo} {mal}
  // ({mal} = ID de MyAnimeList, para la fuente de anime)
  fuentes: [
    // { id: 'mi-fuente', nombre: 'Mi servidor', pelicula: 'https://…/{tmdb}', serie: 'https://…/{tmdb}/{temporada}/{episodio}', anime: 'https://…/{mal}/{episodio}', activa: true }
  ],

  // Proveedores HTML configurables (motor Kotlin + Jsoup). No reemplaza M3U ni agendas JSON.
  proveedores: { version: 1, providers: [] }
};
