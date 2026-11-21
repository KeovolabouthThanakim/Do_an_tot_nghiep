package com.kosis.shop.entity;

import com.kosis.shop.config.I18n;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Yêu cầu trả hàng / hoàn tiền của khách cho một đơn đã giao thành công (mỗi đơn chỉ có 1 yêu cầu).
 * Luồng: REQUESTED (khách gửi) -> APPROVED (nhân viên đồng ý, khách gửi hàng về) -> REFUNDED (đã nhận hàng + hoàn tiền xong)
 *        hoặc REQUESTED -> REJECTED (nhân viên từ chối, bắt buộc ghi lý do).
 */
@Entity
@Table(name = "return_requests", uniqueConstraints = @UniqueConstraint(name = "uk_return_order", columnNames = "order_id"))
public class ReturnRequest {

    public static final String REQUESTED = "REQUESTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String REFUNDED = "REFUNDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_code", length = 20)
    private String orderCode;

    @Column(nullable = false, length = 50)
    private String username;

    /** Lý do khách muốn trả hàng */
    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status = REQUESTED;

    /** Ghi chú của nhân viên (lý do từ chối, hướng dẫn gửi hàng, mã giao dịch hoàn tiền...) */
    @Column(name = "staff_note", length = 300)
    private String staffNote;

    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    @Column(name = "handled_by", length = 50)
    private String handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ReturnRequest() {
    }

    @Transient
    public boolean isRequested() { return REQUESTED.equals(status); }

    @Transient
    public boolean isApproved() { return APPROVED.equals(status); }

    @Transient
    public boolean isRejected() { return REJECTED.equals(status); }

    @Transient
    public boolean isRefunded() { return REFUNDED.equals(status); }

    /** Đang chờ nhân viên xử lý tiếp (duyệt hoặc hoàn tiền) */
    @Transient
    public boolean isOpen() { return isRequested() || isApproved(); }

    @Transient
    public String getStatusLabel() {
        return switch (status) {
            case REQUESTED -> I18n.t("return.status.requested", "Chờ cửa hàng duyệt");
            case APPROVED -> I18n.t("return.status.approved", "Đã duyệt - chờ nhận hàng trả");
            case REJECTED -> I18n.t("order.status.declined", "Bị từ chối");
            case REFUNDED -> I18n.t("return.status.refunded", "Đã hoàn tiền");
            default -> status;
        };
    }

    @Transient
    public String getCreatedAtText() {
        return createdAt == null ? "" : createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    @Transient
    public String getHandledAtText() {
        return handledAt == null ? "" : handledAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStaffNote() { return staffNote; }
    public void setStaffNote(String staffNote) { this.staffNote = staffNote; }

    public long getRefundAmount() { return refundAmount; }
    public void setRefundAmount(long refundAmount) { this.refundAmount = refundAmount; }

    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }

    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
