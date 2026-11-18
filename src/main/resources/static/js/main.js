/* ==========================================================================
   KOSIS storefront behaviour (vanilla ES6, no dependencies)
   header · drawer · search · tabs · rail · wishlist (tài khoản + trình duyệt) · quick view · add to bag
   newsletter · product gallery / size / colour / quantity · reveal on scroll
   ========================================================================== */
(function () {
  'use strict';
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var FALLBACK_IMG = '/images/products/placeholder.svg';

  /* ---------- Toast ---------- */
  var toastTimer;
  function toast(msg, icon) {
    var el = $('[data-toast]');
    if (!el) return;
    el.innerHTML = '<i class="bi ' + (icon || 'bi-check2-circle') + '"></i><span></span>';
    el.lastChild.textContent = msg;
    el.classList.add('is-show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('is-show'); }, 2600);
  }
  window.kosisToast = toast;

  /* Notification toast: shows 2 seconds then disappears by itself (falls back to the bottom toast if the dialog script is missing) */
  function popup(msg, type, opts) {
    if (window.KosisDialog) return window.KosisDialog.alert(msg, Object.assign({ type: type }, opts || {}));
    toast(msg, type === 'error' || type === 'warn' ? 'bi-exclamation-circle' : 'bi-check2-circle');
    return Promise.resolve();
  }

  /* ---------- Header: shadow when stuck, drawer, search overlay, account menu ---------- */
  var header = $('#siteHeader');
  function onScroll() {
    if (header) {
      /* thu gọn khi cuộn quá 120px, mở lại khi về gần đầu trang (<40px) — tránh nhấp nháy vì chiều cao menu đổi */
      var y = window.scrollY, stuck = header.classList.contains('is-stuck');
      if (!stuck && y > 120) header.classList.add('is-stuck');
      else if (stuck && y < 40) header.classList.remove('is-stuck');
    }
    var top = $('[data-to-top]');
    if (top) top.classList.toggle('is-show', window.scrollY > 600);
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
  var toTop = $('[data-to-top]');
  if (toTop) toTop.addEventListener('click', function () { window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' }); });

  var drawer = $('#mobileDrawer'), scrim = $('[data-scrim]'), search = $('#searchOverlay');
  function setOpen(el, open) {
    if (!el) return;
    el.classList.toggle('is-open', open);
    el.setAttribute('aria-hidden', open ? 'false' : 'true');
  }
  function closeOverlays() {
    setOpen(drawer, false); setOpen(search, false);
    if (scrim) scrim.classList.remove('is-open');
    $$('[data-drawer-open]').forEach(function (b) { b.setAttribute('aria-expanded', 'false'); });
    document.body.style.overflow = '';
  }
  $$('[data-drawer-open]').forEach(function (b) {
    b.addEventListener('click', function () {
      setOpen(drawer, true); scrim.classList.add('is-open'); b.setAttribute('aria-expanded', 'true');
      document.body.style.overflow = 'hidden';
    });
  });
  $$('[data-drawer-close]').forEach(function (b) { b.addEventListener('click', closeOverlays); });
  $$('[data-search-open]').forEach(function (b) {
    b.addEventListener('click', function () {
      setOpen(search, true); scrim.classList.add('is-open');
      var input = $('input', search); if (input) setTimeout(function () { input.focus(); }, 250);
    });
  });
  $$('[data-search-close]').forEach(function (b) { b.addEventListener('click', closeOverlays); });
  if (scrim) scrim.addEventListener('click', closeOverlays);
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') { closeOverlays(); closeQuickView(); $$('.is-open[data-account-menu], .is-open[data-select]').forEach(function (x) { x.classList.remove('is-open'); }); }
  });
  window.addEventListener('resize', function () { if (window.innerWidth > 900) closeOverlays(); });

  /* account dropdown + collection selector (click to open, click outside to close) */
  function dropdown(rootSel, toggleSel) {
    $$(rootSel).forEach(function (root) {
      var t = $(toggleSel, root);
      if (!t) return;
      t.addEventListener('click', function (e) {
        e.stopPropagation();
        var open = !root.classList.contains('is-open');
        $$(rootSel).forEach(function (r) { r.classList.remove('is-open'); });
        root.classList.toggle('is-open', open);
        t.setAttribute('aria-expanded', open ? 'true' : 'false');
      });
    });
  }
  dropdown('[data-account-menu]', '[data-account-toggle]');
  dropdown('[data-select]', '[data-select-toggle]');
  document.addEventListener('click', function (e) {
    $$('[data-account-menu].is-open, [data-select].is-open').forEach(function (r) { if (!r.contains(e.target)) r.classList.remove('is-open'); });
  });

  /* ---------- Home: season tabs ---------- */
  var tabs = $$('[data-tab]');
  tabs.forEach(function (tab) {
    tab.addEventListener('click', function () {
      tabs.forEach(function (t) { var on = t === tab; t.classList.toggle('is-on', on); t.setAttribute('aria-selected', on ? 'true' : 'false'); });
      $$('[data-panel]').forEach(function (p) {
        var show = p.getAttribute('data-panel') === tab.getAttribute('data-tab');
        p.hidden = !show;
        if (show) p.classList.add('is-in');
      });
    });
  });

  /* ---------- Home: new-arrivals rail ---------- */
  $$('[data-rail]').forEach(function (rail) {
    var wrap = rail.parentNode, prev = $('[data-rail-prev]', wrap), next = $('[data-rail-next]', wrap);
    function step() { var c = rail.firstElementChild; return c ? c.getBoundingClientRect().width + 16 : 280; }
    function sync() {
      if (prev) prev.disabled = rail.scrollLeft < 4;
      if (next) next.disabled = rail.scrollLeft + rail.clientWidth >= rail.scrollWidth - 4;
    }
    if (prev) prev.addEventListener('click', function () { rail.scrollBy({ left: -step() * 2, behavior: 'smooth' }); });
    if (next) next.addEventListener('click', function () { rail.scrollBy({ left: step() * 2, behavior: 'smooth' }); });
    rail.addEventListener('scroll', sync, { passive: true });
    window.addEventListener('resize', sync);
    sync();
  });

  /* ---------- Home: editorial Women / Men toggle ---------- */
  $$('[data-editorial]').forEach(function (card) {
    var word = $('[data-ed-word]', card), link = $('[data-ed-link]', card);
    $$('[data-ed]', card).forEach(function (b) {
      b.addEventListener('click', function () {
        var g = b.getAttribute('data-ed');
        $$('[data-ed]', card).forEach(function (x) { var on = x === b; x.classList.toggle('is-on', on); x.setAttribute('aria-pressed', on ? 'true' : 'false'); });
        if (word) word.textContent = g;
        if (link) link.setAttribute('href', '/' + g);
      });
    });
  });

  /* ---------- Reveal on scroll ---------- */
  var reveals = $$('.reveal');
  if ('IntersectionObserver' in window && !reduce) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) { if (en.isIntersecting) { en.target.classList.add('is-in'); io.unobserve(en.target); } });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.05 });
    reveals.forEach(function (el) { io.observe(el); });
  } else {
    reveals.forEach(function (el) { el.classList.add('is-in'); });
  }

  /* ---------- Wishlist ----------
     Chưa đăng nhập: lưu trên trình duyệt (localStorage).
     Đã đăng nhập: lưu vào tài khoản trên server (đổi thiết bị vẫn còn); localStorage chỉ là bản sao để vẽ nhanh,
     và danh sách đã lưu trên trình duyệt trước khi đăng nhập được gộp vào tài khoản. */
  var WL_KEY = 'kosis_wishlist';
  var WL_OWNER_KEY = 'kosis_wishlist_owner';
  var authMeta = $('meta[name="kosis-auth"]');
  var WL_USER = authMeta ? authMeta.getAttribute('content') : '';
  var csrfMeta = $('meta[name="_csrf"]'), csrfHeaderMeta = $('meta[name="_csrf_header"]');
  function wlRead() { try { var v = JSON.parse(localStorage.getItem(WL_KEY) || '[]'); return Array.isArray(v) ? v.map(String) : []; } catch (e) { return []; } }
  function wlWrite(list) { try { localStorage.setItem(WL_KEY, JSON.stringify(list)); } catch (e) { /* storage blocked */ } }
  function wlApi(path, params) {
    var headers = { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest' };
    if (csrfMeta && csrfHeaderMeta) headers[csrfHeaderMeta.getAttribute('content')] = csrfMeta.getAttribute('content');
    return fetch('/wishlist/api/' + path, { method: 'POST', credentials: 'same-origin', headers: headers, body: params.toString() })
      .then(function (r) { if (!r.ok) throw new Error('wishlist ' + r.status); return r.json(); });
  }
  function wlPaint() {
    var list = wlRead();
    $$('[data-wishlist-toggle]').forEach(function (b) {
      var on = list.indexOf(b.getAttribute('data-id')) > -1, i = $('i', b);
      b.classList.toggle('is-on', on); b.setAttribute('aria-pressed', on ? 'true' : 'false');
      if (i) { i.classList.toggle('bi-heart-fill', on); i.classList.toggle('bi-heart', !on); }
      b.setAttribute('aria-label', on ? 'Bỏ khỏi yêu thích' : 'Thêm vào yêu thích');
    });
    $$('[data-wishlist-count]').forEach(function (c) { c.textContent = list.length; c.hidden = list.length === 0; });
    var grid = $('[data-wishlist-grid]');
    if (grid) {
      var shown = 0;
      $$('.p-card', grid).forEach(function (card) { var ok = list.indexOf(card.getAttribute('data-id')) > -1; card.hidden = !ok; if (ok) shown++; });
      var empty = $('[data-wishlist-empty]'); if (empty) empty.hidden = shown > 0;
      var cnt = $('[data-wishlist-total]'); if (cnt) cnt.textContent = shown + (shown === 1 ? ' sản phẩm' : ' sản phẩm');
      grid.hidden = shown === 0;
    }
  }
  /* Đăng nhập rồi: đồng bộ với server (gộp danh sách trên trình duyệt này vào tài khoản) */
  function wlSync() {
    if (!WL_USER) return;
    try {
      // localStorage thuộc về tài khoản khác (dùng chung máy) -> bỏ, không gộp nhầm vào tài khoản này
      var owner = localStorage.getItem(WL_OWNER_KEY);
      if (owner && owner !== WL_USER) wlWrite([]);
      localStorage.setItem(WL_OWNER_KEY, WL_USER);
    } catch (e) { /* storage blocked */ }
    var params = new URLSearchParams();
    wlRead().forEach(function (id) { params.append('ids', id); });
    wlApi('merge', params).then(function (data) { wlWrite((data.ids || []).map(String)); wlPaint(); }).catch(function () { /* giữ bản đang có */ });
  }
  /* Đăng xuất: xoá bản sao trên trình duyệt để người dùng sau trên máy này không thấy/không gộp danh sách của bạn */
  document.addEventListener('submit', function (e) {
    var f = e.target;
    if (f && f.getAttribute && /\/logout$/.test(f.getAttribute('action') || '')) {
      try { localStorage.removeItem(WL_KEY); localStorage.removeItem(WL_OWNER_KEY); } catch (x) { /* ignore */ }
    }
  });
  document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-wishlist-toggle]');
    if (!b) return;
    e.preventDefault();
    var id = b.getAttribute('data-id'), list = wlRead(), i = list.indexOf(id);
    if (i > -1) list.splice(i, 1); else list.push(id);
    wlWrite(list); wlPaint();
    if (WL_USER) {
      wlApi('toggle', new URLSearchParams({ productId: id })).then(function (data) { wlWrite((data.ids || []).map(String)); wlPaint(); })
        .catch(function () { toast('Chưa lưu được vào tài khoản, vui lòng thử lại', 'bi-exclamation-circle'); });
    }
    toast(i > -1 ? 'Đã bỏ khỏi yêu thích' : 'Đã lưu vào yêu thích', i > -1 ? 'bi-heart' : 'bi-heart-fill');
  });
  wlPaint();
  wlSync();

  /* ---------- Add to bag (AJAX) ---------- */
  function updateCartBadge(count) {
    $$('.cart-count').forEach(function (el) { el.textContent = count; });
    $$('.cart-btn').forEach(function (btn) { btn.classList.remove('cart-bump'); void btn.offsetWidth; btn.classList.add('cart-bump'); });
  }
  function flyToCart(srcImg, done) {
    var target = $('.cart-btn');
    if (!target || !srcImg || reduce || typeof document.body.animate !== 'function') { done(); return; }
    var s = srcImg.getBoundingClientRect(), t = target.getBoundingClientRect();
    if (!s.width) { done(); return; }
    var size = Math.min(s.width, s.height, 150);
    var x = s.left + s.width / 2 - size / 2, y = s.top + s.height / 2 - size / 2;
    var dx = t.left + t.width / 2 - (x + size / 2), dy = t.top + t.height / 2 - (y + size / 2);
    var fly = document.createElement('img');
    fly.src = srcImg.currentSrc || srcImg.src; fly.alt = ''; fly.className = 'fly';
    fly.style.cssText = 'left:' + x + 'px;top:' + y + 'px;width:' + size + 'px;height:' + size + 'px';
    document.body.appendChild(fly);
    var finished = false;
    function end() { if (finished) return; finished = true; fly.remove(); done(); }
    var a = fly.animate([
      { transform: 'translate(0,0) scale(1)', opacity: 1 },
      { transform: 'translate(' + dx * 0.5 + 'px,' + (Math.min(dy * 0.5, 0) - 60) + 'px) scale(.6)', opacity: .95, offset: .45 },
      { transform: 'translate(' + dx + 'px,' + dy + 'px) scale(.1)', opacity: .3 }
    ], { duration: 800, easing: 'ease-in-out' });
    a.onfinish = end; setTimeout(end, 1100);
  }
  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (!form.matches || !form.matches('form.js-add-cart')) return;
    if (form.dataset.busy === '1') { e.preventDefault(); return; }
    if (e.submitter && e.submitter.name === 'buyNow') { form.dataset.busy = '1'; return; }   // Buy now: normal submit -> /gio-hang
    e.preventDefault();
    form.dataset.busy = '1';
    var card = form.closest('.p-card');
    var img = (card && $('.p-card__media img', card)) || $('#detailMain');
    fetch(form.action, {
      method: 'POST', credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(new FormData(form)).toString()
    }).then(function (res) {
      return res.json().then(function (d) { return { ok: res.ok, d: d }; }, function () { return { ok: false, d: { message: 'Đã có lỗi xảy ra, vui lòng tải lại trang.' } }; });
    }).then(function (r) {
      form.dataset.busy = '0';
      var msg = r.d.message || 'Đã thêm vào giỏ hàng';
      var go = function () { return popup(msg, r.ok ? 'success' : 'error').then(function () { if (r.d.redirect) window.location.href = r.d.redirect; }); };
      if (r.ok && typeof r.d.count === 'number') flyToCart(img, function () { updateCartBadge(r.d.count); go(); });
      else go();
    }, function () { form.dataset.busy = '1'; form.submit(); });
  });
  window.addEventListener('pageshow', function () { $$('form.js-add-cart').forEach(function (f) { f.dataset.busy = '0'; }); });

  function submitCardForm(card, size) {
    var form = $('form.js-add-cart', card);
    if (!form) return;
    $('input[name="size"]', form).value = size || '';
    if (form.requestSubmit) form.requestSubmit();
    else form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
  }

  /* ---------- Quick view + quick add ---------- */
  var qv = null, qvLast = null;
  function parseSizeStock(raw) {
    var m = {};
    (raw || '').split(',').forEach(function (t) { var i = t.lastIndexOf(':'); if (i > 0) m[t.slice(0, i).trim().toUpperCase()] = parseInt(t.slice(i + 1), 10) || 0; });
    return m;
  }
  function closeQuickView() {
    if (!qv) return;
    qv.remove(); qv = null;
    document.body.style.overflow = '';
    if (qvLast) qvLast.focus();
  }
  function openQuickView(card) {
    closeQuickView();
    qvLast = document.activeElement;
    var d = card.dataset, sizes = d.sizes ? d.sizes.split(',') : [], stock = parseSizeStock(d.sizestock), chosen = '';
    qv = document.createElement('div');
    qv.className = 'qv'; qv.setAttribute('role', 'dialog'); qv.setAttribute('aria-modal', 'true'); qv.setAttribute('aria-label', 'Xem nhanh: ' + d.name);
    var html = '<div class="qv__scrim" data-qv-close></div><div class="qv__panel">' +
      '<button class="icon-btn qv__close" type="button" aria-label="Đóng" data-qv-close><i class="bi bi-x-lg"></i></button>' +
      '<div class="qv__img"><img alt=""></div><div class="qv__info"><h2 class="qv__name"></h2>' +
      '<div class="qv__price"><span class="qv__now"></span><s class="qv__old"></s></div><p class="qv__desc"></p>';
    if (sizes.length) html += '<div><div class="qv__label"><span>Kích cỡ</span><b data-qv-msg></b></div><div class="size-list" role="radiogroup" aria-label="Kích cỡ"></div></div>';
    html += '<div class="qv__actions"><button class="btn btn--dark" type="button" data-qv-add><i class="bi bi-bag-plus"></i> Thêm vào giỏ</button>' +
      '<a class="btn btn--outline" data-qv-link>Xem chi tiết</a></div></div></div>';
    qv.innerHTML = html;
    var img = $('.qv__img img', qv);
    img.src = d.img || FALLBACK_IMG; img.alt = d.name; img.onerror = function () { img.onerror = null; img.src = FALLBACK_IMG; };
    $('.qv__name', qv).textContent = d.name;
    $('.qv__now', qv).textContent = d.price;
    var old = $('.qv__old', qv); if (d.old) old.textContent = d.old; else old.remove();
    $('.qv__desc', qv).textContent = d.desc || '';
    $('[data-qv-link]', qv).setAttribute('href', d.url);
    var list = $('.size-list', qv);
    sizes.forEach(function (sz) {
      var b = document.createElement('button');
      b.type = 'button'; b.className = 'size-chip'; b.textContent = sz;
      var left = d.sizestock ? (stock[sz.toUpperCase()] || 0) : null;
      if (left === 0) { b.disabled = true; b.title = 'Hết hàng'; }
      b.addEventListener('click', function () {
        chosen = sz;
        $$('.size-chip', list).forEach(function (x) { x.classList.toggle('is-on', x === b); });
        $('[data-qv-msg]', qv).textContent = left !== null && left > 0 && left <= 5 ? 'Chỉ còn ' + left + ' sản phẩm' : '';
      });
      list.appendChild(b);
    });
    $('[data-qv-add]', qv).addEventListener('click', function () {
      if (sizes.length && !chosen) { $('[data-qv-msg]', qv).textContent = 'Vui lòng chọn kích cỡ'; popup('Vui lòng chọn kích cỡ trước khi thêm vào giỏ hàng.', 'warn'); return; }
      closeQuickView(); submitCardForm(card, chosen);
    });
    qv.addEventListener('click', function (e) { if (e.target.closest('[data-qv-close]')) closeQuickView(); });
    document.body.appendChild(qv); document.body.style.overflow = 'hidden';
    var first = $('.qv__close', qv); if (first) first.focus();
  }
  document.addEventListener('click', function (e) {
    var qvBtn = e.target.closest('[data-quick-view]'), addBtn = e.target.closest('[data-quick-add]');
    if (!qvBtn && !addBtn) return;
    var card = (qvBtn || addBtn).closest('.p-card');
    if (!card) return;
    e.preventDefault();
    if (qvBtn || card.dataset.sizes) openQuickView(card);   // sized products need a size first
    else submitCardForm(card, '');
  });

  /* ---------- Newsletter ---------- */
  $$('[data-newsletter]').forEach(function (form) {
    var msg = form.parentNode.querySelector('[data-newsletter-msg]'), input = $('input[type="email"]', form);
    function say(text, cls) { if (msg) { msg.textContent = ''; msg.className = 'newsletter__msg'; } popup(text, cls === 'is-ok' ? 'success' : 'error'); }
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var v = input.value.trim();
      var ok = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v);
      form.classList.toggle('is-invalid', !ok);
      if (!ok) { say(v ? 'Email chưa hợp lệ.' : 'Vui lòng nhập email của bạn.', 'is-err'); setTimeout(function () { input.focus(); }, 250); return; }
      var btn = $('button[type="submit"]', form); btn.disabled = true;
      fetch(form.action, { method: 'POST', credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(new FormData(form)).toString() })
        .then(function (r) { return r.json().then(function (d) { return { ok: r.ok && d.ok, d: d }; }); })
        .then(function (r) { say(r.d.message || 'Cảm ơn bạn!', r.ok ? 'is-ok' : 'is-err'); if (r.ok) form.reset(); })
        .catch(function () { say('Hiện chưa thể đăng ký. Vui lòng thử lại.', 'is-err'); })
        .then(function () { btn.disabled = false; });
    });
    input.addEventListener('input', function () { form.classList.remove('is-invalid'); });
  });

  /* ---------- Product detail: gallery, colour, size, quantity ---------- */
  var main = $('#detailMain');
  $$('[data-thumb]').forEach(function (t) {
    t.addEventListener('click', function () {
      main.src = t.getAttribute('data-thumb');
      $$('[data-thumb]').forEach(function (x) { x.classList.toggle('is-on', x === t); });
    });
  });
  var zoom = $('[data-zoom]');
  if (zoom && !reduce) {
    zoom.addEventListener('mousemove', function (e) {
      var r = zoom.getBoundingClientRect();
      main.style.transformOrigin = ((e.clientX - r.left) / r.width * 100) + '% ' + ((e.clientY - r.top) / r.height * 100) + '%';
    });
  }
  /* Chọn màu -> đổi ảnh chính sang ảnh gắn với màu đó (data-img), đồng bộ thumbnail */
  function showColorImage(img) {
    if (!img || !main) return;
    if (main.getAttribute('src') !== img) main.src = img;
    $$('[data-thumb]').forEach(function (x) { x.classList.toggle('is-on', x.getAttribute('data-thumb') === img); });
  }
  /* Tồn kho theo màu x size: form có data-matrix = "Màu|SIZE:số,..." (rỗng nếu sản phẩm chưa chia kho theo màu) */
  var pdForm = $('form[data-pd-form]');
  var matrix = {};
  if (pdForm) {
    (pdForm.getAttribute('data-matrix') || '').split(',').forEach(function (t) {
      var i = t.lastIndexOf(':');
      if (i > 0) {
        /* khoá = "màu(chữ thường)|SIZE(chữ HOA)" - phải khớp với leftOf() và với Java (variantRawKey) */
        var key = t.slice(0, i).trim(), bar = key.lastIndexOf('|');
        if (bar >= 0) matrix[key.slice(0, bar).trim().toLowerCase() + '|' + key.slice(bar + 1).trim().toUpperCase()] = parseInt(t.slice(i + 1), 10) || 0;
      }
    });
  }
  var hasMatrix = Object.keys(matrix).length > 0;
  function chosenColor() { var c = $('[data-color-input]'); return c ? c.value : ''; }
  /* số còn lại của (size, màu đang chọn); null = không có số liệu theo màu (dùng data-stock của server) */
  function leftOf(size) {
    if (!hasMatrix) return null;
    var v = matrix[chosenColor().toLowerCase() + '|' + String(size || '').toUpperCase()];
    return v === undefined ? 0 : v;
  }
  var syncStockUi = function () {};

  $$('[data-color]').forEach(function (b) {
    b.addEventListener('click', function () {
      $$('[data-color]').forEach(function (x) { var on = x === b; x.classList.toggle('is-on', on); x.setAttribute('aria-checked', on ? 'true' : 'false'); });
      var label = $('[data-color-name]'); if (label) label.textContent = b.getAttribute('data-color');
      var colorInput = $('[data-color-input]'); if (colorInput) colorInput.value = b.getAttribute('data-color');   // gửi màu đã chọn lên giỏ hàng
      showColorImage(b.getAttribute('data-img'));
      syncStockUi();
    });
  });
  var firstColor = $('[data-color].is-on');
  if (firstColor) showColorImage(firstColor.getAttribute('data-img'));
  if (pdForm) {
    var qty = $('#detailQty'), total = $('#pdTotal'), msg = $('#pdSizeMsg');
    var price = qty ? parseInt(qty.getAttribute('data-price'), 10) || 0 : 0;
    var clamp = function (v) { var max = parseInt(qty.getAttribute('max'), 10) || 99; v = parseInt(v, 10); if (isNaN(v) || v < 1) v = 1; return Math.min(v, max); };
    var sync = function () { qty.value = clamp(qty.value); if (total) total.textContent = (price * qty.value).toLocaleString('vi-VN') + ' ₫'; };
    if (qty) {
      $('#qtyMinus').addEventListener('click', function () { qty.value = clamp(qty.value) - 1; sync(); });
      $('#qtyPlus').addEventListener('click', function () { qty.value = clamp(qty.value) + 1; sync(); });
      qty.addEventListener('input', sync); qty.addEventListener('change', sync);
    }
    var submitBtns = $$('.pd-actions button[type="submit"]', pdForm);
    var sizeRadios = $$('input[name="size"]', pdForm);

    /* Đổi màu / đổi size -> cập nhật size nào còn hàng, số lượng tối đa và thông báo "chỉ còn N" theo ĐÚNG màu đang chọn */
    syncStockUi = function () {
      if (!hasMatrix) return;
      var anyLeft = false;
      sizeRadios.forEach(function (r) {
        var left = leftOf(r.value);
        r.setAttribute('data-stock', left);
        r.disabled = left <= 0;
        if (left <= 0 && r.checked) r.checked = false;      // size đang chọn đã hết ở màu này -> bỏ chọn
        if (left > 0) anyLeft = true;
      });
      var left = null;
      if (sizeRadios.length) {
        var checked = $('input[name="size"]:checked', pdForm);
        left = checked ? leftOf(checked.value) : null;
      } else {
        left = leftOf('');                                  // sản phẩm không có size: chỉ có số lượng của màu
        anyLeft = left > 0;
      }
      if (msg) {
        msg.textContent = !anyLeft ? 'Màu này đã hết hàng'
          : (left !== null && left > 0 && left <= 5 ? 'Chỉ còn ' + left + ' sản phẩm' : '');
      }
      submitBtns.forEach(function (b) { b.disabled = !anyLeft; });
      if (qty && left !== null && left > 0) { qty.setAttribute('max', Math.min(left, 99)); sync(); }
    };

    sizeRadios.forEach(function (r) {
      r.addEventListener('change', function () {
        var left = hasMatrix ? leftOf(r.value) : parseInt(r.getAttribute('data-stock'), 10);
        if (msg) msg.textContent = left > 0 && left <= 5 ? 'Chỉ còn ' + left + ' sản phẩm' : '';
        if (qty && left > 0) { qty.setAttribute('max', Math.min(left, 99)); sync(); }
      });
    });
    pdForm.addEventListener('submit', function (e) {
      var needs = $('input[name="size"]', pdForm);
      if (needs && !$('input[name="size"]:checked', pdForm)) { e.preventDefault(); e.stopImmediatePropagation(); if (msg) msg.textContent = 'Vui lòng chọn kích cỡ'; popup('Vui lòng chọn kích cỡ trước khi thêm vào giỏ hàng.', 'warn'); }
    }, true);
    syncStockUi();
  }

  /* ---------- Listing: auto-submit sort ---------- */
  $$('[data-autosubmit]').forEach(function (s) { s.addEventListener('change', function () { s.form.submit(); }); });

  /* ---------- Contact: topic chips + character counter ---------- */
  var topics = $$('[data-topic]');
  if (topics.length) {
    var subj = $('#c-sub');
    topics.forEach(function (b) {
      b.addEventListener('click', function () {
        topics.forEach(function (x) { x.classList.toggle('is-on', x === b); });
        if (subj) { subj.value = b.getAttribute('data-topic'); }
        var msg = $('#c-msg'); if (msg) msg.focus();
      });
    });
  }
  $$('[data-counter]').forEach(function (t) {
    var out = $(t.getAttribute('data-counter'));
    var upd = function () { if (out) out.textContent = t.value.length; };
    t.addEventListener('input', upd); upd();
  });
})();
