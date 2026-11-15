package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entity ánh xạ với bảng "products" trong SQL database.
 * Đây là nguồn dữ liệu DUY NHẤT cho sản phẩm hiển thị ở:
 *   - Trang chủ (mục "Sản phẩm nổi bật" / "Khuyến mãi" -> lọc theo section)
 *   - Trang danh sách sản phẩm /san-pham (tất cả sản phẩm section = JERSEY)
 *   - Trang quản trị /dashboard/kho-hang (quản lý toàn bộ, thêm/sửa/xoá)
 */
@Entity
@Table(name = "products")
public class Product {

    /**
     * Danh sách loại sản phẩm MẶC ĐỊNH: chỉ dùng để khởi tạo bảng danh mục ở lần chạy đầu và làm dự phòng.
     * Danh mục thực tế do ADMIN quản lý ở /dashboard/cai-dat/menu - xem CategoryService.names().
     */
    public static final List<String> CATEGORIES =
            List.of("Áo khoác", "Áo", "Áo thể thao", "Quần", "Giày", "Túi", "Phụ kiện");

    /** Đối tượng sử dụng: MEN / WOMEN / KIDS / UNISEX. */
    public static final List<String> GENDERS = List.of("MEN", "WOMEN", "KIDS", "UNISEX");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    /**
     * Đường dẫn ảnh sản phẩm - có thể là:
     *   - "/uploads/products/xxx.jpg" (ảnh admin upload qua trang quản trị)
     *   - hoặc URL ảnh ngoài (nếu admin dán link ảnh có sẵn)
     */
    @Column(length = 500)
    private String image;

    /** Ảnh phụ thứ 2 và thứ 3 (sản phẩm có tối đa 3 ảnh: image, image2, image3) */
    @Column(length = 500)
    private String image2;

    @Column(length = 500)
    private String image3;

    /**
     * Toàn bộ ảnh sản phẩm, mỗi ảnh một dòng (KHÔNG giới hạn 3 ảnh nữa).
     * Sản phẩm cũ chưa có giá trị này thì dùng image / image2 / image3 như trước.
     * image, image2, image3 vẫn được đồng bộ (3 ảnh đầu) để các chỗ cũ không bị hỏng.
     */
    @Column(name = "gallery", columnDefinition = "TEXT")
    private String gallery;

    @Column(nullable = false)
    private long price;

    /** Giá nhập hàng (giá vốn) - chỉ dùng trong trang quản trị, không hiển thị ra cửa hàng */
    @Column(name = "import_price")
    private Long importPrice = 0L;

    /** Giá niêm yết trước khi giảm (tự tính từ giá niêm yết khi có giảm giá) */
    @Column(name = "old_price")
    private long oldPrice;

    @Column(name = "discount_percent")
    private int discountPercent;

    @Column(name = "is_new")
    private boolean isNew;

    private double rating = 5.0;

    /** Số lượng tồn kho */
    @Column(nullable = false)
    private int stock = 0;

    /** Tên giải đấu / danh mục hiển thị (VD: "Ngoại Hạng Anh") */
    @Column(length = 100)
    private String category;

    /**
     * Khu vực hiển thị trên trang chủ: FEATURED (nổi bật), PROMOTION (khuyến mãi).
     * Sản phẩm ở trang /san-pham không cần section (để trống hoặc JERSEY).
     */
    @Column(length = 20)
    private String section;

    @Column(length = 1000)
    private String description;

    /**
     * Các size bán, cách nhau bằng dấu phẩy (VD: "S,M,L,XL"). Để trống = tự chọn theo danh mục
     * (xem getSizeList). Nhập "-" nếu sản phẩm không có size (phụ kiện...).
     */
    @Column(length = 100)
    private String sizes;

