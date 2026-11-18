/* Kho hàng: (1) lọc/tìm + popup xem ảnh ở danh sách, (2) 3 ô ảnh + tự tính % giảm ở form */
(function () {
    var MAX = 5 * 1024 * 1024;

    /* Icon bootstrap chỉ để trang trí: ẩn khỏi trình đọc màn hình */
    Array.prototype.forEach.call(document.querySelectorAll('i.bi'), function (i) { i.setAttribute('aria-hidden', 'true'); });

    /* Mở / đóng popup đúng chuẩn truy cập: aria-hidden đổi theo trạng thái, giữ tiêu điểm trong popup, trả tiêu điểm về nút đã bấm */
    var lastFocus = null;
    function openModal(el, focusEl) {
        lastFocus = document.activeElement;
        el.classList.add('open');
        el.setAttribute('aria-hidden', 'false');
        if (focusEl) focusEl.focus();
    }
    function closeModal(el) {
        el.classList.remove('open');
        el.setAttribute('aria-hidden', 'true');
        if (lastFocus && lastFocus.focus) lastFocus.focus();
        lastFocus = null;
    }
    function trapTab(el, e) {
        if (e.key !== 'Tab' || !el.classList.contains('open')) return;
        var f = el.querySelectorAll('button:not([hidden]), [href], input, select, textarea, [tabindex="0"]');
        if (!f.length) return;
        var first = f[0], last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }

    /* ---------- DANH SÁCH ---------- */
    var body = document.getElementById('invBody');
    if (body) {
        var rows = Array.prototype.slice.call(body.querySelectorAll('tr'));
        var search = document.getElementById('invSearch');
        var chips = document.querySelectorAll('#invChips button');
        var shown = document.getElementById('invShown');
        var empty = document.getElementById('invEmpty');
        var status = 'all';
        var catSel = document.getElementById('invCategory');
        var genSel = document.getElementById('invGender');

        function apply() {
            var t = search.value.trim().toLowerCase();
            var n = 0;
            var cat = catSel ? catSel.value : '';
            var gen = genSel ? genSel.value : '';
            rows.forEach(function (r) {
                var rg = r.dataset.gender || '';
                /* "need" = sắp hết + hết hàng (nút "Xem danh sách" ở thanh cảnh báo) */
                var statusOk = status === 'all' ||
                    (status === 'need' ? (r.dataset.status === 'low' || r.dataset.status === 'out') : r.dataset.status === status);
                var ok = statusOk &&
                         (!t || r.dataset.search.indexOf(t) > -1) &&
                         (!cat || (r.dataset.category || '') === cat) &&
                         (!gen || (gen === 'NONE' ? rg === '' : rg === gen));
                r.hidden = !ok;
                if (ok) n++;
            });
            shown.textContent = n;
            empty.hidden = n > 0;
        }
        search.addEventListener('input', apply);
        if (catSel) catSel.addEventListener('change', apply);
        if (genSel) genSel.addEventListener('change', apply);
        chips.forEach(function (c) {
            c.addEventListener('click', function () {
                chips.forEach(function (x) { x.classList.remove('active'); x.setAttribute('aria-pressed', 'false'); });
                c.classList.add('active');
                c.setAttribute('aria-pressed', 'true');
                status = c.dataset.status;
                apply();
            });
        });

        /* sắp xếp: đổi thứ tự các dòng trong bảng (giữ nguyên bộ lọc đang chọn) */
        var sortSel = document.getElementById('invSort');
        if (sortSel) {
            sortSel.addEventListener('change', function () {
                var mode = sortSel.value;
                var num = function (r, k) { return Number(r.dataset[k]) || 0; };
                rows.slice().sort(function (a, b) {
                    if (mode === 'stock-asc') return num(a, 'stock') - num(b, 'stock') || num(a, 'idx') - num(b, 'idx');
                    if (mode === 'stock-desc') return num(b, 'stock') - num(a, 'stock') || num(a, 'idx') - num(b, 'idx');
                    if (mode === 'price-desc') return num(b, 'price') - num(a, 'price') || num(a, 'idx') - num(b, 'idx');
                    if (mode === 'price-asc') return num(a, 'price') - num(b, 'price') || num(a, 'idx') - num(b, 'idx');
                    return num(a, 'idx') - num(b, 'idx');
                }).forEach(function (r) { body.appendChild(r); });
            });
        }

        /* nút "Xem danh sách" ở thanh cảnh báo: lọc hàng sắp hết + hết hàng, xếp ít nhất lên đầu */
        var goNeed = document.querySelector('[data-go-need]');
        if (goNeed) {
            goNeed.addEventListener('click', function () {
                chips.forEach(function (x) { x.classList.remove('active'); x.setAttribute('aria-pressed', 'false'); });
                status = 'need';
                if (sortSel) { sortSel.value = 'stock-asc'; sortSel.dispatchEvent(new Event('change')); }
                apply();
                var card = document.querySelector('.x-table-card');
                if (card && card.scrollIntoView) card.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        }

        /* nút "Xem danh sách" ở thanh "chờ nhập hàng": chỉ hiện các sản phẩm chưa có phiếu nhập */
        var goPending = document.querySelector('[data-go-pending]');
        if (goPending) {
            goPending.addEventListener('click', function () {
                var chip = document.querySelector('#invChips button[data-status="pending"]');
                if (chip) chip.click();
                var card = document.querySelector('.x-table-card');
                if (card && card.scrollIntoView) card.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        }

        /* popup xem ảnh */
        var modal = document.getElementById('gallery');
        var main = document.getElementById('galleryMain');
        var thumbs = document.getElementById('galleryThumbs');
        var title = document.getElementById('galleryTitle');

        function show(src) {
            main.src = src;
            Array.prototype.forEach.call(thumbs.children, function (t) {
                t.classList.toggle('active', t.getAttribute('data-src') === src);
            });
        }
        function close() { if (modal.classList.contains('open')) closeModal(modal); }

        body.addEventListener('click', function (e) {
            var stack = e.target.closest('.thumb-row[data-images]');
            if (!stack) return;
            var list = stack.dataset.images.split('|');
            title.textContent = stack.dataset.name;
            thumbs.innerHTML = '';
            list.forEach(function (src) {
                var im = document.createElement('img');
                im.src = src;
                im.setAttribute('data-src', src);
                im.alt = 'Ảnh ' + (thumbs.children.length + 1) + ' của ' + stack.dataset.name;
                im.tabIndex = 0;
                im.setAttribute('role', 'button');
                im.addEventListener('click', function () { show(src); });
                im.addEventListener('keydown', function (ev) { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); show(src); } });
                thumbs.appendChild(im);
            });
            show(list[0]);
            openModal(modal, document.getElementById('galleryClose'));
        });
        /* ảnh nhỏ ở bảng mở được bằng Enter / Space */
        body.addEventListener('keydown', function (e) {
            if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('.thumb-row[role="button"]')) {
                e.preventDefault();
                e.target.click();
            }
        });
        document.getElementById('galleryClose').addEventListener('click', close);
        modal.addEventListener('click', function (e) { if (e.target === modal) close(); });
        document.addEventListener('keydown', function (e) { if (e.key === 'Escape') close(); trapTab(modal, e); });
    }

    /* ---------- POPUP XÁC NHẬN XOÁ ---------- */
    var cd = document.getElementById('confirmDelete');
    if (cd) {
        var pendingForm = null;
        var cdImg = document.getElementById('cdImage');
        var cdNoImg = document.getElementById('cdNoImage');
        var cdCancel = document.getElementById('cdCancel');
        var closeCd = function () { if (cd.classList.contains('open')) closeModal(cd); pendingForm = null; };

        document.addEventListener('click', function (e) {
            var btn = e.target.closest('.js-delete');
            if (!btn) return;
            pendingForm = btn.closest('form');
            document.getElementById('cdName').textContent = btn.dataset.name;
            var stock = Number(btn.dataset.stock) || 0;
            document.getElementById('cdMeta').textContent =
                'Mã: ' + btn.dataset.code + ' · Tồn kho: ' + stock.toLocaleString('vi-VN');
            /* còn tồn kho -> chặn xoá, chỉ đường sang trang Tồn kho */
            var warn = document.getElementById('cdStock'), ok = document.getElementById('cdConfirm');
            if (warn && ok) {
                warn.hidden = stock <= 0;
                ok.disabled = stock > 0;
                document.getElementById('cdStockN').textContent = stock.toLocaleString('vi-VN');
                if (btn.dataset.stockUrl) document.getElementById('cdStockLink').setAttribute('href', btn.dataset.stockUrl);
            }
            var hasImg = !!btn.dataset.image;
            cdImg.hidden = !hasImg;
            cdNoImg.hidden = hasImg;
            if (hasImg) cdImg.src = btn.dataset.image;
            openModal(cd, cdCancel);
        });
        document.getElementById('cdConfirm').addEventListener('click', function () {
            if (pendingForm && !document.getElementById('cdConfirm').disabled) pendingForm.submit();
        });
        cdCancel.addEventListener('click', closeCd);
        cd.addEventListener('click', function (e) { if (e.target === cd) closeCd(); });
        document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeCd(); trapTab(cd, e); });
    }

    /* ---------- FORM: THƯ VIỆN ẢNH (thêm bao nhiêu ảnh cũng được) ---------- */
    function notify(msg) {
        if (window.KosisDialog) window.KosisDialog.alert(msg, { type: 'error' }); else window.alert(msg);
    }

    var gallery = document.getElementById('gallery');
    /* Trang danh sách cũng có popup id="gallery" (xem ảnh) - chỉ chạy phần này ở trang form (có ô "thêm ảnh") */
    if (gallery && document.getElementById('galleryAdd')) {
        var MAX_IMAGES = 30;
        var picker = document.getElementById('galleryPicker');
        var addTile = document.getElementById('galleryAdd');
        var newBox = document.getElementById('newImageInputs');
        var orderInput = document.getElementById('imageOrder');
        var countEl = document.getElementById('galleryCount');
        var seq = 0;

        var getCards = function () { return Array.prototype.slice.call(gallery.querySelectorAll('.g-card')); };

        /* cập nhật nhãn "Ảnh chính / Ảnh N", nút mũi tên và ô "imageOrder" gửi lên server */
        var refresh = function () {
            var list = getCards();
            list.forEach(function (c, i) {
                c.querySelector('.g-tag').textContent = i === 0 ? 'Ảnh chính' : 'Ảnh ' + (i + 1);
                c.classList.toggle('is-main', i === 0);
                c.querySelector('.g-left').disabled = i === 0;
                c.querySelector('.g-right').disabled = i === list.length - 1;
            });
            var inputs = Array.prototype.slice.call(newBox.querySelectorAll('input[type=file]'));
            orderInput.value = list.map(function (c) {
                return c.dataset.kind === 'e' ? 'e:' + c.dataset.eidx : 'n:' + inputs.indexOf(c._input);
            }).join(',');
            countEl.textContent = list.length + ' ảnh';
            addTile.hidden = list.length >= MAX_IMAGES;
            document.dispatchEvent(new CustomEvent('gallerychange'));
        };

        var wire = function (card) {
            card.querySelector('.g-left').addEventListener('click', function () {
                var prev = card.previousElementSibling;
                if (prev && prev.classList.contains('g-card')) { gallery.insertBefore(card, prev); refresh(); }
            });
            card.querySelector('.g-right').addEventListener('click', function () {
                var next = card.nextElementSibling;
                if (next && next.classList.contains('g-card')) { gallery.insertBefore(next, card); refresh(); }
            });
            card.querySelector('.g-del').addEventListener('click', function () {
                if (card._input) card._input.remove();
                card.remove();
                refresh();
            });
        };

        var addFiles = function (files) {
            Array.prototype.forEach.call(files, function (f) {
                if (!f.type || f.type.indexOf('image/') !== 0) { notify('"' + f.name + '" không phải file ảnh (JPG, PNG, WEBP hoặc GIF).'); return; }
                if (f.size > MAX) { notify('Ảnh "' + f.name + '" vượt quá 5MB, vui lòng chọn ảnh nhỏ hơn.'); return; }
                if (getCards().length >= MAX_IMAGES) { notify('Mỗi sản phẩm tối đa ' + MAX_IMAGES + ' ảnh.'); return; }

                var input = document.createElement('input');
                input.type = 'file';
                input.name = 'newImages';
                var dt = new DataTransfer();
                dt.items.add(f);
                input.files = dt.files;
                newBox.appendChild(input);

                var card = document.createElement('div');
                card.className = 'g-card';
                card.dataset.kind = 'n';
                card.dataset.key = 'n' + (++seq);
                card._input = input;
                card.innerHTML =
                    '<img alt=""><span class="g-tag"></span>' +
                    '<div class="g-actions">' +
                    '<button type="button" class="g-left" title="Dời sang trái" aria-label="Dời sang trái"><i class="bi bi-chevron-left"></i></button>' +
                    '<button type="button" class="g-right" title="Dời sang phải" aria-label="Dời sang phải"><i class="bi bi-chevron-right"></i></button>' +
                    '<button type="button" class="g-del" title="Xoá ảnh" aria-label="Xoá ảnh"><i class="bi bi-x-lg"></i></button>' +
                    '</div>';
                card.querySelector('img').src = URL.createObjectURL(f);
                wire(card);
                gallery.insertBefore(card, addTile);
            });
            refresh();
        };

        getCards().forEach(wire);
        addTile.addEventListener('click', function () { picker.click(); });
        picker.addEventListener('change', function () { addFiles(picker.files); picker.value = ''; });

        gallery.addEventListener('dragover', function (e) { e.preventDefault(); gallery.classList.add('drag'); });
        gallery.addEventListener('dragleave', function (e) { if (!gallery.contains(e.relatedTarget)) gallery.classList.remove('drag'); });
        gallery.addEventListener('drop', function (e) {
            e.preventDefault();
            gallery.classList.remove('drag');
            if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
        });

        refresh();
    }

    /* ---------- FORM: Ô TIỀN CÓ DẤU CHẤM NGĂN CÁCH (890.000) ---------- */
    var fmt = function (n) { return Math.round(n).toLocaleString('vi-VN'); };
    document.querySelectorAll('.money-input').forEach(function (view) {
        var target = document.getElementById(view.dataset.target);
        var digits = function (t) { return (t || '').replace(/\D/g, ''); };
        var show = function (raw) { view.value = raw === '' ? '' : Number(raw).toLocaleString('vi-VN'); };
        // Ô giá vốn ở form THÊM: để trống khi chưa có giá (hiện chữ mờ 0) thay vì hiện sẵn số 0 phải xoá đi
        var startRaw = digits(target.value);
        show(view.hasAttribute('data-blank-zero') && view.getAttribute('data-blank-zero') === 'true' && Number(startRaw) === 0 ? '' : startRaw);
        view.addEventListener('input', function () {
            var fromEnd = view.value.length - view.selectionStart;
            var raw = digits(view.value).replace(/^0+(?=\d)/, '');
            target.value = raw === '' ? '0' : raw;
            show(raw);
            var pos = Math.max(0, view.value.length - fromEnd);
            view.setSelectionRange(pos, pos);
            target.dispatchEvent(new Event('input'));
        });
    });

    /* ---------- FORM: TỰ TÍNH SỐ TIỀN GIẢM + GIÁ SAU GIẢM + LỢI NHUẬN ---------- */
    var listPrice = document.getElementById('listPrice');
    var pct = document.getElementById('discountPercent');
    var amountBox = document.getElementById('discountAmount');
    var finalBox = document.getElementById('finalPrice');
    var importPrice = document.getElementById('importPrice');
    if (listPrice && pct && amountBox && finalBox) {
        var set = function (id, text) { var el = document.getElementById(id); if (el) el.textContent = text; };
        var calc = function () {
            var list = Math.max(0, Number(listPrice.value) || 0);
            var p = Math.min(90, Math.max(0, Number(pct.value) || 0));
            var amount = Math.round(list * p / 100);
            var final = list - amount;
            var cost = Number(importPrice.value) || 0;
            var profit = final - cost;

            amountBox.value = fmt(amount) + '₫';
            finalBox.value = fmt(final) + '₫';
            set('bkList', fmt(list) + '₫');
            set('bkDiscount', '-' + fmt(amount) + '₫');
            set('bkFinal', fmt(final) + '₫');
            set('bkCost', fmt(cost) + '₫');
            set('bkProfit', (profit < 0 ? '-' : '') + fmt(Math.abs(profit)) + '₫');
            var row = document.getElementById('bkProfitRow');
            if (row) row.classList.toggle('loss', profit < 0);
        };
        [listPrice, pct, importPrice].forEach(function (el) { el.addEventListener('input', calc); });
        calc();
    }
})();