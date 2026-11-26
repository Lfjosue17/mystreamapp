// =====================================================================
// NÚCLEO: guardado, red, configuración, TMDB, listas M3U y deportes.
// No dibuja nada en pantalla (eso está en app.js).
// =====================================================================
'use strict';
window.App = window.App || {};

(function (App) {
  const N = window.Nativo || null;   // puente con Android (no existe en el navegador de la PC)
  App.nativo = N;

  const copia = o => JSON.parse(JSON.stringify(o));
  App.nuevoId = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6);

  /** Quita acentos y símbolos para comparar textos. */
  App.normalizar = t => String(t || '').toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, ' ').trim();

  /** Fecha local en formato AAAA-MM-DD. */
  App.fechaLocal = d => {
    const z = n => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + z(d.getMonth() + 1) + '-' + z(d.getDate());
  };

  // -------------------------------------------------------------------
  // Guardado local (archivos internos en Android, localStorage en la PC)
  // -------------------------------------------------------------------
  App.datos = {
    leer(clave, porDefecto) {
      try {
        const t = N ? N.leer(clave) : localStorage.getItem('ms_' + clave);
        return (t === null || t === undefined || t === '') ? porDefecto : JSON.parse(t);
      } catch (e) { return porDefecto; }
    },
    guardar(clave, valor) {
      const t = JSON.stringify(valor);
      try { if (N) N.guardar(clave, t); else localStorage.setItem('ms_' + clave, t); }
      catch (e) { console.warn('No se pudo guardar', clave, e); }
    },
    borrar(clave) {
      try { if (N) N.borrar(clave); else localStorage.removeItem('ms_' + clave); } catch (e) { /* nada */ }
    }
  };

  // -------------------------------------------------------------------
  // Red: en Android descarga por el lado nativo (sin problemas de CORS)
  // -------------------------------------------------------------------
  const pendientes = {};
  let contador = 0;
  App.red = {
    texto(url, cabeceras) {
      if (N && N.descargar) {
        return new Promise((ok, mal) => {
          const id = 'r' + (++contador);
          pendientes[id] = { ok, mal };
          N.descargar(id, url, JSON.stringify(cabeceras || {}));
        });
      }
      return fetch(url, { headers: cabeceras || {} }).then(r => {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.text();
      });
    },
    async json(url, cabeceras) {
      const t = await App.red.texto(url, cabeceras);
      try { return JSON.parse(t); } catch (e) { throw new Error('La respuesta no es JSON válido'); }
    },
    _fin(id, exito, contenido) {
      const p = pendientes[id];
      if (!p) return;
      delete pendientes[id];
      if (exito) p.ok(contenido); else p.mal(new Error(contenido || 'Error de red'));
    }
  };

  // -------------------------------------------------------------------
  // Proveedores HTML configurables (Kotlin/Jsoup en Android)
  // -------------------------------------------------------------------
  const pendientesProveedor = {};
  let contadorProveedor = 0;
  App.proveedores = {
    // Id reservado: cuando está presente, Kotlin consulta tu servidor
    // remoto (TMDB-Embed-API) en vez de una regla JSON local.
    ID_SERVIDOR_REMOTO: 'servidor-remoto',
    async buscar(id, parametros) {
      if (!N || !N.consultarProveedor) throw new Error('El motor local de proveedores solo está disponible en Android');
      return new Promise((ok, mal) => {
        const callbackId = 'p' + (++contadorProveedor);
        pendientesProveedor[callbackId] = { ok, mal };
        N.consultarProveedor(id, JSON.stringify(parametros || {}), callbackId);
      });
    },
    async buscarCategoria(categoria, parametros) {
      const c = App.config.obtener();
      const config = c.proveedores || { providers: [] };
      const ids = (config.providers || [])
        .filter(x => x && x.enabled !== false && x.category === categoria)
        .map(x => x.id);
      if (c.servidorRemoto && c.servidorRemoto.activo && (c.servidorRemoto.url || '').trim()) {
        ids.push(App.proveedores.ID_SERVIDOR_REMOTO);
      }
      const resultados = await Promise.all(ids.map(async id => {
        try { return await App.proveedores.buscar(id, parametros); }
        catch (e) { console.warn('Proveedor ' + id + ': ' + e.message); return []; }
      }));
      return resultados.flat().sort((a, b) => Number(b.score || 0) - Number(a.score || 0));
    },
    _fin(id, exito, contenido) {
      const p = pendientesProveedor[id];
      if (!p) return;
      delete pendientesProveedor[id];
      if (!exito) { p.mal(new Error(contenido || 'Error del proveedor')); return; }
      try { p.ok(JSON.parse(contenido)); } catch (e) { p.mal(new Error('Respuesta del proveedor no válida')); }
    }
  };

  // -------------------------------------------------------------------
  // Configuración (listas, fuentes, claves)
  // -------------------------------------------------------------------
  let configMem = null;
  App.config = {
    obtener() {
      if (configMem) return configMem;
      const guardada = App.datos.leer('config', null);
      configMem = Object.assign(copia(App.PREDETERMINADO), guardada || {});
      ['listasCanales', 'agendas', 'fuentes'].forEach(k => { if (!Array.isArray(configMem[k])) configMem[k] = []; });
      if (!configMem.proveedores || typeof configMem.proveedores !== 'object') configMem.proveedores = { version: 1, providers: [] };
      if (!configMem.servidorRemoto || typeof configMem.servidorRemoto !== 'object') configMem.servidorRemoto = { activo: false, url: '', apiKey: '' };
      if (!guardada) App.datos.guardar('config', configMem);
      return configMem;
    },
    guardar(c) {
      configMem = c;
      App.datos.guardar('config', c);
      App.canales.memoria = null;
      App.deportes.limpiarCache();
    },
    restablecer() {
      App.config.guardar(copia(App.PREDETERMINADO));
    }
  };

  // -------------------------------------------------------------------
  // TMDB (catálogo de películas y series)
  // -------------------------------------------------------------------
  const cacheTmdb = new Map();
  App.tmdb = {
    clave() {
      const c = App.config.obtener();
      let k = (c.tmdbKey || '').trim();
      if (!k && N && N.tmdbKey) { try { k = (N.tmdbKey() || '').trim(); } catch (e) { /* nada */ } }
      return k;
    },
    hayClave() { return !!App.tmdb.clave(); },
    get(ruta, params) {
      const c = App.config.obtener();
      const k = App.tmdb.clave();
      if (!k) return Promise.reject(new Error('SIN_CLAVE'));
      const q = new URLSearchParams(Object.assign({ language: c.idioma || 'es-MX' }, params || {}));
      const cab = {};
      // Acepta la "API Key" (v3) o el "Token de lectura" (v4, empieza con eyJ)
      if (k.startsWith('eyJ')) cab.Authorization = 'Bearer ' + k; else q.set('api_key', k);
      const url = 'https://api.themoviedb.org/3' + ruta + '?' + q.toString();
      if (cacheTmdb.has(url)) return cacheTmdb.get(url);
      const p = App.red.json(url, cab).then(r => {
        if (r && r.success === false) throw new Error(r.status_message || 'TMDB rechazó la petición');
        return r;
      });
      cacheTmdb.set(url, p);
      p.catch(() => cacheTmdb.delete(url));
      return p;
    },
    img(ruta, tam) { return ruta ? 'https://image.tmdb.org/t/p/' + (tam || 'w342') + ruta : ''; }
  };

  // -------------------------------------------------------------------
  // Jikan (catálogo de anime, de MyAnimeList). No necesita clave.
  // Tiene límite de peticiones, así que se hacen de una en una.
  // -------------------------------------------------------------------
  const cacheJikan = new Map();
  let colaJikan = Promise.resolve();
  App.jikan = {
    get(ruta, params) {
      const q = params ? '?' + new URLSearchParams(params).toString() : '';
      // Si hay servidor remoto activo, pasar por su proxy a Jikan en vez de
      // pedirlo directo desde el teléfono: ese pedido directo fallaba seguido
      // (Jikan es lento/limita peticiones y hay redes con mala ruta hacia esa
      // API), mientras que el servidor sí le habla bien. Sin servidor remoto
      // configurado, sigue funcionando igual que antes (pedido directo).
      const c = App.config.obtener();
      const sr = c.servidorRemoto;
      const usarServidor = !!(sr && sr.activo && (sr.url || '').trim());
      const url = usarServidor
        ? sr.url.trim().replace(/\/+$/, '') + '/api/jikan' + ruta + q
        : 'https://api.jikan.moe/v4' + ruta + q;
      const cabeceras = usarServidor ? { 'X-API-Key': sr.apiKey || '' } : undefined;
      if (cacheJikan.has(url)) return cacheJikan.get(url);
      // Encadena las peticiones con una pequeña pausa para no pasar el límite.
      const p = colaJikan.then(() => App.red.json(url, cabeceras)).then(r => {
        if (r && r.status && r.status >= 400) throw new Error(r.message || 'Jikan rechazó la petición');
        return r;
      });
      colaJikan = p.then(() => new Promise(r => setTimeout(r, 400)), () => new Promise(r => setTimeout(r, 400)));
      cacheJikan.set(url, p);
      p.catch(() => cacheJikan.delete(url));
      return p;
    }
  };

  // -------------------------------------------------------------------
  // Listas M3U
  // -------------------------------------------------------------------
  function hash(t) {
    let h = 5381;
    for (let i = 0; i < t.length; i++) h = ((h << 5) + h + t.charCodeAt(i)) | 0;
    return (h >>> 0).toString(36);
  }

  App.m3u = {
    /** Convierte el texto de una lista M3U en una lista de canales. */
    parsear(texto) {
      const lineas = String(texto || '').replace(/^﻿/, '').replace(/\r/g, '').split('\n');
      const res = [];
      let act = null;
      for (let l of lineas) {
        l = l.trim();
        if (!l) continue;
        if (l.startsWith('#EXTINF')) {
          act = { nombre: '', logo: '', grupo: '', ua: '', referer: '' };
          // separa atributos y nombre: el nombre va después de la primera coma fuera de comillas
          let comillas = false, corte = -1;
          for (let i = 0; i < l.length; i++) {
            if (l[i] === '"') comillas = !comillas;
            else if (l[i] === ',' && !comillas) { corte = i; break; }
          }
          const atributos = corte >= 0 ? l.slice(0, corte) : l;
          act.nombre = corte >= 0 ? l.slice(corte + 1).trim() : '';
          atributos.replace(/([\w-]+)="([^"]*)"/g, (_, k, v) => {
            k = k.toLowerCase();
            if (k === 'tvg-logo') act.logo = v;
            else if (k === 'group-title') act.grupo = v.split(';')[0].trim();
            else if (k === 'tvg-name' && !act.nombre) act.nombre = v;
            else if (k === 'http-user-agent' || k === 'user-agent') act.ua = v;
            else if (k === 'http-referrer' || k === 'referrer') act.referer = v;
            return '';
          });
        } else if (l.startsWith('#EXTGRP:')) {
          if (act && !act.grupo) act.grupo = l.slice(8).trim();
        } else if (l.startsWith('#EXTVLCOPT:')) {
          if (!act) continue;
          const op = l.slice(11);
          const i = op.indexOf('=');
          if (i < 0) continue;
          const k = op.slice(0, i).toLowerCase(), v = op.slice(i + 1).trim();
          if (k === 'http-user-agent') act.ua = v;
          else if (k === 'http-referrer' || k === 'http-referer') act.referer = v;
        } else if (!l.startsWith('#')) {
          const c = act || { nombre: '', logo: '', grupo: '', ua: '', referer: '' };
          let url = l;
          // Formato estilo Kodi: url|User-Agent=...&Referer=...
          const tubo = url.indexOf('|');
          if (tubo > 0) {
            url.slice(tubo + 1).split('&').forEach(par => {
              const j = par.indexOf('=');
              if (j < 0) return;
              const k = par.slice(0, j).toLowerCase();
              let v = par.slice(j + 1);
              try { v = decodeURIComponent(v); } catch (e) { /* nada */ }
              if (k === 'user-agent') c.ua = v;
              else if (k === 'referer' || k === 'referrer') c.referer = v;
            });
            url = url.slice(0, tubo);
          }
          c.url = url;
          if (!c.nombre) c.nombre = url;
          c.id = hash(url);
          res.push(c);
          act = null;
        }
      }
      return res;
    }
  };

  // -------------------------------------------------------------------
  // Canales: junta todas las listas activas
  // -------------------------------------------------------------------
  const HORAS_CACHE_LISTA = 6;

  async function textoDeLista(lista, forzar) {
    if (lista.contenido) return lista.contenido;
    const clave = 'cache_lista_' + lista.id;
    const cache = App.datos.leer(clave, null);
    if (!forzar && cache && Date.now() - cache.t < HORAS_CACHE_LISTA * 3600e3) return cache.texto;
    try {
      const t = await App.red.texto(lista.url);
      App.datos.guardar(clave, { t: Date.now(), texto: t });
      return t;
    } catch (e) {
      if (cache) return cache.texto;   // sin internet: usa la última copia
      throw e;
    }
  }

  App.canales = {
    memoria: null,
    async cargar(forzar) {
      if (this.memoria && !forzar) return this.memoria;
      const c = App.config.obtener();
      const activas = c.listasCanales.filter(l => l.activa !== false);
      const todos = [], errores = [], vistos = new Set();
      const resultados = await Promise.all(activas.map(async l => {
        try { return { l, canales: App.m3u.parsear(await textoDeLista(l, forzar)) }; }
        catch (e) { errores.push(l.nombre + ': ' + e.message); return { l, canales: [] }; }
      }));
      resultados.forEach(({ l, canales }) => canales.forEach(ch => {
        if (vistos.has(ch.url)) return;
        vistos.add(ch.url);
        ch.lista = l.nombre;
        todos.push(ch);
      }));
      this.memoria = { canales: todos, errores, listas: activas.length };
      return this.memoria;
    },
    /** Descarga una lista y devuelve cuántos canales tiene (para el botón "Probar"). */
    async probar(lista) {
      return App.m3u.parsear(await textoDeLista(lista, true)).length;
    }
  };

  // -------------------------------------------------------------------
  // Deportes: agenda de TheSportsDB + agendas propias (JSON)
  // -------------------------------------------------------------------
  const DEPORTES = {
    futbol:     { api: 'Soccer',            nombres: ['futbol', 'soccer', 'football'] },
    baloncesto: { api: 'Basketball',        nombres: ['baloncesto', 'basketball', 'basquet', 'nba'] },
    beisbol:    { api: 'Baseball',          nombres: ['beisbol', 'baseball', 'mlb'] },
    americano:  { api: 'American Football', nombres: ['americano', 'american football', 'nfl'] },
    motor:      { api: 'Motorsport',        nombres: ['motor', 'motorsport', 'f1', 'formula 1'] },
    combate:    { api: 'Fighting',          nombres: ['combate', 'fighting', 'boxeo', 'ufc', 'mma'] },
    tenis:      { api: 'Tennis',            nombres: ['tenis', 'tennis'] }
  };
  const cacheAgendas = new Map();

  function fechaDe(ev) {
    // TheSportsDB guarda la hora en UTC
    let t = ev.strTimestamp || (ev.dateEvent && ev.strTime ? ev.dateEvent + 'T' + ev.strTime : ev.dateEvent);
    if (!t) return null;
    if (!/[zZ]|[+-]\d\d:?\d\d$/.test(t)) t += 'Z';
    const d = new Date(t);
    return isNaN(d) ? null : d;
  }

  function sumarDias(fechaTexto, n) {
    const [a, m, d] = fechaTexto.split('-').map(Number);
    return App.fechaLocal(new Date(a, m - 1, d + n));
  }

  async function eventosSportsDb(fecha, deporte) {
    const c = App.config.obtener();
    const clave = encodeURIComponent((c.sportsdbKey || '123').trim());
    const s = deporte && DEPORTES[deporte] ? '&s=' + encodeURIComponent(DEPORTES[deporte].api) : '';
    // La agenda está en UTC: se piden el día y el siguiente para cubrir la noche local.
    const dias = [fecha, sumarDias(fecha, 1)];
    const respuestas = await Promise.all(dias.map(d =>
      App.red.json('https://www.thesportsdb.com/api/v1/json/' + clave + '/eventsday.php?d=' + d + s)
        .catch(() => ({ events: null }))));
    const lista = [];
    const vistos = new Set();
    respuestas.forEach(r => (r.events || []).forEach(ev => {
      if (vistos.has(ev.idEvent)) return;
      vistos.add(ev.idEvent);
      const inicio = fechaDe(ev);
      if (!inicio || App.fechaLocal(inicio) !== fecha) return;
      lista.push({
        id: 'tsdb-' + ev.idEvent,
        titulo: ev.strEvent || ((ev.strHomeTeam || '') + ' vs ' + (ev.strAwayTeam || '')),
        local: ev.strHomeTeam || '', visita: ev.strAwayTeam || '',
        escudoLocal: ev.strHomeTeamBadge || '', escudoVisita: ev.strAwayTeamBadge || '',
        liga: ev.strLeague || '', deporte: ev.strSport || '',
        imagen: ev.strThumb || '',
        inicio: inicio.getTime(),
        estado: ev.strStatus || '',
        marcador: (ev.intHomeScore !== null && ev.intHomeScore !== undefined && ev.intHomeScore !== '')
          ? ev.intHomeScore + ' - ' + ev.intAwayScore : '',
        enlaces: [], origen: 'TheSportsDB'
      });
    }));
    return lista;
  }

  async function leerAgenda(ag) {
    if (ag.contenido) return JSON.parse(ag.contenido);
    const g = cacheAgendas.get(ag.id);
    if (g && Date.now() - g.t < 10 * 60e3) return g.datos;
    const datos = await App.red.json(ag.url);
    cacheAgendas.set(ag.id, { t: Date.now(), datos });
    return datos;
  }

  function coincideDeporte(texto, deporte) {
    if (!deporte || !DEPORTES[deporte] || !texto) return true;
    const n = App.normalizar(texto);
    return DEPORTES[deporte].nombres.some(x => n.includes(x));
  }

  async function eventosDeAgendas(fecha, deporte, errores) {
    const c = App.config.obtener();
    const lista = [];
    await Promise.all(c.agendas.filter(a => a.activa !== false).map(async ag => {
      try {
        const datos = await leerAgenda(ag);
        const eventos = Array.isArray(datos) ? datos : (datos.eventos || datos.events || []);
        eventos.forEach((e, i) => {
          const d = new Date(e.inicio || e.fecha || e.start);
          if (isNaN(d) || App.fechaLocal(d) !== fecha) return;
          if (!coincideDeporte(e.deporte, deporte)) return;
          lista.push({
            id: 'ag-' + ag.id + '-' + i,
            titulo: e.titulo || e.title || ((e.local || '') + ' vs ' + (e.visita || '')),
            local: e.local || '', visita: e.visita || '',
            escudoLocal: e.escudoLocal || '', escudoVisita: e.escudoVisita || '',
            liga: e.liga || e.league || '', deporte: e.deporte || '',
            imagen: e.imagen || e.logo || '',
            inicio: d.getTime(), estado: e.estado || '', marcador: e.marcador || '',
            enlaces: (e.enlaces || e.links || []).map((x, j) => typeof x === 'string'
              ? { nombre: 'Opción ' + (j + 1), url: x }
              : { nombre: x.nombre || x.name || ('Opción ' + (j + 1)), url: x.url, ua: x.ua || '', referer: x.referer || '' })
              .filter(x => x.url)
              .map(x => Object.assign(x, { origen: ag.nombre })),
            origen: ag.nombre
          });
        });
      } catch (e) { errores.push(ag.nombre + ': ' + e.message); }
    }));
    return lista;
  }

  /** ¿Es el mismo partido? Compara nombres de equipos sin acentos. */
  function mismoEvento(a, b) {
    if (Math.abs(a.inicio - b.inicio) > 3 * 3600e3) return false;
    const ta = App.normalizar(a.titulo + ' ' + a.local + ' ' + a.visita);
    const equiposB = [b.local, b.visita].filter(Boolean).map(App.normalizar);
    if (equiposB.length === 2) return equiposB.every(e => ta.includes(e));
    return App.normalizar(b.titulo) === App.normalizar(a.titulo);
  }

  /** Normaliza una entrada de lookuptimeline.php (los nombres de campo varían según el tipo de evento). */
  function lineaTiempo(x) {
    const minuto = x.intTime || x.strTime || x.intMinute || '';
    const tipo = x.strTimeline || x.strEvent || x.strTimelineDetail || 'Evento';
    return {
      minuto: minuto ? (String(minuto).replace(/\D+$/, '') + "'") : '',
      min: parseInt(minuto, 10) || 0,
      equipo: x.strTeam || x.strHomeAway || '',
      jugador: x.strPlayer || '',
      tipo,
      asistencia: x.strAssist || x.strAssistPlayer || '',
      esGol: /gol|goal/i.test(tipo),
      esTarjeta: /tarjeta|card/i.test(tipo),
      esRoja: /roja|red/i.test(tipo),
      esCambio: /cambio|sub/i.test(tipo)
    };
  }

  App.deportes = {
    LISTA: Object.keys(DEPORTES),
    limpiarCache() { cacheAgendas.clear(); },
    /** Detalle de un evento de TheSportsDB: estado/marcador/sede + línea de tiempo (goles/tarjetas), si existe. */
    async detalleEvento(idEvent) {
      const c = App.config.obtener();
      const clave = encodeURIComponent((c.sportsdbKey || '123').trim());
      const base = 'https://www.thesportsdb.com/api/v1/json/' + clave + '/';
      const [evRes, tlRes] = await Promise.all([
        App.red.json(base + 'lookupevent.php?id=' + encodeURIComponent(idEvent)).catch(() => ({ events: null })),
        App.red.json(base + 'lookuptimeline.php?id=' + encodeURIComponent(idEvent)).catch(() => ({ timeline: null }))
      ]);
      const ev = (evRes && evRes.events && evRes.events[0]) || null;
      const linea = ((tlRes && tlRes.timeline) || [])
        .map(lineaTiempo)
        .sort((a, b) => a.min - b.min);
      return {
        estado: ev ? (ev.strStatus || '') : '',
        sede: ev ? (ev.strVenue || '') : '',
        ciudad: ev ? (ev.strCity || '') : '',
        marcador: ev && ev.intHomeScore !== null && ev.intHomeScore !== undefined && ev.intHomeScore !== ''
          ? ev.intHomeScore + ' - ' + ev.intAwayScore : '',
        local: ev ? (ev.strHomeTeam || '') : '',
        visita: ev ? (ev.strAwayTeam || '') : '',
        linea
      };
    },
    async eventosDelDia(fecha, deporte) {
      const errores = [];
      const [api, propios] = await Promise.all([
        eventosSportsDb(fecha, deporte).catch(e => { errores.push('TheSportsDB: ' + e.message); return []; }),
        eventosDeAgendas(fecha, deporte, errores)
      ]);
      // Si un evento de tu agenda coincide con uno de TheSportsDB, se le pegan los enlaces.
      const resultado = api.slice();
      propios.forEach(p => {
        const igual = resultado.find(a => mismoEvento(a, p) || mismoEvento(p, a));
        if (igual) {
          igual.enlaces = igual.enlaces.concat(p.enlaces);
          if (!igual.escudoLocal) igual.escudoLocal = p.escudoLocal;
          if (!igual.escudoVisita) igual.escudoVisita = p.escudoVisita;
        } else {
          resultado.push(p);
        }
      });
      resultado.sort((a, b) => a.inicio - b.inicio);
      return { eventos: resultado, errores };
    }
  };
})(window.App);
