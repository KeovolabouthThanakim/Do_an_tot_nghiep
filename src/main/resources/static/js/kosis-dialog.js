/* ==========================================================================
   KOSIS dialog — replaces alert()/confirm().
   alert()  = a toast notification: it pops in, stays 2 seconds and disappears
              by itself (no OK button, nothing to click).
   confirm()= a centred dialog with buttons (a decision is needed, so it stays).

   API
     KosisDialog.alert(message, { type, title, linkHref, duration }) -> Promise<void>  (resolves when hidden)
     KosisDialog.confirm(message, { title, okText, cancelText, danger })      -> Promise<boolean>
   type: 'success' | 'error' | 'warn' | 'info'

   Markup helpers
     <div hidden data-flash="success|error|warn|info">message</div>   shown on page load
        optional: data-flash-title, data-flash-once="key" (once per browser session),
                  data-flash-link="/url" data-flash-link-text="Label"
     <form data-confirm="Question?" data-confirm-ok="OK" data-confirm-danger>   asks before submit
   ========================================================================== */
(function () {
  'use strict';
  if (window.KosisDialog) return;

    var ICONS = {
    success: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>',
    error: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>',
    warn: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 8v5M12 17h.01"/><path d="M10.3 3.9 2.4 17.5A2 2 0 0 0 4.1 20.5h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/></svg>',
    info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 11v6M12 7h.01"/><circle cx="12" cy="12" r="9"/></svg>',
    confirm: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2.5-3 4.5M12 18h.01"/><circle cx="12" cy="12" r="9"/></svg>'
  };

  var root, box, iconEl, titleEl, msgEl, actionsEl, current = null, queue = [], lastFocus = null;

  function build() {
    if (root) return;
    root = document.createElement('div');
    root.className = 'kd';
    root.hidden = true;
    root.innerHTML =
      '<div class="kd__box" role="alertdialog" aria-modal="true" aria-labelledby="kd-title" aria-describedby="kd-msg">' +
      '<div class="kd__icon"></div><h2 class="kd__title" id="kd-title"></h2>' +
      '<p class="kd__msg" id="kd-msg"></p><div class="kd__actions"></div></div>';
    document.body.appendChild(root);
    box = root.firstChild;
    iconEl = box.querySelector('.kd__icon');
    titleEl = box.querySelector('.kd__title');
    msgEl = box.querySelector('.kd__msg');
    actionsEl = box.querySelector('.kd__actions');
    root.addEventListener('mousedown', function (e) { if (e.target === root && current) finish(false); });
    document.addEventListener('keydown', onKey, true);
  }

  function focusables() { return Array.prototype.slice.call(actionsEl.querySelectorAll('a,button')); }

  function onKey(e) {
    if (!current) return;
    if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); finish(false); }
    else if (e.key === 'Tab') {
      var f = focusables(); if (!f.length) return;
      var i = f.indexOf(document.activeElement);
      e.preventDefault();
      f[(i + (e.shiftKey ? -1 : 1) + f.length) % f.length].focus();
    }
  }

  function button(label, cls, onClick, href) {
    var b = document.createElement(href ? 'a' : 'button');
    b.className = 'kd__btn' + (cls ? ' ' + cls : '');
    b.textContent = label;
    if (href) b.href = href; else b.type = 'button';
    if (onClick) b.addEventListener('click', onClick);
    return b;
  }

  function finish(value) {
    if (!current) return;
    var job = current; current = null;
    root.classList.remove('is-open');
    setTimeout(function () {
      if (current) return;                       // next dialog already opened
      root.hidden = true; document.body.classList.remove('kd-lock');
      if (lastFocus && lastFocus.focus) { try { lastFocus.focus(); } catch (e) {} }
    }, 180);
    job.resolve(value);
    setTimeout(next, 200);
  }

  function kt(key, fb) { var k = window.KOSIS_I18N || {}; return k[key] != null ? k[key] : fb; }

  function next() {
    if (current || !queue.length) return;
    build();
    var job = queue.shift(); current = job;
    var o = job.opts;
    root.className = 'kd kd--info';
    iconEl.innerHTML = ICONS.confirm;
    titleEl.textContent = o.title || kt('confirmTitle', 'Xác nhận');
    msgEl.textContent = job.message;
    actionsEl.textContent = '';
    var primary;
    actionsEl.appendChild(button(o.cancelText || kt('cancel', 'Huỷ'), 'kd__btn--ghost', function () { finish(false); }));
    primary = button(o.okText || kt('confirmTitle', 'Xác nhận'), o.danger ? 'kd__btn--danger' : '', function () { finish(true); });
    actionsEl.appendChild(primary);
    root.hidden = false; document.body.classList.add('kd-lock');
    void root.offsetWidth; root.classList.add('is-open');
    primary.focus();
  }

  /* ---------- toast: shows for 2 seconds, then disappears by itself ---------- */
  var TOAST_MS = 2000, stack = null;

  function buildStack() {
    if (stack) return;
    stack = document.createElement('div');
    stack.className = 'kd-stack';
    stack.setAttribute('role', 'status');
    stack.setAttribute('aria-live', 'polite');
    document.body.appendChild(stack);
  }

  function toast(message, opts, type) {
    return new Promise(function (resolve) {
      function run() {
        buildStack();
        var o = opts || {};
        var el = document.createElement(o.linkHref ? 'a' : 'div');
        el.className = 'kd-toast kd-toast--' + (ICONS[type] ? type : 'info');
        if (o.linkHref) el.href = o.linkHref;
        var ico = document.createElement('span'); ico.className = 'kd-toast__icon'; ico.innerHTML = ICONS[type] || ICONS.info;
        var body = document.createElement('span'); body.className = 'kd-toast__body';
        if (o.title) { var t = document.createElement('strong'); t.textContent = o.title; body.appendChild(t); }
        var m = document.createElement('span'); m.textContent = String(message == null ? '' : message); body.appendChild(m);
        el.appendChild(ico); el.appendChild(body);
        stack.appendChild(el);
        void el.offsetWidth; el.classList.add('is-show');
        var gone = false;
        function hide() {
          if (gone) return; gone = true;
          el.classList.remove('is-show');
          setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); resolve(); }, 300);
        }
        setTimeout(hide, o.duration || TOAST_MS);
      }
      if (document.body) run(); else document.addEventListener('DOMContentLoaded', run);
    });
  }

  /* ---------- confirm: modal dialog, needs an answer ---------- */
  function enqueue(message, opts) {
    return new Promise(function (resolve) {
      queue.push({ isConfirm: true, message: String(message == null ? '' : message), opts: opts || {}, type: 'info', resolve: resolve });
      if (document.body) { if (!current && (!root || root.hidden)) lastFocus = document.activeElement; next(); }
      else document.addEventListener('DOMContentLoaded', next);
    });
  }

  window.KosisDialog = {
    alert: function (message, opts) { opts = opts || {}; return toast(message, opts, opts.type || 'info'); },
    confirm: function (message, opts) { return enqueue(message, opts); }
  };

  /* ---------- <form data-confirm="..."> ---------- */
  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (!form || !form.hasAttribute || !form.hasAttribute('data-confirm') || form.__kdOk) return;
    e.preventDefault();
    window.KosisDialog.confirm(form.getAttribute('data-confirm'), {
      okText: form.getAttribute('data-confirm-ok') || undefined,
      danger: form.hasAttribute('data-confirm-danger')
    }).then(function (ok) {
      if (ok) { form.__kdOk = true; HTMLFormElement.prototype.submit.call(form); }
    });
  }, true);

  /* ---------- server messages: <div hidden data-flash="type"> ---------- */
  function showFlashes() {
    var nodes = Array.prototype.slice.call(document.querySelectorAll('[data-flash]'));
    if (!nodes.length) return;
    nodes.forEach(function (n) {
      var text = (n.textContent || '').trim(), once = n.getAttribute('data-flash-once');
      n.parentNode.removeChild(n);
      if (!text) return;
      if (once) {
        try { if (sessionStorage.getItem('kd:' + once)) return; sessionStorage.setItem('kd:' + once, '1'); } catch (e) {}
      }
      window.KosisDialog.alert(text, {
        type: n.getAttribute('data-flash') || 'info',
        title: n.getAttribute('data-flash-title') || undefined,
        linkHref: n.getAttribute('data-flash-link') || undefined,
        linkText: n.getAttribute('data-flash-link-text') || undefined
      });
    });
    if (window.history && history.replaceState && location.pathname === '/login' && location.search) {
      history.replaceState(null, '', location.pathname);   // a refresh must not replay the message
    }
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', showFlashes); else showFlashes();
})();
