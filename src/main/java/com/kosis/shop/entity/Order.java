package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn hàng khách đặt ở trang thanh toán. Bảng tên "orders" (vì "order" là từ khoá của SQL).
 * Tên, giá, ảnh sản phẩm được CHỤP LẠI trong OrderItem để đơn cũ không đổi khi admin sửa giá sau này.
 */
@Entity
@Table(name = "orders")
public class Order {

    public static final String PAYMENT_COD = "COD";
    public static final String PAYMENT_BANK = "BANK";
    /** COD: chờ nhân viên/admin xác nhận đơn */
    public static final String STATUS_PENDING = "PENDING";
    /** Chuyển khoản: đã gửi ảnh bill, chờ nhân viên/admin kiểm tra tiền đã về chưa */
    public static final String STATUS_PAYMENT_REVIEW = "PAYMENT_REVIEW";
    /** Nhân viên/admin đã kiểm tra và xác nhận */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** Bị từ chối (bill sai / chưa nhận được tiền...) - đã hoàn lại tồn kho */
    public static final String STATUS_REJECTED = "REJECTED";

    // ---- Trạng thái giao hàng (chỉ có ý nghĩa khi đơn đã được xác nhận) ----
    /** Đã xác nhận, đang chuẩn bị hàng, chưa giao cho đơn vị vận chuyển (đơn cũ chưa có dữ liệu cũng coi là PREPARING) */
    public static final String SHIP_PREPARING = "PREPARING";
    /** Đã giao cho đơn vị vận chuyển, đang trên đường tới khách */
    public static final String SHIP_SHIPPING = "SHIPPING";
    /** Vận chuyển báo đã giao tới khách, đang chờ khách xác nhận (quá hạn không phản hồi thì hệ thống tự chuyển DELIVERED) */
    public static final String SHIP_ARRIVED = "ARRIVED";
    /** Khách đã nhận hàng (khách bấm xác nhận / nhân viên ghi nhận / hệ thống tự hoàn tất) */
    public static final String SHIP_DELIVERED = "DELIVERED";
    /** Giao không thành công vì lý do khác (sai địa chỉ, thất lạc, hư hỏng...) - hàng hoàn về cửa hàng */
    public static final String SHIP_RETURNED = "RETURNED";
    /** Khách KHÔNG NHẬN hàng (từ chối, không nghe máy, không có mặt) - tính vào lịch sử cảnh báo của khách */
    public static final String SHIP_REFUSED = "REFUSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã đơn hiển thị cho khách, VD: KS000012 (tạo sau khi có id) */
    @Column(name = "order_code", unique = true, length = 20)
    private String orderCode;

