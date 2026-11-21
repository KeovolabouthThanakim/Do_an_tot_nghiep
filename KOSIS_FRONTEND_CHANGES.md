# KOSIS frontend redesign – what changed

**Languages: the storefront is available in Vietnamese (default) and English** – see "Multi-language (vi / en)" at the bottom. Categories and seeded products are still stored in Vietnamese.
Categories are stored in Vietnamese: Áo khoác, Áo, Quần, Giày, Túi, Phụ kiện (use these values when adding products in the dashboard so filters work).
If you already started the previous English version, the seeder replaces the English KS products once automatically.

## How to run
1. Start MySQL, check `application.properties` (DB user/password).
2. `mvn spring-boot:run` (or run from your IDE).
3. First start seeds 32 KOSIS fashion products (codes `KS...`) and removes the old GrandSport demo
   products (`GS...`). Set `kosis.seed.replace-legacy-demo=false` to keep them.
   Hibernate adds the new columns `products.gender`, `products.colors` and the table `newsletter_subscribers` automatically.

## New / changed files
**Templates** `templates/fragments/layout.html` (head, header, footer, newsletter, product card, scripts),
`home/index.html` + `home/mosaic.html`, `products/index.html` (listing: all/men/women/kids/sale/gifts/search),
`products/detail.html`, `cart/index.html`, `checkout/index.html`, `checkout/success.html`, `orders/index.html`,
`auth/login.html`, `auth/register.html`, `pages/{wishlist,account,about,contact,info}.html`.
**CSS** `static/css/kosis/{base,home,shop,checkout,pages}.css` · **JS** `static/js/main.js`.
**Java (additive)** `ShopController` (new pages), `HomeController`/`ProductController` (rewritten for the new data),
`Product` (+gender, colors), `NewsletterSubscriber(+Repository)`, `DataSeeder`, small `SecurityConfig` change (public routes, `/account` needs login).
Cart, checkout, orders, login/register processing, admin dashboard and all their routes are untouched
(form field names, URLs and the QR/receipt JS were preserved).

## Routes
`/` `/men` `/women` `/kids` `/collections` `/sale` `/gifts` `/search?q=` `/wishlist` `/account` `/about` `/contact`
`/shipping` `/returns` `/faq` `/privacy` `/terms` `/our-story` `/san-pham/{id}` `/gio-hang` `/thanh-toan` `/don-hang-cua-toi` `/login` `/register`

## Known limits (please read)
* Source photos are low resolution – see `static/images/README.md`.
* Colour choice is now saved in the cart (key `id|size|colour`) and on each order item (`order_items.color`); the colour image is used in the cart and on the order.
* Wishlist is stored in the visitor's browser (localStorage), not in the database.
* Prices are in VND (₫) because checkout uses VietQR bank transfer.
* Routes such as /men, /women, /kids stay English; only the text is Vietnamese.
* Kids products reuse adult product photos (no kids product images were supplied).
* ZARA / adidas wordmarks are typographic placeholders – use licensed logos.
* Legal pages (privacy / terms) are draft text; contact e-mail/phone are placeholders.
* Written without being able to compile with Maven in my environment: templates were checked by rendering them
  with a small test renderer in Chrome; please run the app once and report any Java/Thymeleaf error.

## Update: dashboard + notifications
* `KosisDialog.alert()` and all `data-flash` messages are now **toast notifications**: they pop in at the top, stay 2 seconds and disappear by themselves (no OK button). `KosisDialog.confirm()` / `data-confirm` is still a dialog because it needs an answer.
* Dashboard overview (`/dashboard`) now shows **real data**: revenue today (confirmed orders), orders today, products, customers, and the 5 latest orders (with links to the order detail). Trends compare with yesterday.
* New repository methods: `OrderRepository.findTop5ByOrderByCreatedAtDesc / countByCreatedAt… / sumRevenue`, `UserRepository.countByRole`.

## Update: pagination
* Listing pages (`/collections`, `/men`, `/women`, `/kids`, `/sale`, `/gifts`, `/search`) now filter, sort and paginate **in the database** (`ProductSpecs` + `ProductListingService`, `ProductRepository` extends `JpaSpecificationExecutor`) instead of `findAll()` + in-memory filtering.
* URL parameter `?page=N` (1-based; out-of-range values are clamped). Page size: `kosis.listing.page-size` (default 20 - chia hết cho 5/4/2 cột của lưới). Changing category / sort / gender / search resets to page 1.
* Not paginated on purpose: home page sections, "related products" on the detail page, wishlist (still `findAll()`).

