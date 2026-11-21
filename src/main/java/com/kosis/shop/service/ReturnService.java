package com.kosis.shop.service;

import com.kosis.shop.config.I18n;
import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.ReturnRequest;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.repository.ReturnRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Luồng trả hàng / hoàn tiền: khách gửi yêu cầu (trong hạn, đơn đã giao) -> nhân viên duyệt hoặc từ chối
 * -> khi nhận lại hàng thì ghi nhận đã hoàn tiền (có thể cộng lại tồn kho).
 * Mọi lỗi nghiệp vụ ném IllegalStateException với nội dung tiếng Việt để hiện cho người dùng.
 */
@Service
public class ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /** Số ngày được phép gửi yêu cầu trả hàng kể từ lúc nhận hàng (khớp với trang chính sách /returns) */
    @Value("${kosis.returns.window-days:7}")
    private int windowDays;

    /** Cài đặt do ADMIN chỉnh trên /dashboard/cai-dat (ghi đè giá trị trong properties). Không có (test) thì dùng windowDays. */
    @Autowired(required = false)
    private SettingsService settings;

    public ReturnService(ReturnRequestRepository returnRepository, OrderRepository orderRepository, OrderService orderService) {
        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    public int getWindowDays() {
        int days = settings != null ? settings.getInt(SettingsService.RETURNS_WINDOW_DAYS) : windowDays;
        return Math.max(1, Math.min(60, days));
    }

    private static String clean(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** Khách gửi yêu cầu trả hàng cho đơn của chính mình */
    @Transactional
    public ReturnRequest request(Long orderId, String username, String reason) {
        Order o = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng.")));
        if (!Objects.equals(o.getUsername(), username)) {
            throw new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng."));
        }
        if (!o.isConfirmed() || !o.isDelivered()) {
            throw new IllegalStateException(I18n.t("return.only.delivered", "Chỉ đơn đã giao thành công mới gửi được yêu cầu trả hàng."));
        }
        if (returnRepository.findByOrderId(orderId).isPresent()) {
            throw new IllegalStateException(I18n.t("return.already.requested", "Đơn này đã có yêu cầu trả hàng rồi."));
        }
        if (!o.canRequestReturn(getWindowDays())) {
            // số ngày của CHÍNH đơn này (đã chụp lúc đặt hàng), không phải cài đặt hiện tại
            throw new IllegalStateException(I18n.t("return.window.passed", "Đã quá {0} ngày kể từ khi nhận hàng nên không thể gửi yêu cầu trả hàng.", o.effectiveReturnDays(getWindowDays())));
        }
        String why = clean(reason, 500);
        if (why.length() < 5) {
            throw new IllegalStateException(I18n.t("return.reason.required", "Vui lòng nhập lý do trả hàng (ít nhất 5 ký tự)."));
        }
        ReturnRequest r = new ReturnRequest();
        r.setOrderId(o.getId());
        r.setOrderCode(o.getOrderCode());
        r.setUsername(username);
        r.setReason(why);
        r.setRefundAmount(o.getTotalAmount());
        return returnRepository.save(r);
    }

    private ReturnRequest load(Long id) {
        return returnRepository.findById(id).orElseThrow(() -> new IllegalStateException("Không tìm thấy yêu cầu trả hàng."));
    }

    private void stamp(ReturnRequest r, String staff, String note) {
        r.setHandledBy(staff);
        r.setHandledAt(LocalDateTime.now());
        String n = clean(note, 300);
        r.setStaffNote(n.isEmpty() ? null : n);
    }

    /** Nhân viên đồng ý cho trả hàng (note: hướng dẫn gửi hàng về, không bắt buộc) */
    @Transactional
    public ReturnRequest approve(Long id, String staff, String note) {
        ReturnRequest r = load(id);
        if (!r.isRequested()) {
            throw new IllegalStateException("Yêu cầu này đã được xử lý rồi (" + r.getStatusLabel() + ").");
        }
        r.setStatus(ReturnRequest.APPROVED);
        stamp(r, staff, note);
        return returnRepository.save(r);
    }

    /** Nhân viên từ chối - bắt buộc ghi lý do để khách biết */
    @Transactional
    public ReturnRequest reject(Long id, String staff, String note) {
        ReturnRequest r = load(id);
        if (!r.isRequested()) {
            throw new IllegalStateException("Yêu cầu này đã được xử lý rồi (" + r.getStatusLabel() + ").");
        }
        if (clean(note, 300).length() < 3) {
            throw new IllegalStateException("Vui lòng nhập lý do từ chối (để khách biết vì sao).");
        }
        r.setStatus(ReturnRequest.REJECTED);
        stamp(r, staff, note);
        return returnRepository.save(r);
    }

    /**
     * Đã nhận lại hàng và hoàn tiền cho khách. restock = true: hàng còn nguyên vẹn -> cộng lại tồn kho.
     * Việc chuyển tiền thật do nhân viên làm ngoài hệ thống; ở đây chỉ ghi nhận (note: mã giao dịch hoàn tiền...).
     */
    @Transactional
    public ReturnRequest refund(Long id, String staff, String note, boolean restock) {
        ReturnRequest r = load(id);
        if (!r.isApproved()) {
            throw new IllegalStateException("Chỉ yêu cầu đã được duyệt mới ghi nhận hoàn tiền được.");
        }
        if (restock) {
            orderService.restockAfterReturn(r.getOrderId());
        }
        r.setStatus(ReturnRequest.REFUNDED);
        stamp(r, staff, note);
        return returnRepository.save(r);
    }

    // ---------- tra cứu ----------

    public List<ReturnRequest> findAllNewestFirst() {
        return returnRepository.findAllByOrderByCreatedAtDesc();
    }

    public long countOpen() {
        return returnRepository.countByStatusIn(List.of(ReturnRequest.REQUESTED, ReturnRequest.APPROVED));
    }

    /** orderId -> yêu cầu trả hàng của khách (để trang "Đơn hàng của tôi" hiện trạng thái) */
    public Map<Long, ReturnRequest> byOrderForUser(String username) {
        Map<Long, ReturnRequest> map = new HashMap<>();
        for (ReturnRequest r : returnRepository.findByUsernameOrderByCreatedAtDesc(username)) {
            map.put(r.getOrderId(), r);
        }
        return map;
    }

    public ReturnRequest findByOrderId(Long orderId) {
        return returnRepository.findByOrderId(orderId).orElse(null);
    }
}
