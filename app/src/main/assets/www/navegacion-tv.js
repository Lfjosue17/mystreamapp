// =====================================================================
// NAVEGACIÓN CON CONTROL REMOTO (flechas del control de Android TV)
// Mueve el foco al elemento más cercano en la dirección presionada.
// =====================================================================
'use strict';
(function () {
  const SELECTOR = 'button:not([disabled]), a[href], input, select, textarea, [tabindex="0"]';

  function enfocables(ambito) {
    return Array.from(ambito.querySelectorAll(SELECTOR)).filter(el => {
      if (el.closest('.oculto')) return false;
      const r = el.getBoundingClientRect();
      return r.width > 0 && r.height > 0;
    });
  }

  function ambitoActual() {
    const modal = document.querySelector('#modal:not(.oculto)');
    if (modal) return modal;
    const marco = document.querySelector('#marco:not(.oculto)');
    if (marco) return marco;
    return document.body;
  }

  function mover(dir) {
    const ambito = ambitoActual();
    const lista = enfocables(ambito);
    if (!lista.length) return false;
    const act = document.activeElement;
    if (!act || act === document.body || !ambito.contains(act)) {
      lista[0].focus();
      return true;
    }
    const a = act.getBoundingClientRect();
    const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
    let mejor = null, puntaje = Infinity;
    for (const el of lista) {
      if (el === act) continue;
      const r = el.getBoundingClientRect();
      const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      let principal, lateral;
      if (dir === 'der' || dir === 'izq') {
        principal = dir === 'der' ? r.left - a.right : a.left - r.right;
        if (principal < -a.width / 2) continue;
        if (dir === 'der' ? cx <= ax : cx >= ax) continue;
        // debe estar en la misma "fila"
        if (r.bottom <= a.top + 2 || r.top >= a.bottom - 2) continue;
        lateral = Math.abs(cy - ay);
        principal = Math.max(principal, 0);
      } else {
        principal = dir === 'abajo' ? r.top - a.bottom : a.top - r.bottom;
        if (principal < -a.height / 2) continue;
        if (dir === 'abajo' ? cy <= ay : cy >= ay) continue;
        lateral = Math.abs(cx - ax);
        principal = Math.max(principal, 0);
      }
      const p = principal + lateral * (dir === 'der' || dir === 'izq' ? 2 : 0.6);
      if (p < puntaje) { puntaje = p; mejor = el; }
    }
    if (!mejor) return false;
    mejor.focus({ preventScroll: true });
    mejor.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
    return true;
  }

  const TECLAS = { ArrowUp: 'arriba', ArrowDown: 'abajo', ArrowLeft: 'izq', ArrowRight: 'der' };

  document.addEventListener('keydown', e => {
    const dir = TECLAS[e.key];
    const t = document.activeElement;
    const escribiendo = t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT');
    if (dir) {
      if (escribiendo && (dir === 'izq' || dir === 'der')) return;   // mover el cursor del texto
      if (t && t.tagName === 'TEXTAREA') return;
      if (mover(dir)) e.preventDefault();
      return;
    }
    if ((e.key === 'Enter' || e.key === ' ') && t && t.getAttribute('tabindex') === '0' && t.tagName !== 'BUTTON') {
      e.preventDefault();
      t.click();
    }
  });
})();
