package com.kosis.shop.cart;

import java.util.List;

/** Ảnh chụp giỏ hàng tại thời điểm hiển thị: các dòng + tổng tiền */
public class CartView {

    private final List<CartLine> lines;

    public CartView(List<CartLine> lines) { this.lines = lines; }

    public List<CartLine> getLines() { return lines; }
    public boolean isEmpty() { return lines.isEmpty(); }

    /** Tổng số lượng sản phẩm (chỉ tính hàng còn) */
    public int getTotalQuantity() {
        return lines.stream().filter(CartLine::isAvailable).mapToInt(CartLine::getQuantity).sum();
    }

    /** Tạm tính (chỉ tính hàng còn) */
    public long getSubtotal() {
        return lines.stream().mapToLong(CartLine::getLineTotal).sum();
    }

    /** Tổng tiền tiết kiệm nhờ giảm giá (giá cũ - giá bán) x số lượng, chỉ tính hàng còn */
    public long getSavings() {
        return lines.stream()
                .filter(CartLine::isAvailable)
                .mapToLong(l -> l.getProduct().getSavedAmount() * l.getQuantity())
                .sum();
    }

    /** Có ít nhất 1 sản phẩm đã hết hàng trong giỏ -> phải xoá trước khi thanh toán */
    public boolean isHasUnavailable() {
        return lines.stream().anyMatch(l -> !l.isAvailable());
    }
}
