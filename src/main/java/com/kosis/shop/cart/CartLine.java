package com.kosis.shop.cart;

import com.kosis.shop.entity.Product;

/** Một dòng trong giỏ hàng để hiển thị (đã gắn với sản phẩm thật trong DB) */
public class CartLine {

    private final Product product;
    private final int quantity;
    private final String size;
    private final int maxQuantity;
    private final String color;

    public CartLine(Product product, int quantity, String size, String color, int maxQuantity) {
        this.product = product;
        this.quantity = quantity;
        this.size = size == null ? "" : size;
        this.color = color == null ? "" : color;
        this.maxQuantity = maxQuantity;
    }

    /** Màu đã chọn ("" nếu sản phẩm không có màu) */
    public String getColor() { return color; }
    public boolean isHasColor() { return !color.isEmpty(); }

    /** Mã màu CSS để hiện chấm màu (null nếu không có) */
    public String getColorHex() {
        Product.ColorOption c = product.findColor(color);
        return c == null ? null : c.getHex();
    }

    /** Ảnh của dòng: ảnh gắn với màu đã chọn nếu có, không thì ảnh chính của sản phẩm */
    public String getImage() {
        Product.ColorOption c = product.findColor(color);
        return (c != null && c.getImage() != null) ? c.getImage() : product.getImage();
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }

    /** Size đã chọn ("" nếu sản phẩm không có size) */
    public String getSize() { return size; }
    public boolean isHasSize() { return !size.isEmpty(); }

    /** Còn hàng để mua hay không */
    public boolean isAvailable() { return product.getStockOf(size, color) > 0; }   // đúng size + đúng màu khách chọn

    /** Số lượng tối đa cho dòng này = min(tồn kho còn lại của đúng size + màu này, giới hạn mỗi sản phẩm) */
    public int getMaxQuantity() { return maxQuantity; }

    public long getLineTotal() { return isAvailable() ? product.getPrice() * quantity : 0; }
}