## Update: dashboard redesign – Kho hàng / Trả hàng / Khách hàng
* The three pages now use the same design language as Tổng quan / Đơn hàng / Giao hàng (`x-*` classes in `css/dashboard/pages.css`).
* **Kho hàng** (`kho-hang.html`): stock-health bar (còn / sắp hết / hết), total units + stock value at import price, "cần nhập thêm" alert with a one-click filter, per-size stock chips, margin % under import price, sort (stock / price), filter by category / gender.
* **Trả hàng** (`tra-hang.html`): summary bar + processing flow filter (chờ duyệt → chờ nhận hàng → đã hoàn tiền / từ chối), one card per request with a 3-step tracker, customer reason / store note quotes, action forms folded into exclusive `<details>` (same URLs and field names; `data-confirm` dialogs unchanged). Open requests are listed first.
* **Khách hàng** (`khach-hang.html`): summary bar + filters (hoạt động / đã khoá / từng không nhận hàng), sort by newest / spend / orders, contact icons, spend comparison bar, locked accounts dimmed.
* New model attributes (no logic change): `InventoryController` (`totalUnits`, `stockCostValue`, `lowStock`), `ReturnAdminController` (`requestedCount`, `approvedCount`, `refundedCount`, `rejectedCount`, `refundedTotal`), `CustomerAdminController` (`maxSpent`, `totalRevenue`).
* Fixes found while testing: (1) `dashboard-inventory.js` threw an error on the product list because the photo popup and the product form both use `id="gallery"`; (2) on screens ≤ 960px the dashboard sidebar was ~1900px wide (`align-self: flex-start` was never reset), making every dashboard page scroll sideways.

## Update: dashboard redesign – Chi tiết đơn hàng (`don-hang-chi-tiet.html`)
* Progress tracker for the whole order (đặt hàng → xác nhận → chuẩn bị → vận chuyển → đã giao) with timestamps; rejected / failed delivery show in red at the step where it stopped.
* Product card now breaks the total down: tạm tính, mã giảm giá (with voucher code), phí vận chuyển, tổng cần thu (before, only the total was shown, e.g. 2.000₫ item vs 32.000₫ total was confusing). COD / "đã chuyển khoản" notice kept.
* "Cần xử lý" card (xác nhận / từ chối) is first in the right column while the order is waiting; reject form is folded in `<details>` (same URLs, `reason` field, `data-confirm` dialogs). After review it becomes "Kết quả kiểm tra".
* Giao tới: tap-to-call, copy phone, open address in Google Maps, customer note as a quote. Customer card with avatar, status, contact and profile link.
* Invoice (`#hoa-don`) is no longer an inline block that repeats the page: the "Xem / in hóa đơn" button opens it as a popup (Esc / backdrop / X to close; `…/don-hang/{id}#hoa-don` still opens it). Shown only for confirmed orders (invoice is only issued when confirmed, see `Order.isInvoiceIssued`). Invoice shows invoice date; printing gives one clean A4 page (other page parts hidden with `display:none`, 14mm margins) instead of leaving a blank 2nd page.
* No controller change needed.

## Update: dashboard "Liên hệ" and "Đánh giá" redesign
* New shared stylesheet `static/css/dashboard/inbox.css` (classes prefixed `ib-`), linked from `dashboard/lien-he.html` and `dashboard/danh-gia.html`.
* Liên hệ: inbox-style message cards (avatar, relative time, expandable text, "Trả lời qua email" with a prefilled `Re: …` subject, copy email, mark handled), tabs with counts, search, sort, "load more".
* Đánh giá: rating summary (average + 5→1 distribution that doubles as a star filter), review cards with product thumbnail, low-rating flag, star filter, sort, "load more".
* `ReviewAdminController` now also provides `productImages`, `avgRating`, `avgRounded`, `ratingCounts`, `starLevels` (average/distribution count visible reviews only).

## Update: dashboard "Mã giảm giá" và "Nhân viên" redesign
* **Mã giảm giá** (`voucher.html`): thanh tổng hợp (dùng được / hết hạn-hết lượt / đã tắt + tổng lượt dùng), thẻ lọc, tìm kiếm, sắp xếp; mỗi mã là một "vé" có nút sao chép mã, chip điều kiện (đơn tối thiểu, giảm tối đa, hạn dùng), thanh lượt dùng, bật/tắt, xoá. Tạo mã trong popup có **xem trước vé** và **máy tính thử đơn** (cùng công thức `Voucher.discountFor`), chọn loại giảm dạng nút, nút ngày nhanh (+7/+30/+90 ngày). Field name và URL giữ nguyên.
* `VoucherAdminController.list` thêm model: `totalCount`, `usableCount`, `offCount`, `endedCount`, `totalUsed`.
* **Nhân viên** (`nhan-vien.html`): thẻ lọc (tất cả / ADMIN / NHANVIEN / đã khoá) + tìm kiếm, mỗi tài khoản là một thẻ (avatar, vai trò, liên hệ, trạng thái, sửa/khoá/mở khoá/xoá). Không đổi controller.
* **Thêm / sửa nhân viên** (`nhan-vien-form.html`): form 3 bước (Đăng nhập → Hồ sơ → Vai trò), chọn vai trò bằng thẻ có liệt kê quyền, nút hiện/ẩn + tạo mật khẩu ngẫu nhiên + thanh độ mạnh, xem trước ảnh đại diện, thẻ xem trước nhân viên cập nhật trực tiếp, "Cách đăng nhập", đặt lại mật khẩu. Field name giữ nguyên.
* CSS mới ở cuối `css/dashboard/pages.css` (prefix `vc-`, `sf-`, `sfm-`); ba trang giờ cũng link `pages.css`.
* Chưa chạy được Maven trong môi trường làm việc: giao diện đã kiểm tra bằng bản render mô phỏng trong Chromium (1440px và 390px, không lỗi JS); hãy chạy app một lần và báo lỗi Thymeleaf nếu có.

