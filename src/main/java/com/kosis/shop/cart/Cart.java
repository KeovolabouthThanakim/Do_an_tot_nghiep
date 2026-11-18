package com.kosis.shop.cart;

import jakarta.servlet.http.HttpSession;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Giỏ hàng của khách - lưu trong HttpSession.
 * Mỗi dòng được định danh bằng "idSảnPhẩm|size|màu" (cùng 1 áo nhưng khác size hoặc khác màu = 2 dòng khác nhau;
 * khoá cũ dạng "id|size" vẫn đọc được, màu = rỗng)
 * và chỉ lưu số lượng; tên, giá, ảnh luôn đọc lại từ DB để không bị lỗi thời.
 */
public class Cart implements Serializable {

    private static final long serialVersionUID = 2L;
    public static final String SESSION_KEY = "GS_CART";
    public static final int MAX_QTY_PER_ITEM = 99;

    private final Map<String, Integer> items = new LinkedHashMap<>();

    public static String key(long productId, String size, String color) {
        return productId + "|" + (size == null ? "" : size) + "|" + (color == null ? "" : color);
    }

    public static long productIdOf(String key) {
        return Long.parseLong(key.substring(0, key.indexOf('|')));
    }

    public static String sizeOf(String key) {
        int a = key.indexOf('|');
        int b = key.indexOf('|', a + 1);
        return b < 0 ? key.substring(a + 1) : key.substring(a + 1, b);
    }

    /** Màu đã chọn ("" nếu sản phẩm không có màu hoặc khoá cũ chưa có màu) */
    public static String colorOf(String key) {
        int a = key.indexOf('|');
        int b = key.indexOf('|', a + 1);
        return b < 0 ? "" : key.substring(b + 1);
    }

    /** Lấy giỏ hàng từ session, chưa có thì tạo mới (tạo session nếu cần) */
    public static Cart of(HttpSession session) {
        Object o = session.getAttribute(SESSION_KEY);
        if (o instanceof Cart c) return c;
        Cart c = new Cart();
        session.setAttribute(SESSION_KEY, c);
        return c;
    }

    /** Đếm tổng số lượng trong giỏ mà KHÔNG tạo session mới (dùng cho số nhỏ trên icon giỏ hàng) */
    public static int countIn(HttpSession session) {
        if (session == null) return 0;
        Object o = session.getAttribute(SESSION_KEY);
        return o instanceof Cart c ? c.totalQuantity() : 0;
    }

    public Map<String, Integer> getItems() { return items; }

    public int quantityOf(long productId, String size, String color) {
        return items.getOrDefault(key(productId, size, color), 0);
    }

    /** Tổng số lượng của 1 sản phẩm ở 1 size, cộng mọi màu (dùng cho sản phẩm chưa chia kho theo màu - dữ liệu cũ) */
    public int quantityOfSize(long productId, String size) {
        String sz = size == null ? "" : size;
        int n = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (productIdOf(e.getKey()) == productId && sizeOf(e.getKey()).equals(sz)) n += e.getValue();
        }
        return n;
    }

    /** Tổng số lượng của 1 sản phẩm ở mọi size, mọi màu (tồn kho tính chung cho mọi size) */
    public int quantityOfProduct(long productId) {
        int n = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (productIdOf(e.getKey()) == productId) n += e.getValue();
        }
        return n;
    }

    public void set(long productId, String size, String color, int quantity) {
        String k = key(productId, size, color);
        if (quantity <= 0) items.remove(k);
        else items.put(k, Math.min(quantity, MAX_QTY_PER_ITEM));
    }

    public void remove(long productId, String size, String color) { items.remove(key(productId, size, color)); }

    public void removeKey(String key) { items.remove(key); }

    public void clear() { items.clear(); }

    public boolean isEmpty() { return items.isEmpty(); }

    public int totalQuantity() {
        int n = 0;
        for (int q : items.values()) n += q;
        return n;
    }
}
