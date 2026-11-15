package com.kosis.shop.controller;

import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.OrderItem;
import com.kosis.shop.repository.UserRepository;
import com.kosis.shop.service.OrderNotifier;
import com.kosis.shop.service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Quản lý đơn hàng trong dashboard - chỉ ADMIN và NHANVIEN vào được
 * (đã chặn ở SecurityConfig: requestMatchers("/dashboard/**")).
 * MỌI đơn đều phải được nhân viên/admin XÁC NHẬN hoặc TỪ CHỐI sau khi kiểm tra
 * (đơn chuyển khoản: kiểm tra ảnh bill + tiền đã về tài khoản).
 */
@Controller
@RequestMapping("/dashboard/don-hang")
public class OrderAdminController {

    private final OrderService orderService;
    private final UserRepository userRepository;
    private final OrderNotifier orderNotifier;

    public OrderAdminController(OrderService orderService, UserRepository userRepository, OrderNotifier orderNotifier) {
        this.orderNotifier = orderNotifier;
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    // ---------- Danh sách đơn ----------
    @GetMapping
    public String list(Authentication auth, Model model) {
        List<Order> orders = orderService.findAllNewestFirst();
        long waiting = orders.stream().filter(Order::isAwaitingReview).count();
        long confirmed = orders.stream().filter(Order::isConfirmed).count();
        long rejected = orders.stream().filter(Order::isRejected).count();

        addStaffInfo(auth, model);
        model.addAttribute("orders", orders);
        model.addAttribute("refuseMap", orderService.refusalHistoryFor(orders));
        model.addAttribute("totalCount", orders.size());
        model.addAttribute("waitingCount", waiting);
        model.addAttribute("confirmedCount", confirmed);
        model.addAttribute("rejectedCount", rejected);
        return "dashboard/don-hang";
    }

    // ---------- Xuất danh sách đơn ra file CSV (mở được bằng Excel) ----------
    /**
     * [status] = all | waiting | confirmed | rejected, [q] = từ khoá (mã đơn / tên / số điện thoại) - cùng cách lọc
     * với bảng trên trang, nên file xuất ra đúng những đơn đang hiển thị.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "all") String status,
                                         @RequestParam(defaultValue = "") String q,
                                         @RequestParam(defaultValue = "all") String payment,
                                         @RequestParam(defaultValue = "all") String ship,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) { LocalDate t = from; from = to; to = t; }
        String key = q.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder("\uFEFF");   // BOM để Excel đọc đúng tiếng Việt
        sb.append("Mã đơn,Ngày đặt,Tài khoản,Người nhận,Số điện thoại,Địa chỉ,Thanh toán,Trạng thái,Giao hàng,")
          .append("Tiền hàng (VND),Giảm giá (VND),Mã giảm giá,Phí vận chuyển (VND),Tổng tiền (VND),")
          .append("Tiền cọc (VND),Thu khi giao (VND),Sản phẩm,Người xử lý,Thời gian xử lý,Lý do từ chối\r\n");
        int count = 0;
        for (Order o : orderService.findAllNewestFirst()) {
            String st = o.isAwaitingReview() ? "waiting" : (o.isConfirmed() ? "confirmed" : "rejected");
            if (!"all".equals(status) && !st.equals(status)) continue;
            String hay = ((o.getOrderCode() == null ? "" : o.getOrderCode()) + " " + nz(o.getReceiverName()) + " " + nz(o.getPhone()))
                    .toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && !hay.contains(key)) continue;
            // Ngày đặt hàng (bao gồm cả ngày đầu và ngày cuối)
            LocalDate day = o.getCreatedAt() == null ? null : o.getCreatedAt().toLocalDate();
            if (from != null && (day == null || day.isBefore(from))) continue;
            if (to != null && (day == null || day.isAfter(to))) continue;
            if (!"all".equals(payment) && !payment.equals(o.getPaymentMethod())) continue;
            if (!"all".equals(ship) && !(o.isConfirmed() && ship.equals(o.getShippingStatus()))) continue;
            count++;

            StringBuilder items = new StringBuilder();
            for (OrderItem it : o.getItems()) {
                if (items.length() > 0) items.append("; ");
                items.append(it.getProductName()).append(" x").append(it.getQuantity());
                if (it.getVariantText() != null && !it.getVariantText().isBlank()) items.append(" (").append(it.getVariantText()).append(')');
            }
            sb.append(csv(o.getOrderCode())).append(',')
              .append(csv(o.getCreatedAtText())).append(',')
              .append(csv(o.getUsername())).append(',')
              .append(csv(o.getReceiverName())).append(',')
              .append(phoneCell(o.getPhone())).append(',')
              .append(csv(o.getAddress())).append(',')
              .append(csv(o.getPaymentLabel())).append(',')
              .append(csv(o.getStatusLabel())).append(',')
              .append(csv(o.isConfirmed() ? o.getShippingLabel() : "")).append(',')
              .append(o.getItemsTotal()).append(',')
              .append(o.getDiscountAmount()).append(',')
              .append(csv(o.getVoucherCode())).append(',')
              .append(o.getShippingFee()).append(',')
              .append(o.getTotalAmount()).append(',')
              .append(o.getDepositAmount()).append(',')
              .append(o.getCodDue()).append(',')
              .append(csv(items.toString())).append(',')
              .append(csv(o.getReviewedByLabel())).append(',')
              .append(csv(o.getReviewedAtText())).append(',')
              .append(csv(o.getRejectReason())).append("\r\n");
        }
        String range = from == null && to == null ? LocalDate.now().toString()
                : (from == null ? "den-" + to : (to == null ? "tu-" + from : (from.equals(to) ? from.toString() : from + "_" + to)));
        String filename = "don-hang_" + range + ("all".equals(status) ? "" : "_" + status.replaceAll("[^a-z]", "")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String nz(String v) { return v == null ? "" : v; }

    /** Số điện thoại: ép Excel hiểu là chữ để không mất số 0 đầu (số đã được kiểm tra chỉ gồm chữ số / + / khoảng trắng) */
    private static String phoneCell(String phone) {
        String p = nz(phone).replaceAll("[^0-9+]", "");
        return p.isEmpty() ? "" : "=\"" + p + "\"";
    }

    /** Ô CSV: bọc nháy kép nếu cần, và chặn công thức Excel (=, +, -, @ ở đầu ô) */
    private static String csv(String v) {
        if (v == null) return "";
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ---------- Chi tiết 1 đơn (xem ảnh bill + xác nhận / từ chối) ----------
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model, RedirectAttributes ra) {
        Order order = orderService.findById(id);
        if (order == null) {
            ra.addFlashAttribute("orderError", "Không tìm thấy đơn hàng.");
            return "redirect:/dashboard/don-hang";
        }
        addStaffInfo(auth, model);
        model.addAttribute("order", order);
        // số lần khách này từng không nhận hàng -> cảnh báo nhân viên trước khi xác nhận / giao COD
        model.addAttribute("refuseCount", orderService.refusalHistory(order));
        // Tài khoản đã đăng nhập để đặt đơn (có thể null nếu tài khoản không còn trong hệ thống)
        model.addAttribute("account", userRepository.findByUsername(order.getUsername()).orElse(null));
        return "dashboard/don-hang-chi-tiet";
    }

    // ---------- Xác nhận ----------
    @PostMapping("/{id}/xac-nhan")
    public String confirm(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            Order o = orderService.confirm(id, auth.getName());
            orderNotifier.confirmed(o);
            ra.addFlashAttribute("orderMessage", "Đã xác nhận đơn " + o.getOrderCode() + ".");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("orderError", e.getMessage());
        }
        return "redirect:/dashboard/don-hang/" + id;
    }

    // ---------- Từ chối (bắt buộc ghi lý do, hoàn lại tồn kho) ----------
    @PostMapping("/{id}/tu-choi")
    public String reject(@PathVariable Long id, @RequestParam(defaultValue = "") String reason,
                         Authentication auth, RedirectAttributes ra) {
        try {
            Order o = orderService.reject(id, auth.getName(), reason);
            orderNotifier.rejected(o);
            ra.addFlashAttribute("orderMessage", "Đã từ chối đơn " + o.getOrderCode() + " và hoàn lại tồn kho.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("orderError", e.getMessage());
        }
        return "redirect:/dashboard/don-hang/" + id;
    }

    private void addStaffInfo(Authentication auth, Model model) {
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_NHANVIEN")
                .replace("ROLE_", "");
        model.addAttribute("username", auth.getName());
        model.addAttribute("role", role);
    }
}