    /** Tài khoản đặt hàng */
    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 500)
    private String note;

    @Column(name = "payment_method", nullable = false, length = 10)
    private String paymentMethod = PAYMENT_COD;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** Tổng khách phải trả = tiền hàng - giảm giá + phí vận chuyển */
    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    /** Số tiền được giảm nhờ mã giảm giá (0 nếu không dùng) */
    @Column(name = "discount_amount", nullable = false, columnDefinition = "bigint not null default 0")
    private long discountAmount;

    /** Phí vận chuyển khách phải trả (0 = miễn phí; đơn cũ trước khi có phí = 0) */
    @Column(name = "shipping_fee", nullable = false, columnDefinition = "bigint not null default 0")
    private long shippingFee;

    /** Mã giảm giá đã dùng (null nếu không dùng) */
    @Column(name = "voucher_code", length = 30)
    private String voucherCode;

    /**
     * Tiền ĐẶT CỌC khách phải chuyển khoản trước khi đơn COD được xử lý (0 = không cọc / đơn chuyển khoản / đơn cũ).
     * Phần còn lại (totalAmount - depositAmount) shipper thu khi giao hàng.
     */
    @Column(name = "deposit_amount", nullable = false, columnDefinition = "bigint not null default 0")
    private long depositAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Thời hạn đổi trả (ngày) tại lúc đặt hàng - CHỤP LẠI để đổi cài đặt sau này không ảnh hưởng đơn đã bán
     * (cả hoá đơn lẫn quyền gửi yêu cầu trả hàng). null = đơn tạo trước khi có cột này -> dùng cài đặt hiện tại.
     */
    @Column(name = "return_window_days")
    private Integer returnWindowDays;

    /** Tên file ảnh bill chuyển khoản (lưu ở thư mục private/slips, KHÔNG public) */
    @Column(name = "slip_image", length = 100)
    private String slipImage;

    /** Nhân viên/admin đã xác nhận hoặc từ chối đơn này */
    @Column(name = "reviewed_by", length = 50)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reject_reason", length = 300)
    private String rejectReason;

    @Column(name = "shipping_status", length = 20)
    private String shippingStatus;

    /** Tên hằng trong {@link Carrier}, VD: GHN */
    @Column(length = 30)
    private String carrier;

    @Column(name = "tracking_code", length = 60)
    private String trackingCode;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    /** Lúc vận chuyển báo đã giao tới khách (bắt đầu tính hạn tự hoàn tất) */
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** Ai xác nhận đã giao: "@customer" (khách bấm), "@auto" (hệ thống tự hoàn tất), hoặc username nhân viên */
    @Column(name = "delivered_by", length = 50)
    private String deliveredBy;

    /** Ghi chú giao hàng, hoặc lý do giao không thành công */
    @Column(name = "shipping_note", length = 300)
    private String shippingNote;

    /** Nhân viên/admin cập nhật giao hàng gần nhất */
    @Column(name = "shipping_by", length = 50)
    private String shippingBy;

    /**
     * Đã cộng lại tồn kho của đơn này chưa (khi từ chối / khách huỷ / giao không thành công).
     * Dùng để không hoàn kho 2 lần, và để biết cần trừ kho lại nếu giao lại đơn đã hoàn.
     * Kiểu Boolean (có thể null) để các đơn cũ trong DB không cần cập nhật dữ liệu.
     */
    @Column(name = "stock_restored")
    private Boolean stockRestored;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /** Tiền hàng (tổng các dòng sản phẩm, trước giảm giá và phí vận chuyển) */
    @Transient
    public long getItemsTotal() {
        long sum = 0;
        for (OrderItem it : items) sum += it.getLineTotal();
        return sum;
    }

    @Transient
    public String getPaymentLabel() {
        if (PAYMENT_BANK.equals(paymentMethod)) return "Chuyển khoản ngân hàng";
        return isCodDeposit() ? "Thanh toán khi nhận hàng (COD) + đặt cọc" : "Thanh toán khi nhận hàng (COD)";
    }

    /** Đơn COD có đặt cọc trước bằng chuyển khoản? */
    @Transient
    public boolean isCodDeposit() {
        return PAYMENT_COD.equals(paymentMethod) && depositAmount > 0;
    }

    /** Đơn có khoản chuyển khoản cần nhân viên đối chiếu (chuyển khoản toàn bộ HOẶC cọc COD) - cần ảnh bill */
    @Transient
    public boolean isHasTransfer() {
        return isBank() || isCodDeposit();
    }

    /** Số tiền khách phải chuyển khoản NGAY lúc đặt (toàn bộ đơn chuyển khoản / tiền cọc đơn COD / 0) - dùng để đối soát */
    @Transient
    public long getPayNow() {
        if (isBank()) return totalAmount;
        return isCodDeposit() ? Math.min(depositAmount, totalAmount) : 0;
    }

    /** Số tiền shipper thu khi giao hàng (đơn COD: tổng - tiền cọc; đơn chuyển khoản: 0) */
    @Transient
    public long getCodDue() {
        return PAYMENT_COD.equals(paymentMethod) ? Math.max(0, totalAmount - depositAmount) : 0;
    }

    @Transient
    public String getStatusLabel() {
        if (STATUS_PENDING.equals(status)) return "Chờ xác nhận";
        if (STATUS_PAYMENT_REVIEW.equals(status)) return "Chờ kiểm tra thanh toán";
        if (STATUS_CONFIRMED.equals(status)) return "Đã xác nhận";
        if (STATUS_REJECTED.equals(status)) return isCancelledByCustomer() ? "Đã hủy" : "Bị từ chối";
        return status;
    }

    /** Đơn chuyển khoản? */
    @Transient
    public boolean isBank() {
        return PAYMENT_BANK.equals(paymentMethod);
    }

    /** Đang chờ nhân viên/admin xử lý (xác nhận hoặc từ chối) */
    @Transient
    public boolean isAwaitingReview() {
        return STATUS_PENDING.equals(status) || STATUS_PAYMENT_REVIEW.equals(status);
    }

    @Transient
    public boolean isConfirmed() { return STATUS_CONFIRMED.equals(status); }

    @Transient
    public boolean isRejected() { return STATUS_REJECTED.equals(status); }

    /** Marker trong reviewedBy khi chính khách huỷ đơn (đơn vẫn mang trạng thái REJECTED để các màn hình cũ hoạt động) */
    public static final String CANCELLED_BY_CUSTOMER = "@customer";

    /** Marker trong reviewedBy khi hệ thống tự xác nhận đơn nhờ webhook ngân hàng báo tiền đã về */
    public static final String CONFIRMED_BY_BANK = "@bank";

    /** Đơn do chính khách huỷ khi còn chờ xác nhận */
    @Transient
    public boolean isCancelledByCustomer() {
        return STATUS_REJECTED.equals(status) && CANCELLED_BY_CUSTOMER.equals(reviewedBy);
    }

    /** Khách được tự huỷ đơn: đơn còn đang chờ nhân viên kiểm tra/xác nhận */
    @Transient
    public boolean isCustomerCanCancel() { return isAwaitingReview(); }

    /** Tên người xử lý để hiển thị (khách tự huỷ thì ghi rõ thay vì hiện mã @customer) */
    @Transient
    public String getReviewedByLabel() {
        if (CANCELLED_BY_CUSTOMER.equals(reviewedBy)) return "Khách hàng (tự huỷ đơn)";
        if (CONFIRMED_BY_BANK.equals(reviewedBy)) return "Hệ thống (tự đối soát chuyển khoản)";
        return reviewedBy == null ? "" : reviewedBy;
    }

    public boolean isStockRestored() { return Boolean.TRUE.equals(stockRestored); }
    public void setStockRestored(boolean stockRestored) { this.stockRestored = stockRestored; }

    @Transient
    public boolean isHasSlip() { return slipImage != null && !slipImage.isBlank(); }

    @Transient
    public String getReviewedAtText() {
        return reviewedAt == null ? "" : reviewedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /** Hóa đơn chỉ được xuất khi đơn ĐÃ XÁC NHẬN; số hóa đơn = "HD" + mã đơn (VD HDKS000012), duy nhất theo đơn */
    @Transient
    public boolean isInvoiceIssued() { return isConfirmed(); }

    @Transient
    public String getInvoiceNumber() { return isConfirmed() && orderCode != null ? "HD" + orderCode : ""; }

    /** Ngày xuất hóa đơn = lúc nhân viên xác nhận đơn */
    @Transient
    public String getInvoiceDateText() { return isConfirmed() ? getReviewedAtText() : ""; }

    @Transient
    public String getCreatedAtText() {
        return createdAt == null ? "" : createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    // ===== Getter / Setter =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(long totalAmount) { this.totalAmount = totalAmount; }

    public long getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(long discountAmount) { this.discountAmount = discountAmount; }

    public long getShippingFee() { return shippingFee; }
    public void setShippingFee(long shippingFee) { this.shippingFee = shippingFee; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }

    public long getDepositAmount() { return depositAmount; }
    public void setDepositAmount(long depositAmount) { this.depositAmount = Math.max(0, depositAmount); }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getSlipImage() { return slipImage; }
    public void setSlipImage(String slipImage) { this.slipImage = slipImage; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    // ===== Giao hàng =====

    /** Luôn có giá trị: đơn cũ (cột còn null) được coi là đang chuẩn bị hàng */
    public String getShippingStatus() {
        return shippingStatus == null ? SHIP_PREPARING : shippingStatus;
    }
    public void setShippingStatus(String shippingStatus) { this.shippingStatus = shippingStatus; }

    @Transient
    public boolean isPreparing() { return SHIP_PREPARING.equals(getShippingStatus()); }

    @Transient
    public boolean isShipping() { return SHIP_SHIPPING.equals(getShippingStatus()); }

    @Transient
    public boolean isDelivered() { return SHIP_DELIVERED.equals(getShippingStatus()); }

    @Transient
    public boolean isReturned() { return SHIP_RETURNED.equals(getShippingStatus()); }

    @Transient
    public boolean isArrived() { return SHIP_ARRIVED.equals(getShippingStatus()); }

    @Transient
    public boolean isRefused() { return SHIP_REFUSED.equals(getShippingStatus()); }

    /** Giao không thành công (khách không nhận hoặc lý do khác) */
    @Transient
    public boolean isFailed() { return isReturned() || isRefused(); }

    /** Khách được bấm "Tôi đã nhận được hàng": đơn đã xác nhận và đang trên đường / đã tới nơi */
    @Transient
    public boolean isCustomerCanConfirm() { return isConfirmed() && (isShipping() || isArrived()); }

    /**
     * Số ngày đổi trả áp dụng cho đơn này: số ngày đã chụp lại lúc đặt hàng; đơn cũ chưa có thì dùng [fallbackDays]
     * (cài đặt hiện tại).
     */
    public int effectiveReturnDays(int fallbackDays) {
        return returnWindowDays != null && returnWindowDays > 0 ? returnWindowDays : fallbackDays;
    }

    /**
     * Còn trong thời hạn đổi trả kể từ lúc khách nhận hàng? (chỉ đơn đã giao thành công)
     * [days] chỉ dùng cho đơn cũ chưa chụp lại số ngày (xem effectiveReturnDays).
     */
    public boolean canRequestReturn(int days) {
        return isConfirmed() && isDelivered() && deliveredAt != null
                && LocalDateTime.now().isBefore(deliveredAt.plusDays(effectiveReturnDays(days)));
    }

    /** Hạn gửi yêu cầu trả hàng, VD: 12/11/2026 */
    public String returnDeadlineText(int days) {
        return deliveredAt == null ? "" : deliveredAt.plusDays(effectiveReturnDays(days)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    @Transient
    public String getShippingLabel() {
        return switch (getShippingStatus()) {
            case SHIP_SHIPPING -> "Đang vận chuyển";
            case SHIP_ARRIVED -> "Đã tới nơi - chờ khách xác nhận";
            case SHIP_DELIVERED -> "Đã giao thành công";
            case SHIP_RETURNED -> "Giao không thành công";
            case SHIP_REFUSED -> "Khách không nhận hàng";
            default -> "Đang chuẩn bị hàng";
        };
    }

    /** Bước hiện tại trên thanh tiến trình: 1 Đã xác nhận, 2 Chuẩn bị hàng, 3 Đang vận chuyển, 4 Đã giao */
    @Transient
    public int getShipStep() {
        if (!isConfirmed()) return 0;
        if (isDelivered() || isArrived()) return 4;
        if (isShipping() || isFailed()) return 3;
        return 2;
    }

    @Transient
    public String getArrivedAtText() {
        return arrivedAt == null ? "" : arrivedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /** Ngày hệ thống tự hoàn tất nếu khách không phản hồi, VD: 08/11/2026 */
    public String autoDeadlineText(int days) {
        return arrivedAt == null ? "" : arrivedAt.plusDays(days).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    @Transient
    public String getDeliveredByLabel() {
        if (deliveredBy == null || deliveredBy.isBlank()) return "";
        if ("@customer".equals(deliveredBy)) return "Khách xác nhận đã nhận hàng";
        if ("@auto".equals(deliveredBy)) return "Hệ thống tự hoàn tất (khách không phản hồi)";
        return "Nhân viên " + deliveredBy;
    }

    @Transient
    public String getCarrierLabel() {
        Carrier c = Carrier.fromCode(carrier);
        return c != null ? c.getLabel() : (carrier == null ? "" : carrier);
    }

    @Transient
    public String getTrackingUrl() {
        Carrier c = Carrier.fromCode(carrier);
        return c == null ? null : c.urlFor(trackingCode);
    }

    @Transient
    public String getShippedAtText() {
        return shippedAt == null ? "" : shippedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    @Transient
    public String getDeliveredAtText() {
        return deliveredAt == null ? "" : deliveredAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }

    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }

    public LocalDateTime getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(LocalDateTime arrivedAt) { this.arrivedAt = arrivedAt; }

    public String getDeliveredBy() { return deliveredBy; }
    public void setDeliveredBy(String deliveredBy) { this.deliveredBy = deliveredBy; }

    public Integer getReturnWindowDays() { return returnWindowDays; }
    public void setReturnWindowDays(Integer returnWindowDays) { this.returnWindowDays = returnWindowDays; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public String getShippingNote() { return shippingNote; }
    public void setShippingNote(String shippingNote) { this.shippingNote = shippingNote; }

    public String getShippingBy() { return shippingBy; }
    public void setShippingBy(String shippingBy) { this.shippingBy = shippingBy; }
}
