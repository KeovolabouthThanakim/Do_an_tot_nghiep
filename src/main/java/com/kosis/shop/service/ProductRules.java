package com.kosis.shop.service;

import com.kosis.shop.entity.Product;

/**
 * Quy tắc dùng chung khi TẠO sản phẩm (form Thêm sản phẩm, ô "+ Sản phẩm mới" ở trang Nhập hàng, nhập từ CSV):
 * tính giá bán từ giá niêm yết + % giảm, kiểm tra / chuẩn hoá loại, đối tượng và danh sách màu.
 * Tách ra một chỗ để ba cách tạo sản phẩm luôn kiểm tra giống hệt nhau.
 */
public final class ProductRules {

    private ProductRules() {
    }

    /**
     * Tự tính giá theo giá niêm yết + % giảm giá:
     *   số tiền giảm = giá niêm yết x % / 100
     *   giá bán      = giá niêm yết - số tiền giảm
     *   giá cũ (gạch ngang trên web) = giá niêm yết nếu có giảm, ngược lại = 0
     */
    public static void applyPricing(Product p, long listPrice) {
        int pct = Math.max(0, Math.min(90, p.getDiscountPercent()));
        long amount = Math.round(listPrice * pct / 100.0);
        p.setDiscountPercent(pct);
        p.setPrice(listPrice - amount);
        p.setOldPrice(pct > 0 ? listPrice : 0);
    }

    /** "A, B hoặc C" */
    static String listText(java.util.Collection<String> items) {
        java.util.List<String> l = new java.util.ArrayList<>(items);
        if (l.size() <= 1) return String.join("", l);
        return String.join(", ", l.subList(0, l.size() - 1)) + " hoặc " + l.get(l.size() - 1);
    }

    /**
     * Kiểm tra + chuẩn hoá loại sản phẩm và đối tượng (Nam/Nữ/Trẻ em/Unisex) để cửa hàng lọc đúng.
     * Trả về thông báo lỗi, hoặc null nếu hợp lệ.
     */
    public static String normalizeClassification(Product p) {
        return normalizeClassification(p, Product.CATEGORIES);
    }

    /** Như trên nhưng với danh sách danh mục cho phép do ADMIN quản lý (CategoryService.names()). */
    public static String normalizeClassification(Product p, java.util.Collection<String> allowedCategories) {
        String cat = p.getCategory() == null ? "" : p.getCategory().trim();
        String matched = allowedCategories.stream().filter(c -> c.equalsIgnoreCase(cat)).findFirst().orElse(null);
        if (matched == null) {
            return "Vui lòng chọn loại sản phẩm (" + listText(allowedCategories) + ").";
        }
        p.setCategory(matched);

        String g = p.getGender() == null ? "" : p.getGender().trim().toUpperCase();
        if (!Product.GENDERS.contains(g)) {
            return "Vui lòng chọn đối tượng sử dụng (Nam / Nữ / Trẻ em / Unisex).";
        }
        p.setGender(g);

        String colors = p.getColors() == null ? "" : p.getColors().trim();
        if (colors.length() > 500) {
            return "Danh sách màu quá dài (tối đa khoảng 12 màu, tên màu ngắn gọn).";
        }
        // "|" dùng để ghép khoá giỏ hàng / tồn kho theo màu nên không được nằm trong tên màu
        colors = colors.replace('|', ' ');
        p.setColors(colors.isEmpty() ? null : colors);
        // tồn kho theo màu được khoá bằng tên màu -> hai màu trùng tên (không phân biệt hoa thường) sẽ dồn số lượng vào nhau
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Product.ColorOption c : p.getColorList()) {
            if (!seen.add(c.getName().toLowerCase(java.util.Locale.ROOT))) {
                return "Màu \"" + c.getName() + "\" bị trùng tên. Hãy đặt tên khác nhau cho mỗi màu.";
            }
        }
        return null;
    }
}
