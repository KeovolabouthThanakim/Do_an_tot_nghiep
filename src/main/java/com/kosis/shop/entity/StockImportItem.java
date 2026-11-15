package com.kosis.shop.entity;

import jakarta.persistence.*;

/**
 * Một dòng của phiếu nhập: sản phẩm (+ size) x số lượng x giá nhập.
 * Lưu kèm tên / mã sản phẩm tại thời điểm nhập và KHÔNG dùng khoá ngoại tới products,
 * để sau này xoá sản phẩm thì lịch sử nhập hàng vẫn còn nguyên.
 */
@Entity
@Table(name = "stock_import_items", indexes = @Index(name = "idx_stock_import_items_product", columnList = "product_id"))
public class StockImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_import_id")
    private StockImport stockImport;

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

    @Column(nullable = false)
    private int quantity;

    /** Giá nhập MỘT món (₫) */
    @Column(name = "unit_cost", nullable = false)
    private long unitCost;

    public StockImportItem() {
    }

    @Transient
    public long getLineTotal() {
        return unitCost * quantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public StockImport getStockImport() { return stockImport; }
    public void setStockImport(StockImport stockImport) { this.stockImport = stockImport; }

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

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public long getUnitCost() { return unitCost; }
    public void setUnitCost(long unitCost) { this.unitCost = unitCost; }
}