    /**
     * Tồn kho TỪNG SIZE, dạng "S:10,M:5,L:0". Khi có dữ liệu này thì `stock` = tổng các size.
     * Sản phẩm CÓ MÀU thì tồn kho chia theo (màu, size), dạng "Olive|S:10,Olive|M:5,Black|S:3,Black|M:0"
     * (sản phẩm không có size: "Olive|:10,Black|:3"); `stock` = tổng tất cả.
     * Để trống (sản phẩm cũ) = chưa chia theo size/màu, mọi size và màu dùng chung `stock` như trước.
     */
    @Column(name = "size_stock", length = 3000)
    private String sizeStock;

    /** KOSIS storefront: MEN, WOMEN, KIDS or UNISEX (null for products created before the redesign). */
    @Column(length = 10)
    private String gender;

    /**
     * KOSIS storefront: colour options separated by commas. Mỗi màu có dạng "Tên:#hex" hoặc "Tên:#hex:N",
     * N (1-3) là số thứ tự ảnh sản phẩm sẽ hiện ra khi khách bấm chọn màu đó,
     * e.g. "Olive:#6b7a46:1,Black:#111111:2".
     */
    @Column(length = 500)
    private String colors;

    /**
     * "Chờ nhập hàng": sản phẩm mới thêm ở Kho hàng, chưa có phiếu nhập nào -> chưa được bán ngoài cửa hàng.
     * Phiếu nhập đầu tiên sẽ tự chuyển sang false (lên kệ). Dùng Boolean (không phải boolean) để các dòng cũ
     * trong DB có giá trị NULL vẫn được hiểu là "đang bán" -> sản phẩm đang bán không bị ảnh hưởng.
     */
    @Column(name = "pending_import")
    private Boolean pendingImport;

    public Product() {
    }

    // ===== Getter / Setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getImage2() { return image2; }
    public void setImage2(String image2) { this.image2 = image2; }

    public String getImage3() { return image3; }
    public void setImage3(String image3) { this.image3 = image3; }

    /** Danh sách các ảnh đang có (bỏ qua ô trống), tối đa 3 ảnh - dùng cho giao diện quản trị */
    @Transient
    public List<String> getImages() {
        List<String> list = new ArrayList<>();
        if (gallery != null && !gallery.isBlank()) {
            for (String s : gallery.split("\n")) {
                if (!s.isBlank()) list.add(s.trim());
            }
            return list;
        }
        for (String s : new String[]{image, image2, image3}) {
            if (s != null && !s.isBlank()) list.add(s);
        }
        return list;
    }

    /** Ghi lại toàn bộ danh sách ảnh (theo thứ tự, ảnh đầu tiên là ảnh chính). Không giới hạn số lượng. */
    public void replaceImages(List<String> urls) {
        List<String> clean = new ArrayList<>();
        if (urls != null) {
            for (String s : urls) {
                if (s != null && !s.isBlank()) clean.add(s.trim());
            }
        }
        this.gallery = clean.isEmpty() ? null : String.join("\n", clean);
        this.image = clean.size() > 0 ? clean.get(0) : null;
        this.image2 = clean.size() > 1 ? clean.get(1) : null;
        this.image3 = clean.size() > 2 ? clean.get(2) : null;
    }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    /** Hàng cũ trong DB có thể chưa có giá nhập (NULL) -> coi như 0 */
    public long getImportPrice() { return importPrice == null ? 0L : importPrice; }
    public void setImportPrice(long importPrice) { this.importPrice = importPrice; }

    public long getOldPrice() { return oldPrice; }
    public void setOldPrice(long oldPrice) { this.oldPrice = oldPrice; }

    public int getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(int discountPercent) { this.discountPercent = discountPercent; }

    /**
     * % giảm dùng để HIỂN THỊ: ưu tiên giá trị đã lưu; nếu bằng 0 nhưng giá cũ > giá bán
     * (dữ liệu cũ / nhập tay) thì tự tính lại từ 2 giá để ô giảm giá luôn hiện đúng.
     */
    @Transient
    public int getDisplayDiscount() {
        if (discountPercent > 0) return discountPercent;
        if (oldPrice > price && oldPrice > 0) {
            return (int) Math.round((oldPrice - price) * 100.0 / oldPrice);
        }
        return 0;
    }

