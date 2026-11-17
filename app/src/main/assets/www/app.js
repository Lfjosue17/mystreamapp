// =====================================================================
// INTERFAZ: pantallas, navegación y acciones de los botones.
// Pantallas: inicio, buscar, detalle, deportes, tv, ajustes.
// =====================================================================
'use strict';
(function (App) {
  const $ = s => document.querySelector(s);
  const vista = $('#vista');

  // ---------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------
  const esc = s => String(s === null || s === undefined ? '' : s)
    .replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const urlSegura = u => /^https?:\/\//i.test(String(u || '').trim()) ? String(u).trim() : '';
  const anio = f => String(f || '').slice(0, 4);
  const horaCorta = ms => new Date(ms).toLocaleTimeString('es', { hour: 'numeric', minute: '2-digit' });
  const ICONO_LUPA = '<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/></svg>';

  // ---------------------------------------------------------------
  // Modo TV / teléfono
  // ---------------------------------------------------------------
  const esTV = (() => {
    try { if (App.nativo && App.nativo.esTV) return !!App.nativo.esTV(); } catch (e) { /* nada */ }
    return /\b(tv|aft\w*|bravia|googletv|smart-tv)\b/i.test(navigator.userAgent);
  })();
  const raiz = document.documentElement;
  raiz.classList.add(esTV ? 'tv' : 'movil');
  function ajustarRiel() { raiz.classList.toggle('riel', esTV || window.innerWidth >= 900); }
  ajustarRiel();
  window.addEventListener('resize', ajustarRiel);

  // ---------------------------------------------------------------
  // Avisos, ventana emergente y visor web
  // ---------------------------------------------------------------
  let tAviso;
  function aviso(t) {
    const a = $('#aviso');
    a.textContent = t;
    a.classList.add('visible');
    clearTimeout(tAviso);
    tAviso = setTimeout(() => a.classList.remove('visible'), 2800);
  }
  App.aviso = aviso;

  let ultimoFoco = null;
  document.addEventListener('focusin', e => { if (!e.target.closest('#modal, #marco')) ultimoFoco = e.target; });
  function restaurarFoco() { if (ultimoFoco && document.contains(ultimoFoco)) ultimoFoco.focus({ preventScroll: true }); }

  function abrirModal(html) {
    const m = $('#modal');
    m.innerHTML = '<div class="modal-caja" role="dialog" aria-modal="true">' + html + '</div>';
    m.classList.remove('oculto');
    const f = m.querySelector('button, input, textarea');
    if (f && esTV) f.focus();
  }
  function cerrarModal() {
    const m = $('#modal');
    if (m.classList.contains('oculto')) return false;
    m.classList.add('oculto');
    m.innerHTML = '';
    restaurarFoco();
    return true;
  }
  $('#modal').addEventListener('click', e => { if (e.target.id === 'modal') cerrarModal(); });

  function abrirMarco(url, titulo) {
    $('#marco-titulo').textContent = titulo || '';
    $('#marco-iframe').src = url;
    $('#marco').classList.remove('oculto');
    $('#marco-cerrar').focus();
  }
  function cerrarMarco() {
    const m = $('#marco');
    if (m.classList.contains('oculto')) return false;
    $('#marco-iframe').src = 'about:blank';
    m.classList.add('oculto');
    restaurarFoco();
    return true;
  }
  $('#marco-cerrar').addEventListener('click', cerrarMarco);

  function abrirExterno(url) {
    if (App.nativo && App.nativo.abrirExterno) App.nativo.abrirExterno(url);
    else window.open(url, '_blank', 'noopener');
  }

  // ---------------------------------------------------------------
  // Reproducir
  //  - YouTube: se abre en la app de YouTube
  //  - páginas web (.html, .php…): visor web dentro de la app
  //  - todo lo demás: reproductor nativo (m3u8, mpd, mp4, ts…)
  // ---------------------------------------------------------------
  const esPaginaWeb = u => /\.(html?|php|aspx?)(\?|#|$)/i.test(u) || /\/embed\//i.test(u);
  const esVideoDirecto = u => /\.(m3u8|mpd|mp4|mkv|webm|ts|mp3|aac)(\?|#|$)/i.test(u);

  App.reproducir = function (url, titulo, op) {
    op = op || {};
    url = urlSegura(url);
    if (!url) return aviso('Este enlace no es válido');
    if (/(^|\.)youtube\.com|youtu\.be/i.test(url)) return abrirExterno(url);
    const web = op.tipo === 'fuente' ? !esVideoDirecto(url) : esPaginaWeb(url);
    if (web) return abrirMarco(url, titulo);
    const cab = {};
    if (op.ua) cab['User-Agent'] = op.ua;
    if (op.referer) cab.Referer = op.referer;
    if (App.nativo && App.nativo.reproducir) App.nativo.reproducir(url, titulo || '', JSON.stringify(cab));
    else window.open(url, '_blank', 'noopener');
  };

  // ---------------------------------------------------------------
  // Mi lista y recientes
  // ---------------------------------------------------------------
  const resumen = x => ({
    tipo: x.media_type || x.tipo, id: x.id,
    titulo: x.title || x.name || x.titulo || '',
    poster: x.poster_path || x.poster || '',
    anio: anio(x.release_date || x.first_air_date) || x.anio || '',
    nota: x.vote_average || x.nota || 0
  });
  const enMiLista = (tipo, id) => App.datos.leer('milista', []).some(x => x.tipo === tipo && String(x.id) === String(id));
  function alternarMiLista(item) {
    let l = App.datos.leer('milista', []);
    const ya = l.some(x => x.tipo === item.tipo && String(x.id) === String(item.id));
    l = ya ? l.filter(x => !(x.tipo === item.tipo && String(x.id) === String(item.id))) : [item].concat(l);
    App.datos.guardar('milista', l.slice(0, 300));
    aviso(ya ? 'Quitado de Mi lista' : 'Agregado a Mi lista');
    return !ya;
  }
  function registrarReciente(item) {
    const l = App.datos.leer('recientes', []).filter(x => !(x.tipo === item.tipo && String(x.id) === String(item.id)));
    l.unshift(item);
    App.datos.guardar('recientes', l.slice(0, 24));
  }

  // ---------------------------------------------------------------
  // Componentes
  // ---------------------------------------------------------------
  function tarjeta(x) {
    // Los recientes/Mi lista pueden traer anime; se dibuja con su propia tarjeta.
    if ((x.tipo || x.media_type) === 'anime') {
      const img = x.posterUrl ? '<img loading="lazy" alt="" src="' + esc(x.posterUrl) + '">' : '<div class="sin-img">' + esc(x.titulo || '') + '</div>';
      const sub = x.episodio ? 'Ep ' + x.episodio : 'Anime';
      return '<button class="tarjeta foco" data-accion="anime-detalle" data-id="' + esc(x.id) + '">' +
        '<div class="poster">' + img + '</div>' +
        '<span class="t-titulo">' + esc(x.titulo || '') + '</span><span class="t-sub">' + esc(sub) + '</span></button>';
    }
    const it = resumen(x);
    if (!it.id || (it.tipo !== 'movie' && it.tipo !== 'tv')) return '';
    const img = it.poster
      ? '<img loading="lazy" alt="" src="' + esc(App.tmdb.img(it.poster, 'w342')) + '">'
      : '<div class="sin-img">' + esc(it.titulo) + '</div>';
    const nota = it.nota ? '<span class="nota-tmdb">★ ' + Number(it.nota).toFixed(1) + '</span>' : '';
    let sub = it.anio + (it.tipo === 'tv' ? ' · Serie' : '');
    if (x.temporada) sub = 'T' + x.temporada + ' · E' + x.episodio;
    return '<button class="tarjeta foco" data-accion="detalle" data-tipo="' + it.tipo + '" data-id="' + esc(it.id) + '">' +
      '<div class="poster">' + img + nota + '</div>' +
      '<span class="t-titulo">' + esc(it.titulo) + '</span><span class="t-sub">' + esc(sub) + '</span></button>';
  }

  function fila(titulo, items, tipoPorDefecto) {
    const html = (items || []).map(x => tarjeta(tipoPorDefecto && !x.media_type && !x.tipo ? Object.assign({ media_type: tipoPorDefecto }, x) : x)).join('');
    if (!html) return '';
    return '<section class="fila"><h2>' + esc(titulo) + '</h2><div class="carril">' + html + '</div></section>';
  }

  function vacio(titulo, texto, boton) {
    return '<div class="vacio"><h3>' + titulo + '</h3><p>' + texto + '</p>' + (boton || '') + '</div>';
  }

  function errorHtml(err) {
    if (err && err.message === 'SIN_CLAVE') return sinClaveHtml();
    if (err && /HTTP 401/.test(err.message)) {
      return vacio('La clave de TMDB no funciona', 'Revisa que la copiaste completa en Ajustes → Claves.',
        '<button class="btn foco" data-accion="ir" data-a="ajustes">Ir a Ajustes</button>');
    }
    return vacio('Algo salió mal', esc(err && err.message ? err.message : String(err)) +
      '<br>Revisa tu conexión a internet.', '<button class="btn foco" data-accion="recargar">Reintentar</button>');
  }

  function sinClaveHtml() {
    return vacio('Falta la clave de TMDB',
      'El catálogo de películas y series viene de TMDB. Pide una clave gratis en ' +
      '<b>themoviedb.org → Ajustes → API</b> y pégala en Ajustes → Claves.',
      '<button class="btn foco" data-accion="ir" data-a="ajustes">Ir a Ajustes</button>');
  }

  // ---------------------------------------------------------------
  // Navegación entre pantallas
  // ---------------------------------------------------------------
  const PESTANAS = ['inicio', 'buscar', 'anime', 'deportes', 'tv', 'ajustes'];
  const pantallas = {};
  let pila = [];
  let turno = 0;

  App.ir = function (nombre, params) {
    if (pila.length) pila[pila.length - 1].scroll = window.scrollY;
    if (PESTANAS.includes(nombre)) pila = [];
    pila.push({ nombre, params: params || {} });
    pintar(false);
  };

  App.atras = function () {
    if (cerrarModal()) return true;
    if (cerrarMarco()) return true;
    if (pila.length > 1) { pila.pop(); pintar(true); return true; }
    if (pila.length && pila[0].nombre !== 'inicio') { App.ir('inicio'); return true; }
    return false;
  };

  function cima() { return pila[pila.length - 1]; }

  /** Vuelve a dibujar la pantalla actual sin perder la posición. */
  function repintar() {
    if (cima()) cima().scroll = window.scrollY;
    pintar(true);
  }

  /** Escribe en pantalla solo si el usuario no se fue a otra pantalla mientras cargaba. */
  function poner(miTurno, html) {
    if (miTurno !== turno) return false;
    vista.innerHTML = html;
    return true;
  }

  function pintar(restaurar) {
    const c = cima();
    const miTurno = ++turno;
    const pestana = pila[0].nombre;
    document.querySelectorAll('[data-pestana]').forEach(b => b.classList.toggle('activa', b.dataset.pestana === pestana));
    vista.innerHTML = '<div class="cargando"><span></span></div>';
    window.scrollTo(0, 0);
    Promise.resolve()
      .then(() => pantallas[c.nombre](c.params, miTurno))
      .catch(err => { console.error(err); poner(miTurno, errorHtml(err)); })
      .then(() => {
        if (miTurno !== turno) return;
        if (restaurar && c.scroll) window.scrollTo(0, c.scroll);
        if (esTV && !vista.contains(document.activeElement)) {
          const f = vista.querySelector('.foco, input');
          if (f) f.focus({ preventScroll: true });
        }
      });
  }

  // =================================================================
  // PANTALLA: INICIO
  // =================================================================
  pantallas.inicio = async function (p, t) {
    const c = App.config.obtener();
    const recientes = App.datos.leer('recientes', []);
    const miLista = App.datos.leer('milista', []);
    const propias = fila('Seguir viendo', recientes) + fila('Mi lista', miLista);

    if (!App.tmdb.hayClave()) { poner(t, sinClaveHtml() + propias); return; }

    const tend = await App.tmdb.get('/trending/all/week');   // si la clave es mala, falla aquí
    const vacioR = { results: [] };
    const [pelis, series, mejor, cartelera, seriesMejor] = await Promise.all([
      App.tmdb.get('/movie/popular', { region: c.region }),
      App.tmdb.get('/tv/popular'),
      App.tmdb.get('/movie/top_rated'),
      App.tmdb.get('/movie/now_playing', { region: c.region }),
      App.tmdb.get('/tv/top_rated')
    ].map(pr => pr.catch(() => vacioR)));

    const destacados = (tend.results || []).filter(x => x.backdrop_path && (x.media_type === 'movie' || x.media_type === 'tv'));
    const h = destacados[Math.floor(Math.random() * Math.min(6, destacados.length))];
    let hero = '';
    if (h) {
      const tipoTxt = h.media_type === 'tv' ? 'Serie' : 'Película';
      hero = '<section class="hero"><div class="hero-fondo" style="background-image:url(\'' + esc(App.tmdb.img(h.backdrop_path, 'w1280')) + '\')"></div>' +
        '<div class="hero-info"><span class="etiqueta">Tendencia · ' + tipoTxt + '</span>' +
        '<h1>' + esc(h.title || h.name) + '</h1><p>' + esc(h.overview) + '</p>' +
        '<div class="acciones"><button class="btn foco" data-accion="detalle" data-tipo="' + h.media_type + '" data-id="' + h.id + '">Ver detalles</button></div></div></section>';
    }

    poner(t, hero + propias +
      fila('Tendencias de la semana', tend.results) +
      fila('En cartelera', cartelera.results, 'movie') +
      fila('Películas populares', pelis.results, 'movie') +
      fila('Series populares', series.results, 'tv') +
      fila('Películas mejor valoradas', mejor.results, 'movie') +
      fila('Series mejor valoradas', seriesMejor.results, 'tv'));
  };

  // =================================================================
  // PANTALLA: BUSCAR
  // =================================================================
  pantallas.buscar = async function (p, t) {
    p.tipo = p.tipo || 'movie';
    if (!App.tmdb.hayClave()) { poner(t, sinClaveHtml()); return; }
    const generos = await App.tmdb.get('/genre/' + p.tipo + '/list');
    const chipsTipo = [['movie', 'Películas'], ['tv', 'Series']].map(([k, n]) =>
      '<button class="chip foco' + (p.tipo === k ? ' activa' : '') + '" data-accion="buscar-tipo" data-v="' + k + '">' + n + '</button>').join('');
    const chipsGen = (generos.genres || []).map(g =>
      '<button class="chip foco' + (String(p.genero) === String(g.id) ? ' activa' : '') + '" data-accion="buscar-genero" data-v="' + g.id + '">' + esc(g.name) + '</button>').join('');
    if (!poner(t,
      '<div class="cabecera"><label class="campo-busqueda">' + ICONO_LUPA +
      '<input id="q" type="search" placeholder="Película o serie…" autocomplete="off" value="' + esc(p.q || '') + '"></label></div>' +
      '<div class="chips">' + chipsTipo + '</div><div class="chips">' + chipsGen + '</div>' +
      '<div id="resultados"><div class="cargando"><span></span></div></div>')) return;

    const input = $('#q');
    let espera;
    input.addEventListener('input', () => {
      clearTimeout(espera);
      espera = setTimeout(() => { p.q = input.value.trim(); resultadosBusqueda(p); }, 450);
    });
    input.addEventListener('keydown', e => { if (e.key === 'Enter') { clearTimeout(espera); p.q = input.value.trim(); resultadosBusqueda(p); input.blur(); } });
    if (!esTV && !p.q && !p.genero) input.focus();
    await resultadosBusqueda(p);
  };

  let turnoBusqueda = 0;
  async function resultadosBusqueda(p) {
    const caja = $('#resultados');
    if (!caja) return;
    const mio = ++turnoBusqueda;
    caja.innerHTML = '<div class="cargando"><span></span></div>';
    let r, titulo;
    try {
      if (p.q) {
        r = await App.tmdb.get('/search/multi', { query: p.q, include_adult: 'false' });
        titulo = 'Resultados para “' + esc(p.q) + '”';
      } else {
        const params = { sort_by: 'popularity.desc', 'vote_count.gte': '50' };
        if (p.genero) params.with_genres = p.genero;
        r = await App.tmdb.get('/discover/' + p.tipo, params);
        r.results = (r.results || []).map(x => Object.assign({ media_type: p.tipo }, x));
        titulo = p.genero ? 'Populares en este género' : 'Populares';
      }
    } catch (e) {
      if (mio === turnoBusqueda && caja.isConnected) caja.innerHTML = errorHtml(e);
      return;
    }
    if (mio !== turnoBusqueda || !caja.isConnected) return;
    const html = (r.results || []).map(tarjeta).join('');
    caja.innerHTML = html
      ? '<p class="nota">' + titulo + '</p><div class="rejilla">' + html + '</div>'
      : vacio('Sin resultados', 'Prueba con otro nombre.');
  }

  // =================================================================
  // PANTALLA: DETALLE (película o serie)
  // =================================================================
  pantallas.detalle = async function (p, t) {
    const c = App.config.obtener();
    const d = await App.tmdb.get('/' + p.tipo + '/' + p.id, {
      append_to_response: 'videos,credits,similar,watch/providers,external_ids',
      include_video_language: 'es,en,null'
    });
    const esSerie = p.tipo === 'tv';
    const titulo = d.title || d.name || '';
    const item = resumen(Object.assign({ media_type: p.tipo }, d));
    registrarReciente(item);

    const videos = ((d.videos && d.videos.results) || []).filter(v => v.site === 'YouTube');
    const trailer = videos.find(v => v.type === 'Trailer' && v.iso_639_1 === 'es') || videos.find(v => v.type === 'Trailer') || videos[0];

    const meta = [];
    if (d.vote_average) meta.push('<span class="estrella">★ ' + d.vote_average.toFixed(1) + '</span>');
    const fechaEstreno = anio(d.release_date || d.first_air_date);
    if (fechaEstreno) meta.push('<span>' + esc(fechaEstreno) + '</span>');
    if (d.runtime) meta.push('<span>' + Math.floor(d.runtime / 60) + ' h ' + (d.runtime % 60) + ' min</span>');
    if (esSerie && d.number_of_seasons) meta.push('<span>' + d.number_of_seasons + (d.number_of_seasons === 1 ? ' temporada' : ' temporadas') + '</span>');

    const guardado = enMiLista(p.tipo, p.id);
    estadoDetalle = { tipo: p.tipo, id: d.id, titulo, imdb: (d.external_ids && d.external_ids.imdb_id) || d.imdb_id || '', item, trailer };

    let html =
      '<section class="det-cab">' +
      (d.backdrop_path ? '<div class="hero-fondo" style="background-image:url(\'' + esc(App.tmdb.img(d.backdrop_path, 'w1280')) + '\')"></div>' : '') +
      (esTV ? '' : '<button class="btn sec peq det-volver" data-accion="atras">← Volver</button>') +
      '<div class="det-info">' +
      (d.poster_path ? '<div class="det-poster"><img alt="" src="' + esc(App.tmdb.img(d.poster_path, 'w342')) + '"></div>' : '') +
      '<div><h1>' + esc(titulo) + '</h1><div class="meta">' + meta.join('') + '</div>' +
      '<div class="generos">' + (d.genres || []).map(g => '<span>' + esc(g.name) + '</span>').join('') + '</div></div>' +
      '</div></section><div class="det-cuerpo">' +
      '<div class="acciones">' +
      '<button class="btn foco" data-accion="ver"' + (esSerie ? ' data-t="1" data-e="1"' : '') + '>▶ ' + (esSerie ? 'Ver T1 · E1' : 'Ver') + '</button>' +
      (trailer ? '<button class="btn sec foco" data-accion="trailer">Tráiler</button>' : '') +
      '<button class="btn sec foco" data-accion="milista" id="btn-milista">' + (guardado ? '✓ En Mi lista' : '＋ Mi lista') + '</button>' +
      '</div>' +
      (d.overview ? '<p class="sinopsis">' + esc(d.overview) + '</p>' : '');

    // Dónde verla (datos de JustWatch vía TMDB)
    const prov = d['watch/providers'] && d['watch/providers'].results && d['watch/providers'].results[c.region];
    if (prov) {
      const grupos = [['flatrate', 'Suscripción'], ['free', 'Gratis'], ['ads', 'Con anuncios'], ['rent', 'Alquiler'], ['buy', 'Compra']];
      const vistos = new Set();
      const tarjetas = [];
      grupos.forEach(([k, nombre]) => (prov[k] || []).forEach(x => {
        if (vistos.has(x.provider_id)) return;
        vistos.add(x.provider_id);
        tarjetas.push('<div class="prov"><img alt="" src="' + esc(App.tmdb.img(x.logo_path, 'w92')) + '"><span>' + esc(x.provider_name) + '<br><small>' + nombre + '</small></span></div>');
      }));
      if (tarjetas.length) {
        html += '<h3 class="seccion-titulo">Dónde verla en ' + esc(c.region) + '</h3><div class="proveedores">' + tarjetas.join('') + '</div>' +
          '<p class="nota" style="padding:6px 0 0">Datos de JustWatch.</p>';
      }
    }

    if (esSerie) {
      const temporadas = (d.seasons || []).filter(s => s.season_number > 0 && s.episode_count > 0);
      if (temporadas.length) {
        p.temporada = p.temporada || temporadas[0].season_number;
        html += '<h3 class="seccion-titulo">Episodios</h3><div class="chips" style="padding-left:0;padding-right:0">' +
          temporadas.map(s => '<button class="chip foco' + (s.season_number === p.temporada ? ' activa' : '') + '" data-accion="temporada" data-v="' + s.season_number + '">Temporada ' + s.season_number + '</button>').join('') +
          '</div><div id="episodios" class="episodios"><div class="cargando"><span></span></div></div>';
      }
    }

    const reparto = ((d.credits && d.credits.cast) || []).slice(0, 15);
    if (reparto.length) {
      html += '<h3 class="seccion-titulo">Reparto</h3><div class="reparto">' + reparto.map(a =>
        '<div class="persona"><div class="foto">' + (a.profile_path ? '<img loading="lazy" alt="" src="' + esc(App.tmdb.img(a.profile_path, 'w185')) + '">' : '') + '</div>' +
        esc(a.name) + '<small>' + esc(a.character || '') + '</small></div>').join('') + '</div>';
    }
    html += '</div>' + fila('Parecidas', (d.similar && d.similar.results) || [], p.tipo);

    if (!poner(t, html)) return;
    if (esSerie && p.temporada) cargarEpisodios(p);
  };

  let estadoDetalle = null;

  async function cargarEpisodios(p) {
    const caja = $('#episodios');
    if (!caja) return;
    caja.innerHTML = '<div class="cargando"><span></span></div>';
    try {
      const s = await App.tmdb.get('/tv/' + p.id + '/season/' + p.temporada);
      if (!caja.isConnected) return;
      caja.innerHTML = (s.episodes || []).map(e =>
        '<button class="episodio foco" data-accion="ver" data-t="' + p.temporada + '" data-e="' + e.episode_number + '">' +
        '<div class="ep-img">' + (e.still_path ? '<img loading="lazy" alt="" src="' + esc(App.tmdb.img(e.still_path, 'w300')) + '">' : '') + '</div>' +
        '<div><b>' + e.episode_number + '. ' + esc(e.name) + '</b><p>' + esc(e.overview || '') + '</p></div></button>').join('') ||
        '<p class="nota">No hay episodios.</p>';
    } catch (e) {
      if (caja.isConnected) caja.innerHTML = '<p class="nota error-txt">No se pudieron cargar los episodios.</p>';
    }
  }

  // ---------- Ver con tus fuentes ----------
  let opcionesVer = null;

  function plantilla(texto, info) {
    const valores = {
      tmdb: info.id, imdb: info.imdb || '', mal: info.mal || info.id, temporada: info.temporada || 1,
      episodio: info.episodio || 1, titulo: info.titulo || '', tipo: info.tipo === 'movie' ? 'pelicula' : (info.tipo === 'anime' ? 'anime' : 'serie')
    };
    return String(texto).replace(/\{(tmdb|imdb|mal|temporada|episodio|titulo|tipo)\}/g, (_, k) => encodeURIComponent(valores[k]));
  }

  function ver(info) {
    const campo = info.tipo === 'movie' ? 'pelicula' : (info.tipo === 'anime' ? 'anime' : 'serie');
    const nombreTipo = info.tipo === 'movie' ? 'películas' : (info.tipo === 'anime' ? 'anime' : 'series');
    const fuentes = App.config.obtener().fuentes.filter(f => f.activa !== false && f[campo]);
    if (!fuentes.length) {
      return abrirModal('<h3>No tienes fuentes</h3><p>Para ver ' + nombreTipo +
        ' agrega una fuente en <b>Ajustes → Fuentes</b> (con enlace de ' + nombreTipo + ').</p>' +
        '<div class="acciones"><button class="btn foco" data-accion="ir" data-a="ajustes">Ir a Ajustes</button>' +
        '<button class="btn sec foco" data-accion="cerrar-modal">Cerrar</button></div>');
    }
    const abrir = f => {
      const conEp = info.tipo === 'tv' || info.tipo === 'anime';
      const etiqueta = conEp ? info.titulo + ' · E' + info.episodio : info.titulo;
      App.reproducir(plantilla(f[campo], info), etiqueta, { tipo: 'fuente' });
      if (conEp && estadoDetalle) registrarReciente(Object.assign({}, estadoDetalle.item, { temporada: info.temporada, episodio: info.episodio }));
    };
    if (fuentes.length === 1) { cerrarModal(); return abrir(fuentes[0]); }
    opcionesVer = { info, fuentes, abrir };
    abrirModal('<h3>Elige una fuente</h3><div class="lista-opciones">' +
      fuentes.map((f, i) => '<button class="opcion foco" data-accion="usar-fuente" data-i="' + i + '">▶ ' + esc(f.nombre) + '</button>').join('') +
      '</div><div class="acciones"><button class="btn sec foco" data-accion="cerrar-modal">Cancelar</button></div>');
  }

  // =================================================================
  // PANTALLA: ANIME (catálogo de Jikan / MyAnimeList)
  // =================================================================
  function tarjetaAnime(a) {
    if (!a || !a.mal_id) return '';
    const img = (a.images && a.images.jpg && (a.images.jpg.image_url)) || '';
    const nota = a.score ? '<span class="nota-tmdb">★ ' + a.score + '</span>' : '';
    const sub = (a.type || '') + (a.year ? ' · ' + a.year : '');
    return '<button class="tarjeta foco" data-accion="anime-detalle" data-id="' + a.mal_id + '">' +
      '<div class="poster">' + (img ? '<img loading="lazy" alt="" src="' + esc(img) + '">' : '<div class="sin-img">' + esc(a.title) + '</div>') + nota + '</div>' +
      '<span class="t-titulo">' + esc(a.title) + '</span><span class="t-sub">' + esc(sub) + '</span></button>';
  }

  function filaAnime(titulo, lista) {
    const html = (lista || []).map(tarjetaAnime).join('');
    return html ? '<section class="fila"><h2>' + esc(titulo) + '</h2><div class="carril">' + html + '</div></section>' : '';
  }

  pantallas.anime = async function (p, t) {
    if (p.q) { await buscarAnime(p, t); return; }
    const cab = '<div class="cabecera"><label class="campo-busqueda">' + ICONO_LUPA +
      '<input id="q-anime" type="search" placeholder="Buscar anime…" autocomplete="off"></label></div>';
    const temporada = await App.jikan.get('/seasons/now', { limit: 20, sfw: 'true' });
    const vacioR = { data: [] };
    const [top, mejores] = await Promise.all([
      App.jikan.get('/top/anime', { filter: 'airing', limit: 20, sfw: 'true' }).catch(() => vacioR),
      App.jikan.get('/top/anime', { limit: 20, sfw: 'true' }).catch(() => vacioR)
    ]);
    if (!poner(t, cab +
      filaAnime('Temporada actual', temporada.data) +
      filaAnime('Al aire ahora', top.data) +
      filaAnime('Mejor valorados', mejores.data))) return;
    const input = $('#q-anime');
    let espera;
    input.addEventListener('input', () => {
      clearTimeout(espera);
      espera = setTimeout(() => { if (input.value.trim()) App.ir('anime', { q: input.value.trim() }); }, 500);
    });
    if (!esTV) input.focus();
  };

  async function buscarAnime(p, t) {
    const cab = '<div class="cabecera"><label class="campo-busqueda">' + ICONO_LUPA +
      '<input id="q-anime" type="search" placeholder="Buscar anime…" autocomplete="off" value="' + esc(p.q) + '"></label></div>';
    const r = await App.jikan.get('/anime', { q: p.q, limit: 24, sfw: 'true', order_by: 'members', sort: 'desc' });
    const html = (r.data || []).map(tarjetaAnime).join('');
    if (!poner(t, cab + (html
      ? '<p class="nota">Resultados para “' + esc(p.q) + '”</p><div class="rejilla">' + html + '</div>'
      : vacio('Sin resultados', 'Prueba con otro nombre.')))) return;
    const input = $('#q-anime');
    let espera;
    input.addEventListener('input', () => {
      clearTimeout(espera);
      espera = setTimeout(() => App.ir('anime', input.value.trim() ? { q: input.value.trim() } : {}), 500);
    });
    input.addEventListener('keydown', e => { if (e.key === 'Enter') input.blur(); });
  }

  pantallas.animeDetalle = async function (p, t) {
    const r = await App.jikan.get('/anime/' + p.id + '/full');
    const a = r.data;
    if (!a) { poner(t, vacio('No encontrado', 'No se pudo cargar este anime.')); return; }
    const titulo = (a.title_english || a.title || '').trim();
    const total = a.episodes || 0;
    const item = { tipo: 'anime', id: a.mal_id, titulo, poster: '', anio: a.year || '', nota: a.score || 0, anime: true };
    item.posterUrl = (a.images && a.images.jpg && a.images.jpg.image_url) || '';
    registrarReciente(item);
    estadoDetalle = { tipo: 'anime', id: a.mal_id, mal: a.mal_id, titulo, item, trailer: (a.trailer && a.trailer.youtube_id) ? { key: a.trailer.youtube_id } : null };

    const img = (a.images && a.images.jpg && (a.images.jpg.large_image_url || a.images.jpg.image_url)) || '';
    const meta = [];
    if (a.score) meta.push('<span class="estrella">★ ' + a.score + '</span>');
    if (a.year) meta.push('<span>' + esc(a.year) + '</span>');
    if (a.type) meta.push('<span>' + esc(a.type) + '</span>');
    if (total) meta.push('<span>' + total + ' episodios</span>');
    else if (a.status) meta.push('<span>' + esc(a.status) + '</span>');
    const generos = (a.genres || []).concat(a.themes || []).slice(0, 6);

    let html = '<section class="det-cab">' +
      (img ? '<div class="hero-fondo" style="background-image:url(\'' + esc(img) + '\')"></div>' : '') +
      (esTV ? '' : '<button class="btn sec peq det-volver" data-accion="atras">← Volver</button>') +
      '<div class="det-info">' +
      (img ? '<div class="det-poster"><img alt="" src="' + esc(img) + '"></div>' : '') +
      '<div><h1>' + esc(titulo) + '</h1><div class="meta">' + meta.join('') + '</div>' +
      '<div class="generos">' + generos.map(g => '<span>' + esc(g.name) + '</span>').join('') + '</div></div>' +
      '</div></section><div class="det-cuerpo">' +
      '<div class="acciones">' +
      '<button class="btn foco" data-accion="ver-anime" data-e="1">▶ Ver episodio 1</button>' +
      (estadoDetalle.trailer ? '<button class="btn sec foco" data-accion="trailer">Tráiler</button>' : '') +
      '<button class="btn sec foco" data-accion="milista" id="btn-milista">' + (enMiLista('anime', a.mal_id) ? '✓ En Mi lista' : '＋ Mi lista') + '</button>' +
      '</div>' +
      (a.synopsis ? '<p class="sinopsis">' + esc(a.synopsis) + '</p>' : '');

    // Lista de episodios para elegir cuál ver (hasta 100)
    if (total && total > 1) {
      const n = Math.min(total, 100);
      let chips = '';
      for (let e = 1; e <= n; e++) chips += '<button class="chip foco" data-accion="ver-anime" data-e="' + e + '">Ep ' + e + '</button>';
      if (total > 100) chips += '<span class="chip" style="opacity:.6">…</span>';
      html += '<h3 class="seccion-titulo">Episodios</h3><div class="chips" style="flex-wrap:wrap;padding-left:0;padding-right:0">' + chips + '</div>';
    }
    html += '<p class="nota" style="padding-left:0">Datos de MyAnimeList (Jikan).</p></div>';
    poner(t, html);
  };

  // =================================================================
  // PANTALLA: DEPORTES
  // =================================================================
  const NOMBRES_DEPORTE = {
    todos: 'Todos', futbol: 'Fútbol', baloncesto: 'Baloncesto', beisbol: 'Béisbol',
    americano: 'Fútbol americano', motor: 'Motor', combate: 'Combate', tenis: 'Tenis'
  };
  let eventosActuales = [];

  pantallas.deportes = async function (p, t) {
    p.dia = p.dia === undefined ? 0 : p.dia;
    p.deporte = p.deporte || 'futbol';
    const hoy = new Date();
    const dias = [-1, 0, 1, 2, 3].map(n => {
      const d = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() + n);
      const nombre = n === -1 ? 'Ayer' : n === 0 ? 'Hoy' : n === 1 ? 'Mañana'
        : d.toLocaleDateString('es', { weekday: 'short', day: 'numeric' });
      return '<button class="chip foco' + (p.dia === n ? ' activa' : '') + '" data-accion="dia" data-v="' + n + '">' + esc(nombre) + '</button>';
    }).join('');
    const deportes = Object.keys(NOMBRES_DEPORTE).map(k =>
      '<button class="chip foco' + (p.deporte === k ? ' activa' : '') + '" data-accion="deporte" data-v="' + k + '">' + NOMBRES_DEPORTE[k] + '</button>').join('');
    const cab = '<div class="cabecera"><h1>Deportes</h1><button class="btn sec peq foco" data-accion="ir" data-a="tv" data-grupo="__deportes">Canales de deportes</button></div>' +
      '<div class="chips">' + dias + '</div><div class="chips">' + deportes + '</div>';
    if (!poner(t, cab + '<div class="cargando"><span></span></div>')) return;

    const fecha = App.fechaLocal(new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() + p.dia));
    const { eventos, errores } = await App.deportes.eventosDelDia(fecha, p.deporte === 'todos' ? '' : p.deporte);
    eventosActuales = eventos;

    let html = cab;
    if (!eventos.length) {
      html += vacio('No hay eventos', 'No se encontraron eventos para este día.<br>Puedes agregar tus propias agendas en Ajustes → Agendas.');
    } else {
      const ahora = Date.now();
      const porLiga = new Map();
      eventos.forEach((e, i) => {
        const k = e.liga || 'Otros';
        if (!porLiga.has(k)) porLiga.set(k, []);
        porLiga.get(k).push(i);
      });
      porLiga.forEach((indices, liga) => {
        html += '<div class="liga-titulo">' + esc(liga) + '</div><div class="eventos">' + indices.map(i => {
          const e = eventosActuales[i];
          const vivo = ahora >= e.inicio && ahora <= e.inicio + 2.5 * 3600e3 && !/finished|ft|terminado|final/i.test(e.estado);
          const escudo = src => urlSegura(src) ? '<img loading="lazy" alt="" src="' + esc(src) + '">' : '<span class="escudo"></span>';
          const equipos = e.local || e.visita
            ? '<div class="equipo">' + escudo(e.escudoLocal) + esc(e.local) + '</div><div class="equipo">' + escudo(e.escudoVisita) + esc(e.visita) + '</div>'
            : '<div class="equipo">' + esc(e.titulo) + '</div>';
          const lado = (e.marcador ? '<div class="marcador">' + esc(e.marcador) + '</div>' : '') +
            (vivo ? '<span class="en-vivo">EN VIVO</span>' : '') +
            (e.enlaces.length ? '<div class="enlaces-n">▶ ' + e.enlaces.length + '</div>' : '');
          return '<button class="evento foco" data-accion="evento" data-i="' + i + '">' +
            '<div class="hora">' + horaCorta(e.inicio) + '</div><div class="equipos">' + equipos + '</div><div class="lado">' + lado + '</div></button>';
        }).join('') + '</div>';
      });
    }
    html += '<p class="nota" style="margin-top:16px">Agenda: TheSportsDB' +
      (App.config.obtener().agendas.some(a => a.activa !== false) ? ' + tus agendas' : '') + '. Horas en tu zona horaria.</p>';
    if (errores.length) html += '<p class="nota error-txt">' + errores.map(esc).join('<br>') + '</p>';
    poner(t, html);
  };

  function abrirEvento(i) {
    const e = eventosActuales[i];
    if (!e) return;
    const fecha = new Date(e.inicio).toLocaleString('es', { weekday: 'long', day: 'numeric', month: 'long', hour: 'numeric', minute: '2-digit' });
    const enlaces = e.enlaces.length
      ? '<div class="lista-opciones">' + e.enlaces.map((x, j) =>
        '<button class="opcion foco" data-accion="enlace" data-ev="' + i + '" data-i="' + j + '">▶ ' + esc(x.nombre) + '<small>' + esc(x.origen || e.origen) + '</small></button>').join('') + '</div>'
      : '<p class="nota" style="padding:8px 0">Este evento no tiene enlaces en tus agendas.</p>';
    abrirModal('<h3>' + esc(e.titulo) + '</h3><p>' + esc(e.liga) + '<br>' + esc(fecha) +
      (e.marcador ? '<br><b>' + esc(e.marcador) + '</b>' : '') + '</p>' + enlaces +
      '<div class="acciones"><button class="btn sec foco" data-accion="buscar-canal">Canales de deportes</button>' +
      '<button class="btn sec foco" data-accion="cerrar-modal">Cerrar</button></div>');
  }

  // =================================================================
  // PANTALLA: TV EN VIVO
  // =================================================================
  const POR_PAGINA = 120;
  let canalesFiltrados = [];
  let mostrados = 0;

  pantallas.tv = async function (p, t) {
    const forzar = !!p.forzar;
    delete p.forzar;
    const datos = await App.canales.cargar(forzar);
    const favoritos = App.datos.leer('fav_canales', []);
    const conteo = new Map();
    datos.canales.forEach(ch => { const g = ch.grupo || 'Sin grupo'; conteo.set(g, (conteo.get(g) || 0) + 1); });
    const grupos = Array.from(conteo.entries()).sort((a, b) => b[1] - a[1]).slice(0, 40).map(x => x[0]);
    const hayDeportes = datos.canales.some(ch => /sport|deport/i.test(ch.grupo));
    p.grupo = p.grupo || '__todos';

    const chip = (v, nombre) => '<button class="chip foco' + (p.grupo === v ? ' activa' : '') + '" data-accion="grupo" data-v="' + esc(v) + '">' + esc(nombre) + '</button>';
    let chips = chip('__todos', 'Todos') + chip('__fav', '★ Favoritos') + (hayDeportes ? chip('__deportes', 'Deportes') : '');
    chips += grupos.map(g => chip(g, g)).join('');

    let html = '<div class="cabecera"><h1>TV en vivo</h1>' +
      '<label class="campo-busqueda">' + ICONO_LUPA + '<input id="q-canal" type="search" placeholder="Buscar canal…" autocomplete="off" value="' + esc(p.q || '') + '"></label>' +
      '<button class="btn sec peq foco" data-accion="actualizar-canales">⟳ Actualizar</button></div>' +
      '<div class="chips">' + chips + '</div><div id="lista-canales"></div>';
    if (datos.errores.length) html += '<p class="nota error-txt">' + datos.errores.map(esc).join('<br>') + '</p>';
    if (!datos.listas) {
      html = '<div class="cabecera"><h1>TV en vivo</h1></div>' +
        vacio('No tienes listas activas', 'Agrega o activa una lista M3U en Ajustes → Listas de canales.',
          '<button class="btn foco" data-accion="ir" data-a="ajustes">Ir a Ajustes</button>');
    }
    if (!poner(t, html)) return;
    if (!datos.listas) return;

    const input = $('#q-canal');
    let espera;
    input.addEventListener('input', () => {
      clearTimeout(espera);
      espera = setTimeout(() => { p.q = input.value.trim(); filtrarCanales(p, datos, favoritos); }, 250);
    });
    filtrarCanales(p, datos, favoritos);
  };

  function filtrarCanales(p, datos, favoritos) {
    const q = App.normalizar(p.q || '');
    const fav = new Set(favoritos);
    canalesFiltrados = datos.canales.filter(ch => {
      if (p.grupo === '__fav' && !fav.has(ch.id)) return false;
      if (p.grupo === '__deportes' && !/sport|deport/i.test(ch.grupo)) return false;
      if (!p.grupo.startsWith('__') && (ch.grupo || 'Sin grupo') !== p.grupo) return false;
      if (q && !App.normalizar(ch.nombre + ' ' + ch.grupo).includes(q)) return false;
      return true;
    });
    mostrados = 0;
    const caja = $('#lista-canales');
    if (!caja) return;
    if (!canalesFiltrados.length) {
      caja.innerHTML = vacio('Sin canales', p.grupo === '__fav' ? 'Marca canales con ★ para verlos aquí.' : 'No hay canales que coincidan.');
      return;
    }
    caja.innerHTML = '<p class="nota">' + canalesFiltrados.length + ' canales</p><div class="canales" id="rejilla-canales"></div><div class="mas" id="mas-canales"></div>';
    mostrarMasCanales();
  }

  function mostrarMasCanales() {
    const rejilla = $('#rejilla-canales');
    if (!rejilla) return;
    const fav = new Set(App.datos.leer('fav_canales', []));
    const lote = canalesFiltrados.slice(mostrados, mostrados + POR_PAGINA);
    rejilla.insertAdjacentHTML('beforeend', lote.map((ch, k) => {
      const i = mostrados + k;
      const logo = urlSegura(ch.logo) ? '<img loading="lazy" alt="" src="' + esc(ch.logo) + '" onerror="this.remove()">' : esc((ch.nombre || '?').slice(0, 2).toUpperCase());
      return '<div class="canal-caja"><button class="canal foco" data-accion="canal" data-i="' + i + '">' +
        '<span class="logo">' + logo + '</span><span class="nombre">' + esc(ch.nombre) + '<small>' + esc(ch.grupo || ch.lista) + '</small></span></button>' +
        '<button class="canal-fav foco' + (fav.has(ch.id) ? ' si' : '') + '" data-accion="fav-canal" data-i="' + i + '" aria-label="Favorito">' + (fav.has(ch.id) ? '★' : '☆') + '</button></div>';
    }).join(''));
    mostrados += lote.length;
    const mas = $('#mas-canales');
    mas.innerHTML = mostrados < canalesFiltrados.length
      ? '<button class="btn sec foco" data-accion="mas-canales">Mostrar más (' + (canalesFiltrados.length - mostrados) + ')</button>' : '';
  }

  // =================================================================
  // PANTALLA: AJUSTES (claves, listas, agendas, fuentes, respaldo)
  // =================================================================
  const COLECCIONES = {
    listasCanales: { titulo: 'Listas de canales (M3U)', ayuda: 'Listas en formato <code>.m3u</code> / <code>.m3u8</code>. Puedes poner un enlace o pegar el contenido de la lista.', pegar: 'Pegar contenido M3U (opcional)' },
    agendas: { titulo: 'Agendas deportivas (JSON)', ayuda: 'Tu propia agenda de eventos con enlaces. Se junta con la agenda de TheSportsDB en la pantalla Deportes. Formato en docs/LISTAS.md.', pegar: 'Pegar contenido JSON (opcional)' },
    fuentes: { titulo: 'Fuentes para películas, series y anime', ayuda: 'Plantillas de enlace. Comodines: <code>{tmdb}</code> <code>{imdb}</code> <code>{mal}</code> <code>{temporada}</code> <code>{episodio}</code> <code>{titulo}</code>. (<code>{mal}</code> es el ID de MyAnimeList, para anime.)' }
  };

  pantallas.ajustes = async function (p, t) {
    const c = App.config.obtener();
    const version = App.nativo && App.nativo.version ? App.nativo.version() : 'web';
    let html = '<div class="cabecera"><h1>Ajustes</h1></div>';

    html += '<section class="panel"><h2>Claves</h2><p>TMDB da el catálogo de películas y series (gratis en themoviedb.org → Ajustes → API). TheSportsDB da la agenda deportiva (la clave <code>123</code> es la gratuita).</p>' +
      '<form class="formulario" data-form="claves" style="border:0;margin:0;padding:0">' +
      '<label class="campo">Clave o token de TMDB' + (App.nativo && App.nativo.tmdbKey && App.nativo.tmdbKey() ? ' (ya hay una en .env; esta la reemplaza)' : '') +
      '<input name="tmdbKey" autocomplete="off" value="' + esc(c.tmdbKey) + '" placeholder="Pega tu clave aquí"></label>' +
      '<label class="campo">Clave de TheSportsDB<input name="sportsdbKey" autocomplete="off" value="' + esc(c.sportsdbKey) + '"></label>' +
      '<label class="campo">País para “Dónde verla” (código de 2 letras)<input name="region" maxlength="2" value="' + esc(c.region) + '"></label>' +
      '<div class="acciones"><button class="btn foco" type="submit">Guardar claves</button></div></form></section>';

    Object.keys(COLECCIONES).forEach(col => {
      const info = COLECCIONES[col];
      const items = c[col].map(x => {
        const detalle = col === 'fuentes'
          ? [x.pelicula ? 'Películas ✓' : '', x.serie ? 'Series ✓' : '', x.anime ? 'Anime ✓' : ''].filter(Boolean).join(' · ')
          : (x.url || (x.contenido ? 'Contenido pegado (' + Math.round(x.contenido.length / 1024) + ' KB)' : ''));
        return '<div class="item-lista' + (x.activa === false ? ' apagada' : '') + '"><div class="info"><b>' + esc(x.nombre) + '</b><small>' + esc(detalle) + '</small></div>' +
          (col !== 'fuentes' ? '<button class="btn sec peq foco" data-accion="probar" data-col="' + col + '" data-id="' + esc(x.id) + '">Probar</button>' : '') +
          '<button class="btn sec peq foco" data-accion="alternar" data-col="' + col + '" data-id="' + esc(x.id) + '">' + (x.activa === false ? 'Activar' : 'Apagar') + '</button>' +
          '<button class="btn peligro peq foco" data-accion="eliminar" data-col="' + col + '" data-id="' + esc(x.id) + '">✕</button></div>';
      }).join('') || '<p class="nota" style="padding:0">Todavía no hay ninguna.</p>';

      let form = '<form class="formulario" data-form="' + col + '"><h3>Agregar</h3>' +
        '<label class="campo">Nombre<input name="nombre" required placeholder="Ej.: Mi lista"></label>';
      if (col === 'fuentes') {
        form += '<label class="campo">Enlace para películas<input name="pelicula" inputmode="url" placeholder="https://…/{tmdb}"></label>' +
          '<label class="campo">Enlace para series<input name="serie" inputmode="url" placeholder="https://…/{tmdb}/{temporada}/{episodio}"></label>' +
          '<label class="campo">Enlace para anime<input name="anime" inputmode="url" placeholder="https://…/{mal}/{episodio}"></label>';
      } else {
        form += '<label class="campo">Enlace (URL)<input name="url" inputmode="url" placeholder="https://…"></label>' +
          '<label class="campo">' + info.pegar + '<textarea name="contenido" spellcheck="false"></textarea></label>';
      }
      form += '<div class="acciones"><button class="btn foco" type="submit">Agregar</button></div></form>';
      html += '<section class="panel"><h2>' + info.titulo + '</h2><p>' + info.ayuda + '</p>' + items + form + '</section>';
    });

    html += '<section class="panel"><h2>Respaldo</h2><p>Copia este texto para guardar tus listas y fuentes, o pega uno para restaurarlas.</p>' +
      '<label class="campo"><textarea id="respaldo" spellcheck="false">' + esc(JSON.stringify(c, null, 2)) + '</textarea></label>' +
      '<div class="acciones"><button class="btn sec foco" data-accion="importar">Importar lo pegado</button>' +
      '<button class="btn peligro foco" data-accion="restablecer">Restablecer todo</button></div></section>';

    html += '<section class="panel"><h2>Acerca de</h2><p>MyStream ' + esc(version) + '<br>' +
      'Este producto usa la API de TMDB pero no está avalado ni certificado por TMDB.<br>' +
      'Agenda deportiva: TheSportsDB. “Dónde verla”: JustWatch. Listas públicas: iptv-org.</p></section>';

    poner(t, html);
  };

  function guardarFormulario(form) {
    const tipo = form.dataset.form;
    const f = new FormData(form);
    const val = k => String(f.get(k) || '').trim();
    const c = App.config.obtener();
    if (tipo === 'claves') {
      c.tmdbKey = val('tmdbKey');
      c.sportsdbKey = val('sportsdbKey') || '123';
      c.region = (val('region') || 'HN').toUpperCase();
      App.config.guardar(c);
      aviso('Claves guardadas');
      return repintar();
    }
    const nuevo = { id: App.nuevoId(), nombre: val('nombre'), activa: true };
    if (!nuevo.nombre) return aviso('Ponle un nombre');
    if (tipo === 'fuentes') {
      nuevo.pelicula = val('pelicula');
      nuevo.serie = val('serie');
      nuevo.anime = val('anime');
      if (!urlSegura(nuevo.pelicula) && !urlSegura(nuevo.serie) && !urlSegura(nuevo.anime)) return aviso('Pon al menos un enlace que empiece con http');
    } else {
      nuevo.url = val('url');
      const contenido = val('contenido');
      if (contenido) {
        if (tipo === 'agendas') { try { JSON.parse(contenido); } catch (e) { return aviso('El JSON pegado no es válido'); } }
        nuevo.contenido = contenido;
        nuevo.url = '';
      } else if (!urlSegura(nuevo.url)) {
        return aviso('Pon un enlace que empiece con http o pega el contenido');
      }
    }
    c[tipo].push(nuevo);
    App.config.guardar(c);
    aviso('Agregado');
    repintar();
  }

  // ---------------------------------------------------------------
  // Acciones de todos los botones (un solo "escuchador")
  // ---------------------------------------------------------------
  document.addEventListener('submit', e => {
    const form = e.target.closest('form[data-form]');
    if (!form) return;
    e.preventDefault();
    guardarFormulario(form);
  });

  document.addEventListener('click', async e => {
    const pest = e.target.closest('[data-pestana]');
    if (pest) { App.ir(pest.dataset.pestana); return; }
    const b = e.target.closest('[data-accion]');
    if (!b) return;
    const d = b.dataset;
    const p = cima() ? cima().params : {};
    const c = App.config.obtener();

    switch (d.accion) {
      case 'ir':
        cerrarModal();
        App.ir(d.a, d.grupo ? { grupo: d.grupo } : {});
        break;
      case 'atras': App.atras(); break;
      case 'recargar': pintar(false); break;
      case 'cerrar-modal': cerrarModal(); break;
      case 'detalle': App.ir('detalle', { tipo: d.tipo, id: d.id }); break;

      // Buscar
      case 'buscar-tipo': p.tipo = d.v; p.genero = ''; pintar(false); break;
      case 'buscar-genero': p.genero = String(p.genero) === d.v ? '' : d.v; p.q = ''; pintar(false); break;

      // Detalle
      case 'temporada':
        p.temporada = Number(d.v);
        document.querySelectorAll('[data-accion="temporada"]').forEach(x => x.classList.toggle('activa', x === b));
        cargarEpisodios(p);
        break;
      case 'ver':
        if (!estadoDetalle) break;
        ver(Object.assign({}, estadoDetalle, { temporada: Number(d.t || 1), episodio: Number(d.e || 1) }));
        break;
      case 'anime-detalle': App.ir('animeDetalle', { id: d.id }); break;
      case 'ver-anime':
        if (!estadoDetalle) break;
        ver(Object.assign({}, estadoDetalle, { episodio: Number(d.e || 1) }));
        break;
      case 'usar-fuente':
        if (opcionesVer) { cerrarModal(); opcionesVer.abrir(opcionesVer.fuentes[Number(d.i)]); }
        break;
      case 'trailer':
        if (estadoDetalle && estadoDetalle.trailer) abrirExterno('https://www.youtube.com/watch?v=' + encodeURIComponent(estadoDetalle.trailer.key));
        break;
      case 'milista': {
        if (!estadoDetalle) break;
        const ahora = alternarMiLista(estadoDetalle.item);
        b.textContent = ahora ? '✓ En Mi lista' : '＋ Mi lista';
        break;
      }

      // Deportes
      case 'dia': p.dia = Number(d.v); pintar(false); break;
      case 'deporte': p.deporte = d.v; pintar(false); break;
      case 'evento': abrirEvento(Number(d.i)); break;
      case 'enlace': {
        const ev = eventosActuales[Number(d.ev)];
        const x = ev && ev.enlaces[Number(d.i)];
        if (x) { cerrarModal(); App.reproducir(x.url, ev.titulo, { tipo: 'enlace', ua: x.ua, referer: x.referer }); }
        break;
      }
      case 'buscar-canal': cerrarModal(); App.ir('tv', { grupo: '__deportes' }); break;

      // TV
      case 'grupo': p.grupo = d.v; pintar(false); break;
      case 'actualizar-canales': p.forzar = true; pintar(false); break;
      case 'mas-canales': mostrarMasCanales(); break;
      case 'canal': {
        const ch = canalesFiltrados[Number(d.i)];
        if (ch) App.reproducir(ch.url, ch.nombre, { tipo: 'canal', ua: ch.ua, referer: ch.referer });
        break;
      }
      case 'fav-canal': {
        const ch = canalesFiltrados[Number(d.i)];
        if (!ch) break;
        let fav = App.datos.leer('fav_canales', []);
        const si = !fav.includes(ch.id);
        fav = si ? [ch.id].concat(fav) : fav.filter(x => x !== ch.id);
        App.datos.guardar('fav_canales', fav);
        b.classList.toggle('si', si);
        b.textContent = si ? '★' : '☆';
        break;
      }

      // Ajustes
      case 'alternar': {
        const x = c[d.col].find(i => i.id === d.id);
        if (x) { x.activa = x.activa === false; App.config.guardar(c); repintar(); }
        break;
      }
      case 'eliminar': {
        const x = c[d.col].find(i => i.id === d.id);
        if (!x) break;
        abrirModal('<h3>¿Eliminar “' + esc(x.nombre) + '”?</h3><p>Se quitará de la app. Puedes volver a agregarla cuando quieras.</p>' +
          '<div class="acciones"><button class="btn peligro foco" data-accion="eliminar-si" data-col="' + d.col + '" data-id="' + esc(d.id) + '">Eliminar</button>' +
          '<button class="btn sec foco" data-accion="cerrar-modal">Cancelar</button></div>');
        break;
      }
      case 'eliminar-si':
        c[d.col] = c[d.col].filter(i => i.id !== d.id);
        App.datos.borrar('cache_lista_' + d.id);
        App.config.guardar(c);
        cerrarModal();
        repintar();
        break;
      case 'probar': {
        const x = c[d.col].find(i => i.id === d.id);
        if (!x) break;
        aviso('Probando…');
        try {
          if (d.col === 'listasCanales') aviso('Funciona: ' + (await App.canales.probar(x)) + ' canales');
          else {
            const datos = x.contenido ? JSON.parse(x.contenido) : await App.red.json(x.url);
            const n = (Array.isArray(datos) ? datos : (datos.eventos || datos.events || [])).length;
            aviso('Funciona: ' + n + ' eventos');
          }
        } catch (err) { aviso('No funcionó: ' + err.message); }
        break;
      }
      case 'importar': {
        try {
          const nuevo = JSON.parse($('#respaldo').value);
          if (!nuevo || typeof nuevo !== 'object' || Array.isArray(nuevo)) throw new Error('formato');
          App.config.guardar(Object.assign(JSON.parse(JSON.stringify(App.PREDETERMINADO)), nuevo));
          aviso('Configuración importada');
          pintar(false);
        } catch (err) { aviso('El texto pegado no es una configuración válida'); }
        break;
      }
      case 'restablecer':
        abrirModal('<h3>¿Restablecer todo?</h3><p>Se borran tus listas, agendas, fuentes y claves guardadas en la app, y se vuelve a la configuración inicial. Mi lista y favoritos se conservan.</p>' +
          '<div class="acciones"><button class="btn peligro foco" data-accion="restablecer-si">Restablecer</button>' +
          '<button class="btn sec foco" data-accion="cerrar-modal">Cancelar</button></div>');
        break;
      case 'restablecer-si':
        App.config.restablecer();
        cerrarModal();
        aviso('Listo');
        pintar(false);
        break;
    }
  });

  // ---------------------------------------------------------------
  // Arranque
  // ---------------------------------------------------------------
  App.ir('inicio');
})(window.App);
