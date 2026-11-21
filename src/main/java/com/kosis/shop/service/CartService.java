package com.kosis.shop.service;

import com.kosis.shop.config.I18n;
import com.kosis.shop.cart.Cart;
import com.kosis.shop.cart.CartLine;
import com.kosis.shop.cart.CartView;
import com.kosis.shop.entity.Product;
import com.kosis.shop.repository.ProductRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    /** Sản phẩm có size nhưng khách chưa chọn -> controller sẽ đưa khách sang trang chi tiết để chọn */
    public static class SizeRequiredException extends IllegalArgumentException {
        private final long productId;
        public SizeRequiredException(long productId, String message) {
            super(message);
            this.productId = productId;
        }
        public long getProductId() { return productId; }
    }

    private final ProductRepository productRepository;

    public CartService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    private static String normSize(String size) {
        return size == null ? "" : size.trim().toUpperCase();
    }

    private static String normColor(String color) {
        return color == null ? "" : color.trim();
    }

    private static String sizeText(String size, String color) {
        String s = size == null || size.isEmpty() ? "" : I18n.t("cart.text.size", "size {0}", size);
        String c = color == null || color.isEmpty() ? "" : I18n.t("cart.text.color", "màu {0}", color);
        String both = s.isEmpty() ? c : (c.isEmpty() ? s : s + ", " + c);
        return both.isEmpty() ? "" : " (" + both + ")";
    }

    /** Sản phẩm + size + màu đã được kiểm tra hợp lệ (còn hàng, đúng size, đúng màu) */
    private record Checked(Product product, String size, String color) {}

    private Checked check(long productId, String size, String color) {
        Product p = productRepository.findListedById(productId)   // sản phẩm chờ nhập hàng = chưa bán
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("cart.product.gone", "Sản phẩm không còn tồn tại.")));
        if (p.getStock() <= 0) {
            throw new IllegalArgumentException(I18n.t("cart.product.soldout", "Sản phẩm \"{0}\" đã hết hàng.", p.getName()));
        }
        size = normSize(size);
        // Màu: sản phẩm có màu mà khách không chọn -> lấy màu đầu tiên CÒN HÀNG; màu phải nằm trong danh sách của sản phẩm
        color = normColor(color);
        List<Product.ColorOption> colors = p.getColorList();
        if (colors.isEmpty()) {
            color = "";
        } else if (color.isEmpty()) {
            color = firstAvailableColor(p, size);
        } else {
            Product.ColorOption found = p.findColor(color);
            if (found == null) {
                throw new IllegalArgumentException(I18n.t("cart.color.unavailable", "Màu {0} không có cho sản phẩm này.", color));
            }
            color = found.getName();      // dùng đúng tên màu đã lưu của sản phẩm
        }
        if (p.isHasSizes()) {
            if (size.isEmpty()) {
                // Khách không chọn size -> tự lấy size đầu tiên CÒN HÀNG (của màu đã chọn); khách vẫn đổi được ngay trong giỏ hàng
                size = firstAvailableSize(p, color);
            }
            if (!p.getSizeList().contains(size)) {
                throw new IllegalArgumentException(I18n.t("cart.size.not.available", "Size {0} không có cho sản phẩm này.", size));
            }
        } else {
            size = "";
        }
        if (p.getStockOf(size, color) <= 0) {
            throw new IllegalArgumentException(I18n.t("cart.product.variant.soldout", "Sản phẩm \"{0}\"{1} đã hết hàng.", p.getName(), sizeText(size, color)));
        }
        return new Checked(p, size, color);
    }

    /** Size đầu tiên còn hàng của màu đã chọn (không còn size nào thì trả về size đầu tiên để báo hết hàng) */
    private static String firstAvailableSize(Product p, String color) {
        for (String sz : p.getSizeList()) {
            if (p.getStockOf(sz, color) > 0) return sz;
        }
        return p.getSizeList().get(0);
    }

    /** Màu đầu tiên còn hàng (đúng size khách chọn nếu có chọn); không có màu nào còn thì trả về màu đầu tiên */
    private static String firstAvailableColor(Product p, String size) {
        for (Product.ColorOption c : p.getColorList()) {
            int left = size.isEmpty() ? p.getStockOfColor(c.getName()) : p.getStockOf(size, c.getName());
            if (left > 0) return c.getName();
        }
        return p.getColorList().get(0).getName();
    }

    /**
     * Số lượng tối đa khách được có ở dòng (sản phẩm, size), theo tồn kho.
     * - Đã chia tồn kho theo size: kho của đúng size đó, dùng chung cho mọi màu -> trừ đi số lượng các màu khác cùng size.
     * - Chưa chia theo size / không có size: các size và màu dùng chung 1 kho nên trừ đi số lượng ở các dòng khác trong giỏ.
     */
    private static int allowedFor(Product p, Cart cart, long productId, String size, String color) {
        int current = cart.quantityOf(productId, size, color);
        int pool;
        if (p.isPerColorStock()) pool = current;
        else pool = p.isPerSizeStock() ? cart.quantityOfSize(productId, size) : cart.quantityOfProduct(productId);
        int others = pool - current;
        return Math.min(p.getStockOf(size, color) - others, Cart.MAX_QTY_PER_ITEM);
    }

    /**
     * Thêm sản phẩm vào giỏ (cộng dồn số lượng theo từng size, tổng các size không vượt tồn kho).
     * @throws IllegalArgumentException với nội dung tiếng Việt để hiện cho khách
     */
    public int add(HttpSession session, long productId, String size, String color, int quantity) {
        Checked c = check(productId, size, color);
        Product p = c.product();
        size = c.size();
        color = c.color();

        Cart cart = Cart.of(session);
        int allowed = allowedFor(p, cart, productId, size, color);
        int current = cart.quantityOf(productId, size, color);
        if (current >= allowed) {
            throw new IllegalArgumentException(I18n.t("cart.max.reached", "Bạn đã có {0} sản phẩm này{1} trong giỏ - đã đạt số lượng tối đa (còn {2} trong kho).", current, sizeText(size, color), p.getStockOf(size, color)));
        }
        int add = Math.min(Math.max(1, quantity), allowed - current);
        cart.set(productId, size, color, current + add);
        return cart.totalQuantity();
    }

    /**
     * Nút "Mua ngay": đảm bảo trong giỏ CÓ ÍT NHẤT `quantity` cái của sản phẩm/size này,
     * KHÔNG cộng dồn thêm. VD đã bấm "Thêm vào giỏ" (giỏ có 1) rồi bấm "Mua ngay" -> vẫn là 1.
     */
    public int buyNow(HttpSession session, long productId, String size, String color, int quantity) {
        Checked c = check(productId, size, color);
        Product p = c.product();
        String sz = c.size();
        String col = c.color();

        Cart cart = Cart.of(session);
        int allowed = allowedFor(p, cart, productId, sz, col);
        int current = cart.quantityOf(productId, sz, col);
        if (allowed <= 0) {
            throw new IllegalArgumentException(I18n.t("cart.max.reached.2", "Sản phẩm này{0} đã đạt số lượng tối đa trong giỏ (còn {1} trong kho).", sizeText(sz, col), p.getStockOf(sz, col)));
        }
        int want = Math.min(Math.max(1, quantity), allowed);
        if (current < want) {
            cart.set(productId, sz, col, want);
        }
        return cart.totalQuantity();
    }

    /** Đặt lại số lượng của 1 dòng (<= 0 thì xoá dòng), tự giới hạn theo tồn kho */
    public void setQuantity(HttpSession session, long productId, String size, String color, int quantity) {
        size = normSize(size);
        color = normColor(color);
        Cart cart = Cart.of(session);
        if (quantity <= 0) {
            cart.remove(productId, size, color);
            return;
        }
        final String sz = size;
        final String col = color;
        productRepository.findListedById(productId).ifPresentOrElse(
                p -> {
                    int allowed = allowedFor(p, cart, productId, sz, col);
                    cart.set(productId, sz, col, Math.min(quantity, Math.max(1, allowed)));
                },
                () -> cart.remove(productId, sz, col));
    }

    /** Đổi size của 1 dòng (nếu đã có dòng cùng size mới thì gộp số lượng lại) */
    public void changeSize(HttpSession session, long productId, String oldSize, String color, String newSize) {
        oldSize = normSize(oldSize);
        newSize = normSize(newSize);
        color = normColor(color);
        if (oldSize.equals(newSize)) return;
        Cart cart = Cart.of(session);
        Product p = productRepository.findListedById(productId).orElse(null);
        if (p == null || !p.getSizeList().contains(newSize)) return;
        if (p.getStockOf(newSize, color) <= 0) return;      // size mới (của màu này) đã hết hàng -> không đổi
        int qty = cart.quantityOf(productId, oldSize, color);
        if (qty <= 0) return;
        cart.remove(productId, oldSize, color);
        cart.set(productId, newSize, color, cart.quantityOf(productId, newSize, color) + qty);
    }

    public void remove(HttpSession session, long productId, String size, String color) {
        Cart.of(session).remove(productId, normSize(size), normColor(color));
    }

    public void clear(HttpSession session) {
        Cart.of(session).clear();
    }

    /**
     * Dựng giỏ hàng để hiển thị: đọc giá/tồn kho MỚI NHẤT từ DB,
     * bỏ sản phẩm đã bị xoá khỏi kho / size không còn hợp lệ, và hạ số lượng nếu vượt tồn kho.
     */
    public CartView view(HttpSession session) {
        Cart cart = Cart.of(session);
        List<Long> ids = new ArrayList<>();
        for (String k : cart.getItems().keySet()) ids.add(Cart.productIdOf(k));
        Map<Long, Product> byId = new HashMap<>();
        for (Product p : productRepository.findAllById(ids)) byId.put(p.getId(), p);

        List<CartLine> lines = new ArrayList<>();
        boolean migrated = false;
        for (String key : new ArrayList<>(cart.getItems().keySet())) {
            long id = Cart.productIdOf(key);
            String size = Cart.sizeOf(key);
            String color = Cart.colorOf(key);
            Product p = byId.get(id);
            if (p != null && p.isPendingImport()) p = null;   // sản phẩm chờ nhập hàng: gỡ khỏi giỏ như sản phẩm đã xoá
            if (p == null || (p.isHasSizes() && !p.getSizeList().contains(size))) {   // đã xoá / size không còn
                cart.removeKey(key);
                continue;
            }
            // Màu không còn hợp lệ (admin đã sửa/xoá màu) -> bỏ dòng; khoá cũ chưa có màu -> gán màu đầu tiên
            if (!p.getColorList().isEmpty()) {
                Product.ColorOption co = color.isEmpty() ? p.getColorList().get(0) : p.findColor(color);
                if (co == null) {
                    cart.removeKey(key);
                    continue;
                }
                if (!co.getName().equals(color)) {         // chuẩn hoá lại khoá của dòng
                    int old = cart.getItems().get(key);
                    cart.removeKey(key);
                    cart.set(id, size, co.getName(), cart.quantityOf(id, size, co.getName()) + old);
                    migrated = true;                       // dựng lại giỏ sau vòng lặp với khoá đã chuẩn hoá
                    continue;
                }
            } else if (!color.isEmpty()) {
                cart.removeKey(key);
                continue;
            }
            int qty = cart.quantityOf(id, size, color);
            int max = 0;
            if (p.getStockOf(size, color) > 0) {
                max = allowedFor(p, cart, id, size, color);
                if (max <= 0) {                // các dòng khác trong giỏ đã dùng hết tồn kho
                    cart.removeKey(key);
                    continue;
                }
                if (qty > max) {
                    qty = max;
                    cart.set(id, size, color, qty);
                }
            }
            lines.add(new CartLine(p, qty, size, color, max));
        }
        if (migrated) return view(session);
        return new CartView(lines);
    }
}
