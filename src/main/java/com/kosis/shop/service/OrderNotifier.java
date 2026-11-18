package com.kosis.shop.service;

import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.OrderItem;
import com.kosis.shop.entity.User;
import com.kosis.shop.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Gửi email thông báo cho khách khi đơn hàng đổi trạng thái (đã nhận đơn, xác nhận, từ chối, huỷ, giao cho vận chuyển).
 * Gọi từ controller SAU KHI thao tác đã thành công. Mọi lỗi đều được nuốt lại: email không bao giờ làm hỏng nghiệp vụ.
 */
@Component
public class OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);

    private final EmailService emailService;
    private final UserRepository userRepository;

    /** Địa chỉ website, dùng tạo link trong email (đặt kosis.base-url hoặc biến môi trường KOSIS_BASE_URL) */
    @Value("${kosis.base-url:http://localhost:8080}")
    private String baseUrl;

    public OrderNotifier(EmailService emailService, UserRepository userRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    public void placed(Order o) {
        String extra = o.isBank()
                ? "Nhân viên sẽ kiểm tra ảnh bill và khoản chuyển khoản của bạn, sau đó xác nhận đơn."
                : o.isCodDeposit()
                ? "Nhân viên sẽ kiểm tra khoản cọc " + money(o.getDepositAmount()) + " bạn đã chuyển, sau đó xác nhận đơn. "
                  + "Phần còn lại " + money(o.getCodDue()) + " bạn thanh toán khi nhận hàng."
                : "Nhân viên sẽ xác nhận đơn và liên hệ theo số điện thoại bạn đã cung cấp.";
        notify(o, "KOSIS - Đã nhận đơn hàng " + o.getOrderCode(), "Cảm ơn bạn đã đặt hàng tại KOSIS! Chúng tôi đã nhận được đơn của bạn.\n" + extra, true);
    }

    public void confirmed(Order o) {
        notify(o, "KOSIS - Đơn hàng " + o.getOrderCode() + " đã được xác nhận",
                "Đơn hàng của bạn đã được xác nhận và đang được chuẩn bị. Chúng tôi sẽ báo cho bạn khi đơn được giao cho đơn vị vận chuyển.\n"
                        + "Hóa đơn bán hàng số " + o.getInvoiceNumber() + " (xem và in tại): "
                        + trimSlash(baseUrl) + "/thanh-toan/hoa-don/" + o.getId(), true);
    }

    public void rejected(Order o) {
        notify(o, "KOSIS - Đơn hàng " + o.getOrderCode() + " không được xác nhận",
                "Rất tiếc, đơn hàng của bạn chưa thể được xác nhận.\nLý do: " + nz(o.getRejectReason())
                        + "\nNếu bạn đã chuyển khoản, vui lòng liên hệ cửa hàng để được hỗ trợ.", false);
    }

    public void cancelled(Order o) {
        notify(o, "KOSIS - Đơn hàng " + o.getOrderCode() + " đã được huỷ",
                "Đơn hàng của bạn đã được huỷ theo yêu cầu."
                        + (o.isHasTransfer() ? "\nNếu bạn đã chuyển khoản, cửa hàng sẽ liên hệ để xử lý khoản tiền đã chuyển." : ""), false);
    }

    public void shipped(Order o) {
        StringBuilder sb = new StringBuilder("Đơn hàng của bạn đã được giao cho đơn vị vận chuyển.\n");
        sb.append("Đơn vị vận chuyển: ").append(nz(o.getCarrierLabel())).append('\n');
        if (o.getTrackingCode() != null && !o.getTrackingCode().isBlank()) {
            sb.append("Mã vận đơn: ").append(o.getTrackingCode()).append('\n');
        }
        if (o.getTrackingUrl() != null && !o.getTrackingUrl().isBlank()) {
            sb.append("Theo dõi: ").append(o.getTrackingUrl()).append('\n');
        }
        sb.append("Khi nhận được hàng, bạn vào \"Đơn hàng của tôi\" để xác nhận đã nhận hàng.");
        notify(o, "KOSIS - Đơn hàng " + o.getOrderCode() + " đang được giao", sb.toString(), false);
    }

    /** Thông báo tuỳ biến cho khách (dùng cho luồng trả hàng / hoàn tiền) */
    public void custom(Order o, String subject, String intro) {
        notify(o, subject, intro, false);
    }

    // ---------------------------------------------------------------

    private void notify(Order o, String subject, String intro, boolean withItems) {
        try {
            if (o == null || o.getUsername() == null) return;
            User u = userRepository.findByUsername(o.getUsername()).orElse(null);
            if (u == null || u.getEmail() == null || u.getEmail().isBlank()) return;

            StringBuilder sb = new StringBuilder();
            String name = (o.getReceiverName() == null || o.getReceiverName().isBlank()) ? "bạn" : o.getReceiverName();
            sb.append("Xin chào ").append(name).append(",\n\n").append(intro).append("\n\n");
            sb.append("Mã đơn: ").append(o.getOrderCode()).append('\n');
            if (withItems) {
                for (OrderItem it : o.getItems()) {
                    sb.append(" - ").append(it.getProductName())
                            .append(it.getVariantText().isEmpty() ? "" : " (" + it.getVariantText() + ")")
                            .append(" x").append(it.getQuantity())
                            .append(": ").append(money(it.getLineTotal())).append('\n');
                }
                if (o.getDiscountAmount() > 0) {
                    sb.append("Mã giảm giá ").append(nz(o.getVoucherCode())).append(": -").append(money(o.getDiscountAmount())).append('\n');
                }
                sb.append("Phí vận chuyển: ").append(o.getShippingFee() == 0 ? "Miễn phí" : money(o.getShippingFee())).append('\n');
                sb.append("Tổng cộng: ").append(money(o.getTotalAmount())).append('\n');
                sb.append("Thanh toán: ").append(o.getPaymentLabel()).append('\n');
                sb.append("Giao tới: ").append(o.getReceiverName()).append(", ").append(o.getPhone()).append(", ").append(o.getAddress()).append('\n');
            }
            sb.append("\nXem đơn hàng: ").append(trimSlash(baseUrl)).append("/don-hang-cua-toi\n\nKOSIS");
            emailService.send(u.getEmail(), subject, sb.toString());
        } catch (Exception e) {
            log.warn("Không soạn được email cho đơn {}: {}", o == null ? "?" : o.getOrderCode(), e.getMessage());
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String trimSlash(String s) {
        String t = s == null ? "" : s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    /** 1290000 -> "1.290.000 ₫" */
    private static String money(long v) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", sym).format(v) + " ₫";
    }
}