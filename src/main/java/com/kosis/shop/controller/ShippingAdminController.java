package com.kosis.shop.controller;

import com.kosis.shop.entity.Carrier;
import com.kosis.shop.entity.Order;
import com.kosis.shop.service.OrderNotifier;
import com.kosis.shop.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Giao hàng trong dashboard (ADMIN + NHANVIEN - đã chặn ở SecurityConfig: /dashboard/**).
 * Danh sách các đơn ĐÃ XÁC NHẬN theo tình trạng giao; chọn đơn vị vận chuyển, nhập mã vận đơn
 * và cập nhật đã giao / giao không thành công ở trang riêng /dashboard/giao-hang/{id}.
 */
@Controller
@RequestMapping("/dashboard/giao-hang")
public class ShippingAdminController {

    private final OrderService orderService;
    private final OrderNotifier orderNotifier;

    public ShippingAdminController(OrderService orderService, OrderNotifier orderNotifier) {
        this.orderNotifier = orderNotifier;
        this.orderService = orderService;
    }

    @GetMapping
    public String list(Authentication auth, Model model) {
        List<Order> orders = orderService.findAllNewestFirst().stream().filter(Order::isConfirmed).toList();
        model.addAttribute("orders", orders);
        model.addAttribute("preparingCount", orders.stream().filter(Order::isPreparing).count());
        model.addAttribute("shippingCount", orders.stream().filter(Order::isShipping).count());
        model.addAttribute("arrivedCount", orders.stream().filter(Order::isArrived).count());
        model.addAttribute("deliveredCount", orders.stream().filter(Order::isDelivered).count());
        model.addAttribute("returnedCount", orders.stream().filter(Order::isReturned).count());
        model.addAttribute("refusedCount", orders.stream().filter(Order::isRefused).count());
        // đơn -> số lần khách này từng không nhận hàng (để cảnh báo trước khi giao COD)
        model.addAttribute("refuseMap", orderService.refusalHistoryFor(orders));
        model.addAttribute("autoDays", orderService.getAutoCompleteDays());
        model.addAttribute("totalCount", orders.size());
        addStaffInfo(auth, model);
        return "dashboard/giao-hang";
    }

    /** Trang giao hàng riêng của 1 đơn: tiến trình, chọn đơn vị vận chuyển, cập nhật kết quả */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model, RedirectAttributes ra) {
        Order order = orderService.findById(id);
        if (order == null) {
            ra.addFlashAttribute("orderError", "Không tìm thấy đơn hàng.");
            return "redirect:/dashboard/giao-hang";
        }
        if (!order.isConfirmed()) {
            ra.addFlashAttribute("orderError", "Đơn " + order.getOrderCode() + " chưa được xác nhận nên chưa giao hàng được.");
            return "redirect:/dashboard/giao-hang";
        }
        addStaffInfo(auth, model);
        model.addAttribute("order", order);
        model.addAttribute("carriers", Carrier.values());
        model.addAttribute("refuseCount", orderService.refusalHistory(order));
        model.addAttribute("autoDays", orderService.getAutoCompleteDays());
        return "dashboard/giao-hang-chi-tiet";
    }

    /** Giao cho đơn vị vận chuyển / đổi hãng / đổi mã vận đơn */
    @PostMapping("/{id}/van-chuyen")
    public String ship(@PathVariable Long id,
                       @RequestParam(defaultValue = "") String carrier,
                       @RequestParam(defaultValue = "") String trackingCode,
                       @RequestParam(defaultValue = "") String note,
                       Authentication auth, RedirectAttributes ra) {
        try {
            boolean firstShipment = orderService.findById(id) != null && !orderService.findById(id).isShipping();
            Order o = orderService.ship(id, carrier, trackingCode, note, auth.getName());
            if (firstShipment) orderNotifier.shipped(o);   // chỉ báo khi bắt đầu giao, không báo lại mỗi lần sửa mã vận đơn
            ra.addFlashAttribute("orderMessage", "Đơn " + o.getOrderCode() + " đã giao cho " + o.getCarrierLabel() + ".");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("orderError", e.getMessage());
        }
        return back(id);
    }

    /** Vận chuyển báo đã giao tới khách -> chờ khách xác nhận (quá hạn tự hoàn tất) */
    @PostMapping("/{id}/da-toi-noi")
    public String arrived(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            Order o = orderService.markArrived(id, auth.getName());
            ra.addFlashAttribute("orderMessage", "Đơn " + o.getOrderCode() + " đã tới khách. Đang chờ khách xác nhận (tự hoàn tất sau "
                    + orderService.getAutoCompleteDays() + " ngày nếu khách không phản hồi).");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("orderError", e.getMessage());
        }
        return back(id);
    }

    @PostMapping("/{id}/da-giao")
    public String delivered(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            Order o = orderService.markDelivered(id, auth.getName());
            ra.addFlashAttribute("orderMessage", "Đơn " + o.getOrderCode() + " đã giao thành công.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("orderError", e.getMessage());
        }
        return back(id);
    }

    /** Giao không thành công. type = REFUSED (khách không nhận hàng) | RETURNED (lý do khác) */
    @PostMapping("/{id}/hoan-hang")
    public String failed(@PathVariable Long id,
                         @RequestParam(defaultValue = "REFUSED") String type,
                         @RequestParam(defaultValue = "") String reason,
                         Authentication auth, RedirectAttributes ra) {
        try {
            boolean refused = !"RETURNED".equalsIgnoreCase(type);
            Order o = orderService.markFailed(id, refused, reason, auth.getName());
            ra.addFlashAttribute("orderMessage", "Đã ghi nhận đơn " + o.getOrderCode()
                    + (refused ? ": khách không nhận hàng." : " giao không thành công."));
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("orderError", e.getMessage());
        }
        return back(id);
    }

    private static String back(Long id) {
        return "redirect:/dashboard/giao-hang/" + id;
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
