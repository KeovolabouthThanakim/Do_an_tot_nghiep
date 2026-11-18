package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu nhập hàng: ghi lại mỗi lần nhập hàng từ một NGUỒN (nhà cung cấp) - nhập ngày nào, những món nào,
 * số lượng và GIÁ NHẬP từng món. Khi lưu phiếu, tồn kho của sản phẩm được cộng thêm (xem StockImportService).
 * Phiếu chỉ để tra cứu (không sửa/xoá) - nhập sai thì chỉnh lại tồn kho ở trang Kho hàng.
 */
@Entity
@Table(name = "stock_imports", indexes = @Index(name = "idx_stock_imports_date", columnList = "import_date"))
public class StockImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nguồn hàng: tên nhà cung cấp / xưởng / chợ... */
    @Column(nullable = false, length = 150)
    private String supplier;

    /** SĐT / địa chỉ / link của nguồn (không bắt buộc) */
    @Column(name = "supplier_contact", length = 200)
    private String supplierContact;

    /** Số hoá đơn / chứng từ của nhà cung cấp (không bắt buộc) */
    @Column(length = 60)
    private String reference;

    @Column(name = "import_date", nullable = false)
    private LocalDate importDate;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "stockImport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StockImportItem> items = new ArrayList<>();

    public StockImport() {
    }

    public void addItem(StockImportItem item) {
        item.setStockImport(this);
        items.add(item);
    }

    /** Mã phiếu hiển thị, VD: NH00012 */
    @Transient
    public String getCode() {
        return id == null ? "NH—" : String.format("NH%05d", id);
    }

    /** Tổng tiền cả phiếu (₫) */
    @Transient
    public long getTotalCost() {
        long t = 0;
        for (StockImportItem i : items) t += i.getLineTotal();
        return t;
    }

    /** Tổng số món nhập trong phiếu */
    @Transient
    public int getTotalQty() {
        int t = 0;
        for (StockImportItem i : items) t += i.getQuantity();
        return t;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public String getSupplierContact() { return supplierContact; }
    public void setSupplierContact(String supplierContact) { this.supplierContact = supplierContact; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public LocalDate getImportDate() { return importDate; }
    public void setImportDate(LocalDate importDate) { this.importDate = importDate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<StockImportItem> getItems() { return items; }
    public void setItems(List<StockImportItem> items) { this.items = items; }
}
