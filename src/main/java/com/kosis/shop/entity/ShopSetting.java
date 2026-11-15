package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Một giá trị cài đặt do ADMIN chỉnh trên /dashboard/cai-dat (ghi đè giá trị mặc định trong application.properties).
 * Chỉ lưu những mục ĐÃ bị đổi khác mặc định: không có dòng nào = đang dùng giá trị mặc định.
 */
@Entity
@Table(name = "shop_settings")
public class ShopSetting {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Khoá cài đặt, VD "shipping.fee" (xem SettingsService) */
    @Id
    @Column(name = "setting_key", length = 80)
    private String key;

    /** Giá trị dạng chữ (số được đọc/ghi qua SettingsService). Chuỗi rỗng = cố ý để trống */
    @Column(name = "setting_value", length = 500)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Tên đăng nhập của người sửa gần nhất */
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    public ShopSetting() {
    }

    public ShopSetting(String key, String value, String updatedBy) {
        this.key = key;
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    public String getUpdatedAtText() {
        return updatedAt == null ? "—" : updatedAt.format(FMT);
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
