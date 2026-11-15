package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Mã giảm giá (voucher). Giảm theo phần trăm (PERCENT, có thể giới hạn mức giảm tối đa) hoặc số tiền cố định (FIXED).
 * Áp dụng trên TẠM TÍNH của đơn (sau giá sale, trước phí vận chuyển).
 */
@Entity
@Table(name = "vouchers", uniqueConstraints = @UniqueConstraint(name = "uk_voucher_code", columnNames = "code"))
public class Voucher {

    public static final String PERCENT = "PERCENT";
    public static final String FIXED = "FIXED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã khách nhập (luôn chữ HOA, không dấu cách) */
    @Column(nullable = false, length = 30)
    private String code;

    @Column(length = 150)
    private String description;

    /** PERCENT hoặc FIXED */
    @Column(name = "discount_type", nullable = false, length = 10)
    private String discountType = PERCENT;

    /** PERCENT: 1-100 (%); FIXED: số tiền (₫) */
    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    /** Đơn tối thiểu (tạm tính) để dùng mã; 0 = không yêu cầu */
    @Column(name = "min_order", nullable = false)
    private long minOrder;

    /** Mức giảm tối đa cho mã PERCENT; 0 = không giới hạn */
    @Column(name = "max_discount", nullable = false)
    private long maxDiscount;

    /** Tổng số lượt dùng cho phép; 0 = không giới hạn */
    @Column(name = "usage_limit", nullable = false)
    private int usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    /** Hết hạn lúc (null = không hết hạn) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Voucher() {
    }

    /** Chuẩn hoá mã: bỏ khoảng trắng, chữ HOA */
    public static String normalize(String code) {
        return code == null ? "" : code.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    /** Số tiền được giảm trên tạm tính [subtotal] (không vượt quá tạm tính) */
    public long discountFor(long subtotal) {
        if (subtotal <= 0) return 0;
        long d;
        if (PERCENT.equals(discountType)) {
            d = subtotal * discountValue / 100;
            if (maxDiscount > 0) d = Math.min(d, maxDiscount);
        } else {
            d = discountValue;
        }
        return Math.max(0, Math.min(d, subtotal));
    }

    @Transient
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    @Transient
    public boolean isExhausted() {
        return usageLimit > 0 && usedCount >= usageLimit;
    }

    /** Dùng được ngay bây giờ? (đang bật, chưa hết hạn, còn lượt) */
    @Transient
    public boolean isUsable() {
        return active && !isExpired() && !isExhausted();
    }

    @Transient
    public boolean isPercent() { return PERCENT.equals(discountType); }

    @Transient
    public String getExpiresAtText() {
        return expiresAt == null ? "Không hết hạn" : expiresAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }

    public long getDiscountValue() { return discountValue; }
    public void setDiscountValue(long discountValue) { this.discountValue = discountValue; }

    public long getMinOrder() { return minOrder; }
    public void setMinOrder(long minOrder) { this.minOrder = minOrder; }

    public long getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(long maxDiscount) { this.maxDiscount = maxDiscount; }

    public int getUsageLimit() { return usageLimit; }
    public void setUsageLimit(int usageLimit) { this.usageLimit = usageLimit; }

    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
