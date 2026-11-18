package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Một lần tồn kho thay đổi (sổ kho): nhập hàng, bán ra, hoàn kho từ đơn bị huỷ/trả, hoặc điều chỉnh giảm thủ công.
 * Lưu kèm tên / mã sản phẩm và KHÔNG dùng khoá ngoại tới products để xoá sản phẩm thì lịch sử vẫn còn.
 */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_product", columnList = "product_id"),
        @Index(name = "idx_stock_movements_created", columnList = "created_at")})
public class StockMovement {

    public static final String TYPE_IMPORT = "IMPORT";
    public static final String TYPE_SALE = "SALE";
    public static final String TYPE_RESTORE = "RESTORE";
    public static final String TYPE_ADJUST = "ADJUST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "product_code", length = 30)
    private String productCode;

    /** Size (null nếu sản phẩm không chia kho theo size) */
    @Column(length = 20)
    private String size;

    /** Màu (null nếu sản phẩm không có màu) */
    @Column(length = 40)
    private String color;

    @Column(nullable = false, length = 10)
    private String type;

    /** Số lượng thay đổi: dương = tăng, âm = giảm */
    @Column(nullable = false)
    private int delta;

    @Column(length = 300)
    private String reason;

    /** Chứng từ liên quan: mã đơn (KS000012) hoặc số phiếu nhập */
    @Column(length = 100)
    private String reference;

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public StockMovement() {
    }

    @Transient
    public String getTypeLabel() {
        return switch (type == null ? "" : type) {
            case TYPE_IMPORT -> "Nhập hàng";
            case TYPE_SALE -> "Bán ra";
            case TYPE_RESTORE -> "Hoàn kho";
            case TYPE_ADJUST -> "Điều chỉnh giảm";
            default -> type;
        };
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getDelta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
