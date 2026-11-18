package com.kosis.shop.entity;

import jakarta.persistence.*;

/** Một dòng sản phẩm trong đơn hàng (đã chụp lại tên/giá/ảnh tại thời điểm đặt) */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "product_code", length = 30)
    private String productCode;

    @Column(length = 500)
    private String image;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    /**
     * Giá vốn (giá nhập) MỖI SẢN PHẨM tại thời điểm đặt hàng - chụp lại như unitPrice để báo cáo lợi nhuận
     * không đổi khi sau này nhập hàng giá khác. null = không biết (đơn cũ trước khi có cột này,
     * hoặc sản phẩm chưa nhập giá nhập) -> KHÔNG tính vào giá vốn/lợi nhuận.
     */
    @Column(name = "unit_cost")
    private Long unitCost;

    /** Size khách đã chọn (null/rỗng nếu sản phẩm không có size) */
    @Column(length = 20)
    private String size;

    /** Màu khách đã chọn (null/rỗng nếu sản phẩm không có màu) */
    @Column(length = 50)
    private String color;

    public OrderItem() {
    }

    /** Chuỗi mô tả lựa chọn để hiển thị, vd "Size M · Màu Olive" ("" nếu không có size/màu) */
    @Transient
    public String getVariantText() {
        StringBuilder sb = new StringBuilder();
        if (size != null && !size.isBlank()) sb.append("Size ").append(size);
        if (color != null && !color.isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("Màu ").append(color);
        }
        return sb.toString();
    }

    @Transient
    public long getLineTotal() { return unitPrice * quantity; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(long unitPrice) { this.unitPrice = unitPrice; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Long getUnitCost() { return unitCost; }
    public void setUnitCost(Long unitCost) { this.unitCost = unitCost; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
