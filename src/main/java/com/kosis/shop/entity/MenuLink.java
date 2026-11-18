package com.kosis.shop.entity;

import jakarta.persistence.*;

/** Liên kết thêm vào thanh menu của cửa hàng (ngoài các mục cố định và danh mục), do ADMIN quản lý. */
@Entity
@Table(name = "menu_links")
public class MenuLink implements Positioned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    /** Đường dẫn trong cửa hàng (bắt đầu bằng /) hoặc địa chỉ ngoài (http:// hoặc https://) */
    @Column(name = "url", nullable = false, length = 300)
    private String url;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "visible", nullable = false)
    private boolean visible = true;

    public MenuLink() {
    }

    public MenuLink(String label, String url, int sortOrder, boolean visible) {
        this.label = label;
        this.url = url;
        this.sortOrder = sortOrder;
        this.visible = visible;
    }

    /** Liên kết ra ngoài cửa hàng (mở tab mới) */
    @Transient
    public boolean isExternal() {
        return url != null && url.regionMatches(true, 0, "http", 0, 4);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    @Override
    public int getSortOrder() { return sortOrder; }
    @Override
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