    /** Số tiền tiết kiệm được (giá cũ - giá bán), 0 nếu không giảm */
    @Transient
    public long getSavedAmount() {
        return oldPrice > price ? oldPrice - price : 0;
    }

    public String getSizes() { return sizes; }
    public void setSizes(String sizes) { this.sizes = sizes; }

    /** Danh sách size khách được chọn (rỗng = sản phẩm không có size) */
    @Transient
    public List<String> getSizeList() {
        String raw = sizes == null ? "" : sizes.trim();
        if ("-".equals(raw)) return List.of();
        if (!raw.isEmpty()) {
            Set<String> set = new LinkedHashSet<>();
            for (String t : raw.split("[,;\\s]+")) {
                if (!t.isBlank()) set.add(t.trim().toUpperCase());
            }
            return new ArrayList<>(set);
        }
        // Default by category (KOSIS fashion categories + legacy Vietnamese ones)
        String c = category == null ? "" : category.trim().toLowerCase();
        if (c.equals("phụ kiện") || c.equals("túi") || c.equals("accessories") || c.equals("bags")) return List.of();
        if (c.equals("giày") || c.equals("footwear")) return List.of("38", "39", "40", "41", "42", "43", "44");
        return List.of("S", "M", "L", "XL", "XXL");
    }

    @Transient
    public boolean isHasSizes() { return !getSizeList().isEmpty(); }


    // ==================== Tồn kho theo size ====================

    public String getSizeStock() { return sizeStock; }
    public void setSizeStock(String sizeStock) { this.sizeStock = sizeStock; }

    /** Đã quản lý tồn kho riêng cho từng size? (sản phẩm có size + có dữ liệu size_stock) */
    @Transient
    public boolean isPerSizeStock() {
        return isHasSizes() && sizeStock != null && !sizeStock.isBlank();
    }

    /** Sản phẩm có size nhưng chưa chia tồn kho theo size (dữ liệu cũ) */
    @Transient
    public boolean isLegacySizeStock() {
        return isHasSizes() && !isPerSizeStock();
    }

    private static String normSize(String size) {
        return size == null ? "" : size.trim().toUpperCase();
    }

    private Map<String, Integer> parseSizeStock() {
        Map<String, Integer> raw = new LinkedHashMap<>();
        if (sizeStock == null) return raw;
        for (String token : sizeStock.split(",")) {
            int i = token.lastIndexOf(':');
            if (i <= 0) continue;
            try {
                raw.put(normSize(token.substring(0, i)), Math.max(0, Integer.parseInt(token.substring(i + 1).trim())));
            } catch (NumberFormatException ignored) {
                // bỏ qua mục hỏng
            }
        }
        return raw;
    }

