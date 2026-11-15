package com.kosis.shop.entity;

import jakarta.persistence.*;

/**
 * Danh mục (loại) sản phẩm do ADMIN quản lý ở /dashboard/cai-dat/menu.
 * Sản phẩm vẫn lưu tên danh mục dạng chữ (Product.category); đổi tên danh mục thì CategoryService cập nhật luôn các sản phẩm.
 */
@Entity
@Table(name = "product_categories")
public class ProductCategory implements Positioned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    /** Thứ tự hiển thị (0, 1, 2...) ở menu, bộ lọc và ô chọn loại sản phẩm */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Có hiện trên thanh menu / trang chủ không (ẩn khỏi menu nhưng vẫn gán được cho sản phẩm) */
    @Column(name = "show_in_menu", nullable = false)
    private boolean visible = true;

    public ProductCategory() {
    }

    public ProductCategory(String name, int sortOrder, boolean visible) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.visible = visible;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public int getSortOrder() { return sortOrder; }
    @Override
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
