/* ==========================================================================
   Trang "Nhập hàng" (dashboard)
   - /dashboard/nhap-hang       : lọc lịch sử, mở/đóng chi tiết phiếu, bấm nguồn hàng để lọc
   - /dashboard/nhap-hang/them  : form tạo phiếu (thêm/xoá dòng, chọn size, tự tính thành tiền)
   Một file dùng cho cả hai trang; mỗi phần chỉ chạy khi trang có phần tử tương ứng.
   ========================================================================== */
(function () {
    'use strict';

    // Icon bootstrap chỉ để trang trí: ẩn khỏi trình đọc màn hình
    Array.prototype.forEach.call(document.querySelectorAll('i.bi'), function (i) { i.setAttribute('aria-hidden', 'true'); });

    function fmt(n) {
        return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    }
    function digits(s) {
        return String(s || '').replace(/\D/g, '');
    }
    function num(s) {
        var d = digits(s);
        return d ? parseInt(d, 10) : 0;
    }

    /* ======================= TRANG LỊCH SỬ ======================= */
    var history = document.getElementById('imHistory');
    if (history) {
        var recs = Array.prototype.slice.call(history.querySelectorAll('tbody.im-rec'));
        var search = document.getElementById('imSearch');
        var supplier = document.getElementById('imSupplier');
        var from = document.getElementById('imFrom');
        var to = document.getElementById('imTo');
        var shown = document.getElementById('imShown');
        var shownCost = document.getElementById('imShownCost');
        var empty = document.getElementById('imEmpty');

        function apply() {
            var q = search.value.trim().toLowerCase();
            var sup = supplier.value.trim().toLowerCase();
            var f = from.value, t = to.value;
            var count = 0, cost = 0;
            recs.forEach(function (r) {
                var ok = true;
                if (q && (r.getAttribute('data-search') || '').indexOf(q) === -1) ok = false;
                if (ok && sup && (r.getAttribute('data-supplier') || '').toLowerCase() !== sup) ok = false;
                var d = r.getAttribute('data-date') || '';
                if (ok && f && d < f) ok = false;
                if (ok && t && d > t) ok = false;
                r.hidden = !ok;
                if (ok) {
                    count++;
                    cost += parseInt(r.getAttribute('data-cost') || '0', 10);
                }
            });
            shown.textContent = count;
            shownCost.textContent = fmt(cost) + '₫';
            empty.hidden = count !== 0;
        }

        [search, from, to].forEach(function (el) { el.addEventListener('input', apply); });
        supplier.addEventListener('change', apply);

        // Mở / đóng chi tiết phiếu
        history.addEventListener('click', function (e) {
            var main = e.target.closest('tr.im-main');
            if (!main) return;
            var rec = main.parentElement;
            var detail = rec.querySelector('tr.im-detail');
            var open = detail.hidden;
            detail.hidden = !open;
            rec.classList.toggle('open', open);
            var tg = main.querySelector('.im-toggle');
            if (tg) tg.setAttribute('aria-expanded', open ? 'true' : 'false');
        });

        // Bấm một nguồn ở bảng tổng hợp -> lọc lịch sử theo nguồn đó
        Array.prototype.forEach.call(document.querySelectorAll('tr.im-sup-row'), function (row) {
            row.addEventListener('click', function () {
                var name = row.getAttribute('data-supplier');
                var found = false;
                Array.prototype.forEach.call(supplier.options, function (o) {
                    if (o.value.toLowerCase() === name.toLowerCase()) { supplier.value = o.value; found = true; }
                });
                if (!found) return;
                apply();
                history.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        });

        apply();
    }

    /* ======================= FORM TẠO PHIẾU ======================= */
    // Mỗi SẢN PHẨM là một nhóm (.im-line): chọn sản phẩm một lần, bấm chọn NHIỀU size cùng lúc,
    // mỗi size có một dòng riêng để nhập số lượng / giá nhập. Server vẫn nhận danh sách phẳng
    // (productId, size, qty, cost) theo từng dòng nên không cần đổi phía Java.
    var linesBox = document.getElementById('imLines');
    if (!linesBox) return;

    var optionsTpl = document.getElementById('imProductOptions');
    var addBtn = document.getElementById('imAddLine');
    var form = document.getElementById('importForm');

    function esc(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    // "Khoá" của một lựa chọn: chỉ size ("M") hoặc "Màu|SIZE" khi sản phẩm có màu ("Olive|M"; không có size: "Olive|")
    function partsOf(key) {
        var i = key.indexOf('|');
        return i < 0 ? { color: '', size: key } : { color: key.slice(0, i), size: key.slice(i + 1) };
    }
    function chipText(key) {
        var p = partsOf(key);
        return p.color ? p.color + (p.size ? ' · ' + p.size : '') : p.size;
    }
    function rowLabel(key) {
        var p = partsOf(key);
        if (p.color) return p.color + (p.size ? ' · Size ' + p.size : '');
        return p.size ? 'Size ' + p.size : 'Số lượng';
    }
    function keyOfRow(r) {
        return r.color ? r.color + '|' + (r.size || '') : (r.size || '');
    }

    function groupHtml() {
        return '' +
            '<div class="im-line-main">' +
            '  <select class="im-prod" aria-label="Sản phẩm"></select>' +
            '  <button type="button" class="im-remove" title="Xoá sản phẩm" aria-label="Xoá sản phẩm"><i class="bi bi-x-lg"></i></button>' +
            '</div>' +
            '<div class="im-sizes" hidden>' +
            '  <span class="im-sizes-label"><span class="im-sizes-title">Size</span> <small>— bấm để chọn nhiều ô</small></span>' +
            '  <div class="im-chips" role="group" aria-label="Chọn size"></div>' +
            '</div>' +
            '<div class="im-sz-rows"></div>' +
            '<div class="im-line-hint"></div>';
    }

    function rowHtml(key, removable) {
        var p = partsOf(key);
        var label = rowLabel(key);
        var hasLabel = !!(p.color || p.size);
        return '' +
            '<input type="hidden" name="productId" class="im-pid">' +
            '<input type="hidden" name="size" value="' + esc(p.size) + '">' +
            '<input type="hidden" name="color" value="' + esc(p.color) + '">' +
            '<span class="im-sz-label">' + esc(label) + '</span>' +
            '<input type="text" name="qty" class="im-qty" inputmode="numeric" maxlength="7" placeholder="0" aria-label="Số lượng' + (hasLabel ? ' ' + esc(label) : '') + '">' +
            '<div class="money-box"><input type="text" name="cost" class="money-input im-cost" inputmode="numeric" maxlength="13" placeholder="0" aria-label="Giá nhập' + (hasLabel ? ' ' + esc(label) : '') + '"><span>₫</span></div>' +
            '<output class="im-total">0₫</output>' +
            (removable
                ? '<button type="button" class="im-sz-remove" title="Bỏ dòng này" aria-label="Bỏ ' + esc(label) + '"><i class="bi bi-x-lg"></i></button>'
                : '<span></span>');
    }

    function selectedOpt(line) {
        var sel = line.querySelector('.im-prod');
        return sel.options[sel.selectedIndex];
    }
    function sizesOf(line) {
        var opt = selectedOpt(line);
        return opt && opt.value ? (opt.getAttribute('data-sizes') || '').split(',').filter(Boolean) : [];
    }
    function syncPid(line) {
        var v = line.querySelector('.im-prod').value;
        Array.prototype.forEach.call(line.querySelectorAll('.im-pid'), function (h) { h.value = v; });
    }

    // Giá gợi ý cho dòng mới: giá của dòng vừa nhập trong cùng sản phẩm, nếu chưa có thì giá nhập hiện tại
    function suggestCost(line) {
        var costs = line.querySelectorAll('.im-cost');
        for (var k = costs.length - 1; k >= 0; k--) {
            if (costs[k].value) return costs[k].value;
        }
        var opt = selectedOpt(line);
        var c = opt ? parseInt(opt.getAttribute('data-cost') || '0', 10) : 0;
        return c > 0 ? fmt(c) : '';
    }

    function addRow(line, size, removable, vals) {
        var box = line.querySelector('.im-sz-rows');
        var row = document.createElement('div');
        row.className = 'im-sz-row';
        row.setAttribute('data-size', size);
        row.innerHTML = rowHtml(size, removable);

        // chèn đúng thứ tự size của sản phẩm (S, M, L, XL...)
        var order = sizesOf(line);
        var idx = order.indexOf(size);
        var before = null;
        if (size && idx > -1) {
            Array.prototype.some.call(box.children, function (r) {
                if (order.indexOf(r.getAttribute('data-size')) > idx) { before = r; return true; }
                return false;
            });
        }
        var cost = (vals && vals.cost) ? fmt(num(vals.cost)) : suggestCost(line);
        box.insertBefore(row, before);
        row.querySelector('.im-cost').value = cost;
        if (vals && vals.qty) row.querySelector('.im-qty').value = vals.qty;
        syncPid(line);
        return row;
    }

    function removeRow(line, size) {
        Array.prototype.forEach.call(line.querySelectorAll('.im-sz-row'), function (r) {
            if (r.getAttribute('data-size') === size) r.remove();
        });
    }
    function hasRow(line, size) {
        return Array.prototype.some.call(line.querySelectorAll('.im-sz-row'), function (r) {
            return r.getAttribute('data-size') === size;
        });
    }

    function refreshChips(line) {
        Array.prototype.forEach.call(line.querySelectorAll('.im-chip[data-size]'), function (c) {
            var on = hasRow(line, c.getAttribute('data-size'));
            c.classList.toggle('on', on);
            c.setAttribute('aria-pressed', on ? 'true' : 'false');
        });
        var all = line.querySelector('.im-chip-all');
        if (all) {
            var sizes = sizesOf(line);
            var allOn = sizes.length > 0 && sizes.every(function (s) { return hasRow(line, s); });
            all.textContent = allOn ? 'Bỏ chọn hết' : 'Chọn tất cả';
            all.setAttribute('data-on', allOn ? '1' : '0');
        }
    }

    function addGroup(data) {
        var line = document.createElement('div');
        line.className = 'im-line';
        line.innerHTML = groupHtml();
        line.querySelector('.im-prod').appendChild(optionsTpl.content.cloneNode(true));
        linesBox.appendChild(line);
        return line;
    }

    // Đổi sản phẩm: dựng lại các nút size / dòng nhập, gợi ý tồn kho và giá nhập hiện tại
    function syncProduct(line, prefill) {
        var opt = selectedOpt(line);
        var sizesBox = line.querySelector('.im-sizes');
        var chips = line.querySelector('.im-chips');
        var rows = line.querySelector('.im-sz-rows');
        var hint = line.querySelector('.im-line-hint');
        rows.innerHTML = '';
        chips.innerHTML = '';

        if (!opt || !opt.value) {
            sizesBox.hidden = true;
            hint.textContent = '';
            return;
        }
        var sizes = sizesOf(line);
        if (sizes.length) {
            sizesBox.hidden = false;
            var ttl = line.querySelector('.im-sizes-title');
            if (ttl) ttl.textContent = sizes.some(function (v) { return v.indexOf('|') >= 0; }) ? 'Màu / size' : 'Size';
            sizes.forEach(function (s) {
                var b = document.createElement('button');
                b.type = 'button';
                b.className = 'im-chip';
                b.setAttribute('data-size', s);
                b.setAttribute('aria-pressed', 'false');
                b.textContent = chipText(s);
                chips.appendChild(b);
            });
            var all = document.createElement('button');
            all.type = 'button';
            all.className = 'im-chip im-chip-all';
            all.textContent = 'Chọn tất cả';
            chips.appendChild(all);
        } else {
            sizesBox.hidden = true;
            addRow(line, '', false, null);   // sản phẩm không chia size: một dòng duy nhất
        }
        var stock = opt.getAttribute('data-stock') || '0';
        var cost = parseInt(opt.getAttribute('data-cost') || '0', 10);
        hint.textContent = 'Đang tồn ' + stock + ' · giá nhập hiện tại ' + (cost > 0 ? fmt(cost) + '₫' : 'chưa có');
        syncPid(line);
        refreshChips(line);
    }

    function recalcRow(row) {
        var t = num(row.querySelector('.im-qty').value) * num(row.querySelector('.im-cost').value);
        row.querySelector('.im-total').textContent = fmt(t) + '₫';
        return t;
    }

    function recalcAll() {
        var qty = 0, cost = 0, used = 0;
        Array.prototype.forEach.call(linesBox.querySelectorAll('.im-sz-row'), function (r) {
            qty += num(r.querySelector('.im-qty').value);
            cost += recalcRow(r);
            used++;
        });
        document.getElementById('imSumLines').textContent = used;
        document.getElementById('imSumQty').textContent = fmt(qty);
        document.getElementById('imSumCost').textContent = fmt(cost) + '₫';
    }

    linesBox.addEventListener('change', function (e) {
        var line = e.target.closest('.im-line');
        if (!line) return;
        if (e.target.classList.contains('im-prod')) syncProduct(line, true);
        recalcAll();
    });

    linesBox.addEventListener('input', function (e) {
        var t = e.target;
        if (t.classList.contains('im-qty')) {
            t.value = digits(t.value);
        } else if (t.classList.contains('im-cost')) {
            var d = digits(t.value);
            t.value = d ? fmt(parseInt(d, 10)) : '';
        } else {
            return;
        }
        recalcAll();
    });

    linesBox.addEventListener('click', function (e) {
        var line = e.target.closest('.im-line');
        if (!line) return;

        // Bấm nút size: bật / tắt, có thể chọn nhiều size
        var chip = e.target.closest('.im-chip');
        if (chip) {
            if (chip.classList.contains('im-chip-all')) {
                var sizes = sizesOf(line);
                if (chip.getAttribute('data-on') === '1') {
                    sizes.forEach(function (s) { removeRow(line, s); });
                } else {
                    sizes.forEach(function (s) { if (!hasRow(line, s)) addRow(line, s, true, null); });
                }
            } else {
                var size = chip.getAttribute('data-size');
                if (hasRow(line, size)) removeRow(line, size);
                else addRow(line, size, true, null);
            }
            refreshChips(line);
            recalcAll();
            return;
        }

        // Bỏ một size
        var szRm = e.target.closest('.im-sz-remove');
        if (szRm) {
            removeRow(line, szRm.closest('.im-sz-row').getAttribute('data-size'));
            refreshChips(line);
            recalcAll();
            return;
        }

        // Xoá cả sản phẩm
        var rm = e.target.closest('.im-remove');
        if (rm) {
            if (linesBox.querySelectorAll('.im-line').length === 1) {
                // sản phẩm cuối cùng: làm trống thay vì xoá
                line.querySelector('.im-prod').value = '';
                syncProduct(line, false);
            } else {
                line.remove();
            }
            recalcAll();
        }
    });

    // Enter trong ô nhập không được gửi form nhầm (phiếu đã lưu thì không sửa được)
    form.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && e.target.tagName === 'INPUT' && e.target.type !== 'submit') {
            e.preventDefault();
        }
    });

    addBtn.addEventListener('click', function () {
        var line = addGroup();
        syncProduct(line, false);
        line.querySelector('.im-prod').focus();
    });

    /* ======================= POPUP: TẠO NHANH SẢN PHẨM MỚI =======================
       Dùng khi nhập hàng mới từ nguồn mà sản phẩm chưa có trong kho: tạo ngay tại đây, không phải rời trang.
       Server tạo sản phẩm ở trạng thái "chờ nhập hàng" (POST /dashboard/kho-hang/them-nhanh, trả JSON); sản phẩm sẽ
       lên kệ khi phiếu nhập đang soạn được lưu. Sản phẩm vừa tạo được chọn sẵn ở một dòng của phiếu. */
    (function () {
        var dlg = document.getElementById('imNewDialog');
        var openBtn = document.getElementById('imNewProduct');
        if (!dlg || !openBtn) return;

        var nvForm = document.getElementById('imNewForm');
        var errBox = document.getElementById('imNewError');
        var saveBtn = document.getElementById('imNewSave');
        var colorBox = document.getElementById('nvColorRows');
        var colorsInput = document.getElementById('nvColors');
        var addColorBtn = document.getElementById('nvAddColor');
        var priceInput = document.getElementById('nvPrice');
        var MAXC = 12;
        var busy = false;

        function showError(msg) {
            errBox.textContent = msg || '';
            errBox.hidden = !msg;
        }

        // --- mở / đóng ---
        function openDialog() {
            nvForm.reset();
            colorBox.innerHTML = '';
            syncColors();
            showError('');
            if (typeof dlg.showModal === 'function') dlg.showModal(); else dlg.setAttribute('open', '');
            document.getElementById('nvName').focus();
        }
        function closeDialog() {
            if (typeof dlg.close === 'function') dlg.close(); else dlg.removeAttribute('open');
        }
        openBtn.addEventListener('click', openDialog);
        document.getElementById('imNewClose').addEventListener('click', closeDialog);
        document.getElementById('imNewCancel').addEventListener('click', closeDialog);
        dlg.addEventListener('click', function (e) { if (e.target === dlg) closeDialog(); });   // bấm ra ngoài hộp = đóng

        // --- ô giá: hiện dấu chấm ngăn cách ---
        priceInput.addEventListener('input', function () {
            var d = digits(priceInput.value);
            priceInput.value = d ? fmt(parseInt(d, 10)) : '';
        });

        // --- màu: mỗi dòng = ô chọn màu + mã hex + tên  ->  ghi vào ô ẩn "colors" dạng Tên:#hex ---
        function normHex(v) {
            v = (v || '').trim();
            if (v.charAt(0) !== '#') v = '#' + v;
            if (/^#[0-9a-f]{3}$/i.test(v)) v = '#' + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2) + v.charAt(3) + v.charAt(3);
            return /^#[0-9a-f]{6}$/i.test(v) ? v.toLowerCase() : null;
        }
        function cleanName(v) { return String(v || '').replace(/[,:|]/g, ' ').trim(); }

        function syncColors() {
            var tokens = [];
            Array.prototype.forEach.call(colorBox.querySelectorAll('.color-row'), function (row) {
                var name = cleanName(row.querySelector('.cr-name').value);
                if (!name) return;
                var hex = normHex(row.querySelector('.cr-hex').value) || row.querySelector('.cr-color').value;
                tokens.push(name + ':' + hex);
            });
            colorsInput.value = tokens.join(',');
            addColorBtn.style.display = colorBox.children.length >= MAXC ? 'none' : '';
        }

        function addColorRow() {
            if (colorBox.children.length >= MAXC) return;
            var row = document.createElement('div');
            row.className = 'color-row nv-color-row';
            row.innerHTML =
                '<input type="color" class="cr-color" value="#111111" aria-label="Chọn màu">' +
                '<input type="text" class="cr-hex" maxlength="7" placeholder="#RRGGBB" spellcheck="false" aria-label="Mã màu hex" value="#111111">' +
                '<input type="text" class="cr-name" maxlength="20" placeholder="Tên màu (VD: Đen)" aria-label="Tên màu">' +
                '<button type="button" class="cr-del" aria-label="Xoá màu">&times;</button>';
            var picker = row.querySelector('.cr-color');
            var hexIn = row.querySelector('.cr-hex');
            picker.addEventListener('input', function () { hexIn.value = picker.value; hexIn.classList.remove('invalid'); });
            hexIn.addEventListener('input', function () {
                var n = normHex(hexIn.value);
                if (n) { picker.value = n; hexIn.classList.remove('invalid'); }
                else hexIn.classList.toggle('invalid', hexIn.value.trim() !== '');
            });
            hexIn.addEventListener('blur', function () {
                var n = normHex(hexIn.value);
                hexIn.value = n || picker.value;
                hexIn.classList.remove('invalid');
                syncColors();
            });
            row.querySelector('.cr-name').addEventListener('input', function () { this.style.borderColor = ''; });
            row.addEventListener('input', syncColors);
            row.addEventListener('change', syncColors);
            row.querySelector('.cr-del').addEventListener('click', function () { row.remove(); syncColors(); });
            colorBox.appendChild(row);
            syncColors();
            row.querySelector('.cr-name').focus();
        }
        addColorBtn.addEventListener('click', addColorRow);

        // --- đưa sản phẩm vừa tạo vào ô chọn sản phẩm ---
        function makeOption(prod) {
            var o = document.createElement('option');
            o.value = String(prod.id);
            o.textContent = prod.label;
            o.setAttribute('data-sizes', prod.sizes || '');
            o.setAttribute('data-stock', '0');
            o.setAttribute('data-cost', '0');
            return o;
        }
        function useInForm(prod) {
            // 1) mọi ô chọn đang có + mẫu cho các dòng thêm sau đều biết sản phẩm mới
            optionsTpl.content.appendChild(makeOption(prod));
            Array.prototype.forEach.call(linesBox.querySelectorAll('.im-prod'), function (sel) { sel.appendChild(makeOption(prod)); });
            // 2) chọn nó ở một dòng trống (nếu có), không thì mở thêm một dòng
            var target = null;
            Array.prototype.forEach.call(linesBox.querySelectorAll('.im-line'), function (g) {
                if (!g.querySelector('.im-prod').value) target = g;
            });
            if (!target) target = addGroup();
            target.querySelector('.im-prod').value = String(prod.id);
            syncProduct(target, false);
            recalcAll();
            var first = target.querySelector('.im-chip') || target.querySelector('.im-qty');
            if (first) first.focus();
            try { target.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch (e) { /* trình duyệt cũ */ }
        }

        // --- gửi ---
        nvForm.addEventListener('submit', function (e) {
            e.preventDefault();
            if (busy) return;

            var badName = null;
            Array.prototype.forEach.call(colorBox.querySelectorAll('.cr-name'), function (el) {
                var empty = !cleanName(el.value);
                el.style.borderColor = empty ? '#c0392b' : '';
                if (empty && !badName) badName = el;
            });
            if (badName) {
                badName.focus();
                showError('Có dòng màu chưa đặt tên. Hãy nhập tên màu (VD: Đen) hoặc bấm × để xoá dòng đó.');
                return;
            }
            syncColors();
            showError('');

            busy = true;
            saveBtn.disabled = true;
            saveBtn.setAttribute('aria-busy', 'true');
            fetch(nvForm.getAttribute('action'), {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
                body: new URLSearchParams(new FormData(nvForm)).toString()
            }).then(function (res) {
                return res.json().catch(function () { return null; }).then(function (data) { return { ok: res.ok, data: data }; });
            }).then(function (r) {
                if (r.ok && r.data && r.data.ok) {
                    closeDialog();
                    useInForm(r.data);
                    if (window.KosisDialog) window.KosisDialog.alert('Đã tạo sản phẩm "' + r.data.name + '". Nhập số lượng và giá nhập rồi lưu phiếu để sản phẩm lên kệ.', { type: 'success' });
                } else {
                    showError((r.data && r.data.error) || 'Không tạo được sản phẩm. Phiên đăng nhập có thể đã hết hạn, hãy tải lại trang rồi thử lại.');
                }
            }).catch(function () {
                showError('Không kết nối được máy chủ. Hãy kiểm tra mạng rồi thử lại.');
            }).then(function () {
                busy = false;
                saveBtn.disabled = false;
                saveBtn.removeAttribute('aria-busy');
            });
        });
    })();

    function warn(msg) {
        if (window.KosisDialog) window.KosisDialog.alert(msg, { type: 'error' });
        else alert(msg);
    }

    var submitting = false;
    form.addEventListener('submit', function (e) {
        if (submitting) { e.preventDefault(); return; }
        var groups = linesBox.querySelectorAll('.im-line');
        var anyProduct = false, problem = null, focusEl = null;
        Array.prototype.forEach.call(groups, function (g) {
            var sel = g.querySelector('.im-prod');
            if (!sel.value) return;
            anyProduct = true;
            if (!problem && sizesOf(g).length && !g.querySelector('.im-sz-row')) {
                problem = 'Vui lòng chọn ít nhất một màu / size cho "' + sel.options[sel.selectedIndex].text + '".';
                focusEl = g.querySelector('.im-chip');
            }
        });
        if (!anyProduct) {
            problem = 'Vui lòng chọn ít nhất một sản phẩm.';
            focusEl = linesBox.querySelector('.im-prod');
        }
        if (problem) {
            e.preventDefault();
            if (focusEl) focusEl.focus();
            warn(problem);
            return;
        }
        // Khoá nút để bấm đúp không tạo 2 phiếu (server cũng chặn bằng mã dùng một lần)
        submitting = true;
        var btn = form.querySelector('.im-submit');
        if (btn) { btn.disabled = true; btn.setAttribute('aria-busy', 'true'); }
    });
    // Quay lại bằng nút Back (bfcache): mở khoá lại
    window.addEventListener('pageshow', function () {
        submitting = false;
        var btn = form.querySelector('.im-submit');
        if (btn) { btn.disabled = false; btn.removeAttribute('aria-busy'); }
    });

    // Dựng lại form (lần đầu: 1 sản phẩm trống; sau khi server báo lỗi: gộp các dòng cùng sản phẩm lại thành một nhóm)
    var rows = [];
    try { rows = JSON.parse(window.IM_ROWS || '[]'); } catch (err) { rows = []; }
    if (!rows.length) {
        syncProduct(addGroup(), false);
    } else {
        var groupsData = [];
        rows.forEach(function (r) {
            var last = groupsData[groupsData.length - 1];
            if (r.productId && last && last.productId === r.productId) last.rows.push(r);
            else groupsData.push({ productId: r.productId || '', rows: [r] });
        });
        groupsData.forEach(function (gd) {
            var line = addGroup();
            line.querySelector('.im-prod').value = gd.productId;
            syncProduct(line, false);
            var hasSizes = sizesOf(line).length > 0;
            gd.rows.forEach(function (r) {
                if (hasSizes) {
                    var k = keyOfRow(r);
                    if (k && !hasRow(line, k)) addRow(line, k, true, r);
                } else {
                    var row = line.querySelector('.im-sz-row') || addRow(line, '', false, null);
                    if (r.qty) row.querySelector('.im-qty').value = r.qty;
                    if (r.cost) row.querySelector('.im-cost').value = fmt(num(r.cost));
                }
            });
            refreshChips(line);
        });
    }
    recalcAll();
})();