## Update: dashboard "Liên hệ" redesign (v2)
* `dashboard/lien-he.html` làm lại theo kiểu hàng dài (cùng ngôn ngữ với Khách hàng / Nhân viên / Mã giảm giá): thanh tổng hợp đã xử lý, thẻ lọc, tìm kiếm, sắp xếp, mỗi tin là một hàng (avatar, tên + email, tiêu đề + đoạn xem trước, trạng thái, thời gian). Bấm hàng để mở nội dung đầy đủ + trả lời qua email / sao chép email / đánh dấu đã xử lý. Tin chưa xử lý có vạch vàng bên trái. Không đổi controller, URL hay tên field.
* CSS mới ở cuối `pages.css` (prefix `ct-`); `inbox.css` vẫn dùng cho trang Đánh giá.

## Update: thanh tổng hợp (`x-summary`) cho Giao hàng, Đánh giá, Nhân viên
* Thêm thanh tổng hợp giống Kho hàng / Đơn hàng / Khách hàng: **Giao hàng** (tỷ lệ giao thành công + 5 trạng thái), **Đánh giá** (đang hiện / đã ẩn + điểm trung bình), **Nhân viên** (ADMIN / NHANVIEN + số bị khoá). `danh-gia.html` giờ cũng link `pages.css`. Không đổi controller.

## Update: sidebar Editorial
* `dashboard/sidebar.html` + khối sidebar trong `css/dashboard/style.css`: nền kem, chữ serif (Playfair), icon giữ như cũ, menu chia 4 nhóm (Bán hàng / Sản phẩm / Khách hàng / Quản trị), mục đang chọn in nghiêng có vạch vàng bên trái, badge viền vàng. Chân sidebar: "Xem cửa hàng" + thẻ người dùng (chữ cái đầu, tên, vai trò, nút đăng xuất dạng icon). Tên fragment và tham số không đổi nên mọi trang dùng chung; mobile vẫn thu thành thanh cuộn ngang ở trên. Class `.sidebar-profile` không còn dùng.


## Multi-language (vi / en)
* **Switch**: `EN`/`VI` button in the header and a language switch in the footer. Any page accepts `?lang=en` / `?lang=vi`; the choice is kept in the cookie `kosis-lang` (1 year). Default = Vietnamese.
* **Where the texts live**: `src/main/resources/messages.properties` (Vietnamese) and `messages_en.properties` (English). Same keys in both files (`MessagesBundleTest` fails if one is missing). Messages use `MessageFormat`: parameters `{0}`, `{1}`, a literal apostrophe is written `''`.
* **Templates**: storefront templates use `#{key}` / `[[#{key}]]`. Page `<html lang>` follows the language. Prices use `1.290.000 ₫` (vi) or `1,290,000 ₫` (en) through the model attribute `numSep`.
* **Java messages** (flash messages, validation errors, order/return status labels, info pages, some e-mails): `I18n.t("key", "Vietnamese default", args...)` (`config/I18n.java`). Without a request (scheduler, unit tests) it returns the Vietnamese default.
* **JS**: `main.js` / `kosis-dialog.js` read `window.KOSIS_I18N` (keys `js.*`, injected in `fragments/layout.html`); checkout page strings come from `CO_I18N` in `checkout/index.html`.
* **Add another language**: copy `messages_en.properties` to `messages_xx.properties`, translate, and allow `xx` in `KosisLocaleResolver` + add a link next to the EN/VI ones.
* **Not translated (still Vietnamese)**: the whole `/dashboard/**` area (forced to Vietnamese on purpose), product names/descriptions/categories/colours coming from the database, e-mails sent when staff change an order (order confirmed/shipped...), carrier names, legal pages are translated but are drafts like the Vietnamese ones.
* Written without being able to compile with Maven here: please run `mvn test` and click through the site once in both languages.