    /** Tồn kho từng size theo đúng thứ tự size bán (size chưa có số liệu = 0). Rỗng nếu chưa chia theo size.
     *  Sản phẩm chia kho theo màu: số liệu của mỗi size = tổng các màu. */
    @Transient
    public Map<String, Integer> getSizeStockMap() {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!isPerSizeStock()) return out;
        if (isPerColorStock()) {
            Map<String, Integer> raw = parseVariantStock();
            for (String sz : getSizeList()) {
                int sum = 0;
                for (ColorOption c : getColorList()) sum += raw.getOrDefault(variantRawKey(c.getName(), sz), 0);
                out.put(sz, sum);
            }
            return out;
        }
        Map<String, Integer> raw = parseSizeStock();
        for (String sz : getSizeList()) out.put(sz, raw.getOrDefault(sz, 0));
        return out;
    }

    /** "S:3,M:5" - tồn kho từng size (cộng mọi màu), dùng cho thẻ sản phẩm ngoài cửa hàng. Rỗng nếu chưa chia theo size. */
    @Transient
    public String getSizeStockSummary() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : getSizeStockMap().entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    /** Ghi tồn kho từng size và tự cập nhật `stock` = tổng các size.
     *  Sản phẩm có màu: số lượng mỗi size được chia đều cho các màu (dùng khi tạo mới / chuyển từ dữ liệu cũ). */
    public void setSizeStockMap(Map<String, Integer> map) {
        List<String> sizes = getSizeList();
        if (isHasColors()) {
            Map<String, Integer> out = new LinkedHashMap<>();
            List<ColorOption> colors = getColorList();
            int n = colors.size();
            if (sizes.isEmpty()) {
                int total = Math.max(0, stock);
                for (int i = 0; i < n; i++) out.put(variantKey(colors.get(i).getName(), ""), total / n + (i < total % n ? 1 : 0));
            } else {
                for (String sz : sizes) {
                    int q = Math.max(0, map.getOrDefault(sz, 0));
                    for (int i = 0; i < n; i++) out.put(variantKey(colors.get(i).getName(), sz), q / n + (i < q % n ? 1 : 0));
                }
            }
            setVariantStockMap(out);
            return;
        }
        writeSizeStockOnly(map);
    }

    /** Ghi tồn kho theo size (không chia theo màu) và cập nhật `stock` = tổng các size */
    private void writeSizeStockOnly(Map<String, Integer> map) {
        List<String> sizes = getSizeList();
        if (sizes.isEmpty()) {
            sizeStock = null;
            return;
        }
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (String sz : sizes) {
            int q = Math.max(0, map.getOrDefault(sz, 0));
            if (sb.length() > 0) sb.append(',');
            sb.append(sz).append(':').append(q);
            total += q;
        }
        sizeStock = sb.toString();
        stock = total;
    }

    /** Số lượng còn của 1 size (cộng mọi màu). Chưa chia theo size / không có size: trả về tồn kho chung. */
    public int getStockOf(String size) {
        if (isPerSizeStock()) return getSizeStockMap().getOrDefault(normSize(size), 0);
        return stock;
    }

    /**
     * Số lượng còn của đúng 1 (size, màu). Sản phẩm chưa chia kho theo màu (dữ liệu cũ) thì các màu dùng chung kho của size.
     * Màu để trống = màu đầu tiên; màu không còn trong danh sách = 0.
     */
    public int getStockOf(String size, String color) {
        if (!isPerColorStock()) return getStockOf(size);
        ColorOption c = pickColor(color, false);
        if (c == null) return 0;
        String sz = variantSize(size);
        if (sz == null) return 0;
        return parseVariantStock().getOrDefault(variantRawKey(c.getName(), sz), 0);
    }

    /** Tăng/giảm tồn kho của 1 size (delta âm = bán ra, dương = hoàn kho); không cho xuống dưới 0 */
    public void adjustStock(String size, int delta) {
        adjustStock(size, null, delta);
    }

    /** Tăng/giảm tồn kho của 1 (size, màu); không cho xuống dưới 0. Chưa chia kho theo màu thì chỉ tính theo size như trước. */
    public void adjustStock(String size, String color, int delta) {
        if (isPerColorStock()) {
            ColorOption c = pickColor(color, delta > 0);   // hoàn kho cho màu đã bị xoá -> cộng vào màu đầu tiên
            String sz = variantSize(size);
            if (c == null || sz == null) return;
            Map<String, Integer> m = getVariantStockMap();
            String key = variantKey(c.getName(), sz);
            if (m.containsKey(key)) m.put(key, Math.max(0, m.get(key) + delta));
            setVariantStockMap(m);
        } else if (isPerSizeStock()) {
            Map<String, Integer> m = getSizeStockMap();
            String key = normSize(size);
            if (m.containsKey(key)) m.put(key, Math.max(0, m.get(key) + delta));
            writeSizeStockOnly(m);   // kho chung các màu (dữ liệu cũ): chỉ cập nhật theo size
        } else {
            stock = Math.max(0, stock + delta);
        }
    }

    /** Số liệu điền sẵn vào form sửa: đã chia theo size thì dùng số thật, chưa thì chia đều tổng tồn kho */
    @Transient
    public Map<String, Integer> getSizeStockForForm() {
        if (isPerSizeStock()) return getSizeStockMap();
        Map<String, Integer> out = new LinkedHashMap<>();
        List<String> sizes = getSizeList();
        int n = sizes.size();
        for (int i = 0; i < n; i++) {
            out.put(sizes.get(i), stock / n + (i < stock % n ? 1 : 0));
        }
        return out;
    }

    // ==================== Tồn kho theo MÀU x SIZE ====================

    /** Sản phẩm có ít nhất một màu */
    @Transient
    public boolean isHasColors() { return !getColorList().isEmpty(); }

    /** Đã chia tồn kho riêng cho từng màu (sản phẩm có màu + dữ liệu dạng "Màu|SIZE:số") */
    @Transient
    public boolean isPerColorStock() {
        return isHasColors() && sizeStock != null && sizeStock.indexOf('|') >= 0;
    }

    /** Sản phẩm có màu nhưng chưa chia tồn kho theo màu (dữ liệu cũ: mọi màu dùng chung kho) */
    @Transient
    public boolean isLegacyColorStock() {
        return isHasColors() && !isPerColorStock();
    }

    /** Khoá chuẩn của 1 biến thể: "Tên màu|SIZE" (sản phẩm không có size: "Tên màu|") */
    public static String variantKey(String color, String size) {
        return (color == null ? "" : color.trim()) + "|" + normSize(size);
    }

    private static String variantRawKey(String color, String size) {
        return (color == null ? "" : color.trim().toLowerCase(java.util.Locale.ROOT)) + "|" + normSize(size);
    }

    /** Size dùng trong khoá biến thể: "" nếu sản phẩm không có size; null nếu size không nằm trong danh sách */
    private String variantSize(String size) {
        if (!isHasSizes()) return "";
        String sz = normSize(size);
        return getSizeList().contains(sz) ? sz : null;
    }

    /** Tìm màu theo tên; để trống (hoặc fallback = true với màu lạ) -> màu đầu tiên; không tìm thấy -> null */
    private ColorOption pickColor(String color, boolean fallbackToFirst) {
        List<ColorOption> list = getColorList();
        if (list.isEmpty()) return null;
        if (color == null || color.isBlank()) return list.get(0);
        ColorOption c = findColor(color);
        return c != null ? c : (fallbackToFirst ? list.get(0) : null);
    }

    /** Đọc tồn kho "Màu|SIZE:số" -> map khoá "màu(chữ thường)|SIZE" */
    private Map<String, Integer> parseVariantStock() {
        Map<String, Integer> raw = new LinkedHashMap<>();
        if (sizeStock == null) return raw;
        for (String token : sizeStock.split(",")) {
            int i = token.lastIndexOf(':');
            if (i <= 0) continue;
            String key = token.substring(0, i);
            int bar = key.lastIndexOf('|');
            if (bar < 0) continue;
            try {
                raw.put(variantRawKey(key.substring(0, bar), key.substring(bar + 1)),
                        Math.max(0, Integer.parseInt(token.substring(i + 1).trim())));
            } catch (NumberFormatException ignored) {
                // bỏ qua mục hỏng
            }
        }
        return raw;
    }

    /** Tồn kho từng (màu, size) theo thứ tự màu rồi size, khoá "Tên màu|SIZE". Rỗng nếu chưa chia theo màu. */
    @Transient
    public Map<String, Integer> getVariantStockMap() {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!isPerColorStock()) return out;
        Map<String, Integer> raw = parseVariantStock();
        List<String> sizes = isHasSizes() ? getSizeList() : List.of("");
        for (ColorOption c : getColorList()) {
            for (String sz : sizes) out.put(variantKey(c.getName(), sz), raw.getOrDefault(variantRawKey(c.getName(), sz), 0));
        }
        return out;
    }

    /** Ghi tồn kho từng (màu, size) (khoá "Tên màu|SIZE", không phân biệt hoa thường) và tự cập nhật `stock` = tổng */
    public void setVariantStockMap(Map<String, Integer> map) {
        List<ColorOption> colors = getColorList();
        if (colors.isEmpty()) return;
        Map<String, Integer> norm = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            int bar = e.getKey().lastIndexOf('|');
            if (bar < 0) continue;
            norm.put(variantRawKey(e.getKey().substring(0, bar), e.getKey().substring(bar + 1)), e.getValue());
        }
        List<String> sizes = isHasSizes() ? getSizeList() : List.of("");
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (ColorOption c : colors) {
            for (String sz : sizes) {
                int q = Math.max(0, norm.getOrDefault(variantRawKey(c.getName(), sz), 0));
                if (sb.length() > 0) sb.append(',');
                sb.append(variantKey(c.getName(), sz)).append(':').append(q);
                total += q;
            }
        }
        sizeStock = sb.toString();
        stock = total;
    }

    /** Số liệu điền sẵn vào form sửa (khoá "Tên màu|SIZE"): đã chia theo màu thì dùng số thật,
     *  chưa thì chia đều tồn kho của từng size (hoặc tổng tồn kho) cho các màu. */
    @Transient
    public Map<String, Integer> getVariantStockForForm() {
        if (isPerColorStock()) return getVariantStockMap();
        Map<String, Integer> out = new LinkedHashMap<>();
        List<ColorOption> colors = getColorList();
        int n = colors.size();
        if (n == 0) return out;
        if (isHasSizes()) {
            Map<String, Integer> perSize = getSizeStockForForm();
            for (String sz : getSizeList()) {
                int q = perSize.getOrDefault(sz, 0);
                for (int i = 0; i < n; i++) out.put(variantKey(colors.get(i).getName(), sz), q / n + (i < q % n ? 1 : 0));
            }
        } else {
            for (int i = 0; i < n; i++) out.put(variantKey(colors.get(i).getName(), ""), stock / n + (i < stock % n ? 1 : 0));
        }
        return out;
    }

    /** Chuyển sản phẩm có màu đang dùng kho chung sang kho riêng từng màu (chia đều, tổng không đổi). Đã chia rồi thì không làm gì. */
    public void convertToPerColorStock() {
        if (!isLegacyColorStock()) return;
        setVariantStockMap(getVariantStockForForm());
    }

    /**
     * Đổi danh sách size / màu / loại sản phẩm của sản phẩm ĐÃ CÓ tồn kho mà không làm mất hàng:
     * ô (màu, size) còn nguyên thì giữ số cũ, ô mới = 0. Riêng sản phẩm trước đó chưa chia theo màu
     * thì tồn kho của từng size được chia đều cho các màu. Nơi gọi phải chặn trước việc bỏ size/màu còn hàng.
     */
    public void reshape(String newSizes, String newColors, String newCategory) {
        boolean hadSizes = isHasSizes();
        Map<String, Integer> oldVar = isPerColorStock() ? getVariantStockMap() : null;
        Map<String, Integer> oldSize = hadSizes ? getSizeStockForForm() : new LinkedHashMap<>();
        int oldStock = stock;

        this.category = newCategory;
        this.sizes = newSizes;
        this.colors = newColors;
        boolean hasSizes = isHasSizes();
        List<String> sizeList = hasSizes ? getSizeList() : List.of("");

        if (isHasColors()) {
            List<ColorOption> cols = getColorList();
            int n = cols.size();
            Map<String, Integer> nv = new LinkedHashMap<>();
            for (String sz : sizeList) {
                int sizeQty = hasSizes ? (hadSizes ? oldSize.getOrDefault(sz, 0) : oldStock / sizeList.size() + (sizeList.indexOf(sz) < oldStock % sizeList.size() ? 1 : 0))
                                       : oldStock;
                for (int i = 0; i < n; i++) {
                    String key = variantKey(cols.get(i).getName(), sz);
                    nv.put(key, oldVar != null
                            ? oldVar.getOrDefault(key, 0)                       // đã chia theo màu: giữ số cũ, màu / size mới = 0
                            : sizeQty / n + (i < sizeQty % n ? 1 : 0));         // chưa chia theo màu: chia đều cho các màu
                }
            }
            setVariantStockMap(nv);
        } else if (hasSizes) {
            Map<String, Integer> ns = new LinkedHashMap<>();
            int n = sizeList.size();
            for (int i = 0; i < n; i++) {
                String sz = sizeList.get(i);
                int q;
                if (oldVar != null) {                                           // trước đó chia theo màu: cộng các màu lại
                    q = 0;
                    for (Map.Entry<String, Integer> e : oldVar.entrySet()) {
                        if (e.getKey().substring(e.getKey().lastIndexOf('|') + 1).equals(sz)) q += e.getValue();
                    }
                } else if (hadSizes) {
                    q = oldSize.getOrDefault(sz, 0);
                } else {
                    q = oldStock / n + (i < oldStock % n ? 1 : 0);              // trước đó không có size: chia đều
                }
                ns.put(sz, q);
            }
            writeSizeStockOnly(ns);
        } else {
            sizeStock = null;                                                   // không size, không màu: dùng 1 kho chung
            stock = oldStock;
        }
    }

    /**
     * Các lựa chọn khi nhập hàng: sản phẩm có màu -> mọi (màu, size) dạng "Tên màu|SIZE" ("Tên màu|" nếu không có size);
     * sản phẩm chỉ chia theo size -> danh sách size; còn lại -> rỗng (một dòng số lượng duy nhất).
     */
    @Transient
    public List<String> getImportVariants() {
        List<String> out = new ArrayList<>();
        if (isHasColors()) {
            List<String> szs = isHasSizes() ? getSizeList() : List.of("");
            for (ColorOption c : getColorList()) {
                for (String sz : szs) out.add(variantKey(c.getName(), sz));
            }
        } else if (isPerSizeStock()) {
            out.addAll(getSizeList());
        }
        return out;
    }

    /** Tổng tồn kho của 1 màu (cộng mọi size). Chưa chia theo màu: tổng tồn kho chung. */
    public int getStockOfColor(String color) {
        if (!isPerColorStock()) return stock;
        ColorOption c = pickColor(color, false);
        if (c == null) return 0;
        int sum = 0;
        for (Map.Entry<String, Integer> e : getVariantStockMap().entrySet()) {
            if (e.getKey().substring(0, e.getKey().lastIndexOf('|')).equalsIgnoreCase(c.getName())) sum += e.getValue();
        }
        return sum;
    }

    /** Một màu kèm tồn kho từng size - để trang quản trị hiện "Màu: S 3 · M 5" */
    public static class ColorStock {
        private final String name;
        private final String hex;
        private final int total;
        private final Map<String, Integer> sizes;
        public ColorStock(String name, String hex, int total, Map<String, Integer> sizes) {
            this.name = name; this.hex = hex; this.total = total; this.sizes = sizes;
        }
        public String getName() { return name; }
        public String getHex() { return hex; }
        public int getTotal() { return total; }
        /** Tồn kho từng size của màu này (rỗng nếu sản phẩm không có size) */
        public Map<String, Integer> getSizes() { return sizes; }
    }

    /** Tồn kho từng màu (kèm từng size) theo thứ tự màu; rỗng nếu chưa chia kho theo màu. */
    @Transient
    public List<ColorStock> getColorStockList() {
        List<ColorStock> out = new ArrayList<>();
        if (!isPerColorStock()) return out;
        Map<String, Integer> all = getVariantStockMap();
        for (ColorOption c : getColorList()) {
            Map<String, Integer> sizes = new LinkedHashMap<>();
            int total = 0;
            for (Map.Entry<String, Integer> e : all.entrySet()) {
                int bar = e.getKey().lastIndexOf('|');
                if (!e.getKey().substring(0, bar).equalsIgnoreCase(c.getName())) continue;
                total += e.getValue();
                String sz = e.getKey().substring(bar + 1);
                if (!sz.isEmpty()) sizes.put(sz, e.getValue());
            }
            out.add(new ColorStock(c.getName(), c.getHex(), total, sizes));
        }
        return out;
    }

    /** Màu mặc định khi mở trang chi tiết: màu đầu tiên còn hàng (không có thì màu đầu tiên); null nếu sản phẩm không có màu. */
    @Transient
    public ColorOption getDefaultColor() {
        List<ColorOption> list = getColorList();
        if (list.isEmpty()) return null;
        for (ColorOption c : list) {
            if (getStockOfColor(c.getName()) > 0) return c;
        }
        return list.get(0);
    }

    public boolean isNew() { return isNew; }
    public void setNew(boolean aNew) { isNew = aNew; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // ==================== KOSIS: gender + colours ====================

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getColors() { return colors; }
    public void setColors(String colors) { this.colors = colors; }

    /** One colour swatch: display name + CSS colour (+ ảnh gắn với màu này, nếu có). */
    public static class ColorOption {
        private final String name;
        private final String hex;
        private final int imageIndex;   // 0 = không gắn ảnh, 1..3 = ảnh thứ mấy của sản phẩm
        private final String image;     // URL ảnh tương ứng (null nếu không gắn / ảnh không tồn tại)
        public ColorOption(String name, String hex, int imageIndex, String image) {
            this.name = name; this.hex = hex; this.imageIndex = imageIndex; this.image = image;
        }
        public String getName() { return name; }
        public String getHex() { return hex; }
        public int getImageIndex() { return imageIndex; }
        public String getImage() { return image; }
    }

    /** Parsed colour options (empty list when the product has none). */
    @Transient
    public List<ColorOption> getColorList() {
        List<ColorOption> out = new ArrayList<>();
        if (colors == null || colors.isBlank()) return out;
        List<String> imgs = getImages();
        for (String token : colors.split(",")) {
            String[] parts = token.trim().split(":");
            if (parts.length < 2) continue;
            String name = parts[0].trim();
            String hex = parts[1].trim();
            if (name.isEmpty() || !hex.matches("#[0-9a-fA-F]{3,8}")) continue;   // only accept real hex colours (also keeps the value safe in style="")
            int idx = 0;
            if (parts.length > 2) {
                try { idx = Integer.parseInt(parts[2].trim()); } catch (NumberFormatException ignored) { idx = 0; }
            }
            String img = (idx >= 1 && idx <= imgs.size()) ? imgs.get(idx - 1) : null;
            out.add(new ColorOption(name, hex, img == null ? 0 : idx, img));
        }
        return out;
    }

    /** Tìm tuỳ chọn màu theo tên (không phân biệt hoa thường); null nếu không có. */
    public ColorOption findColor(String name) {
        if (name == null || name.isBlank()) return null;
        for (ColorOption c : getColorList()) {
            if (c.getName().equalsIgnoreCase(name.trim())) return c;
        }
        return null;
    }

    /** Đang chờ phiếu nhập đầu tiên (chưa bán). NULL trong DB (hàng cũ) = false. */
    public boolean isPendingImport() { return Boolean.TRUE.equals(pendingImport); }
    public void setPendingImport(boolean pendingImport) { this.pendingImport = pendingImport; }

    /** Khách có nhìn thấy / mua được sản phẩm này không (ngược lại với "chờ nhập hàng"). */
    @Transient
    public boolean isListed() { return !isPendingImport(); }

    @Transient
    public boolean isOnSale() { return oldPrice > price && price > 0; }
}
