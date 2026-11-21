package com.kosis.shop.controller;

import com.kosis.shop.config.I18n;
import com.kosis.shop.entity.Order;
import com.kosis.shop.service.OrderNotifier;
import com.kosis.shop.entity.ReturnRequest;
import com.kosis.shop.service.OrderService;
import com.kosis.shop.service.ReturnService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;

/**
 * "Đơn hàng của tôi": /don-hang-cua-toi (bắt buộc đăng nhập - cấu hình ở SecurityConfig).
 * Chỉ lấy đơn theo username đang đăng nhập nên khách không thể xem đơn của người khác.
 */
@Controller
public class MyOrdersController {

    private final OrderService orderService;
    private final OrderNotifier orderNotifier;
    private final ReturnService returnService;

    public MyOrdersController(OrderService orderService, OrderNotifier orderNotifier, ReturnService returnService) {
        this.returnService = returnService;
        this.orderNotifier = orderNotifier;
        this.orderService = orderService;
    }

    /** tab: all | waiting (chờ xử lý) | confirmed | rejected (bị từ chối hoặc khách đã huỷ) */
    @GetMapping("/don-hang-cua-toi")
    public String myOrders(@RequestParam(value = "tab", defaultValue = "all") String tab,
                           Principal principal, Model model) {
        List<Order> all = orderService.findByUsernameNewestFirst(principal.getName());

        long waiting = all.stream().filter(Order::isAwaitingReview).count();
        long confirmed = all.stream().filter(Order::isConfirmed).count();
        long rejected = all.stream().filter(Order::isRejected).count();

        if (!List.of("waiting", "confirmed", "rejected").contains(tab)) {
            tab = "all";
        }
        final String t = tab;
        List<Order> shown = all.stream().filter(o -> switch (t) {
            case "waiting" -> o.isAwaitingReview();
            case "confirmed" -> o.isConfirmed();
            case "rejected" -> o.isRejected();
            default -> true;
        })
        // Đơn đang giao (đang vận chuyển / đã tới nơi chờ xác nhận) lên đầu; sorted() ổn định nên trong mỗi nhóm vẫn mới nhất trước
        .sorted(Comparator.comparingInt((Order o) -> (o.isShipping() || o.isArrived()) ? 0 : 1))
        .toList();

        model.addAttribute("orders", shown);
        model.addAttribute("autoDays", orderService.getAutoCompleteDays());
        model.addAttribute("returnDays", returnService.getWindowDays());
        model.addAttribute("returns", returnService.byOrderForUser(principal.getName()));
        model.addAttribute("tab", tab);
        model.addAttribute("countAll", all.size());
        model.addAttribute("countWaiting", waiting);
        model.addAttribute("countConfirmed", confirmed);
        model.addAttribute("countRejected", rejected);
        model.addAttribute("popularSearches", HomeController.buildPopularSearches());
        model.addAttribute("activePage", "orders");
        return "orders/index";
    }

    /** Khách tự huỷ đơn còn chờ xác nhận - chỉ huỷ được đơn của chính mình (hoàn lại tồn kho) */
    @PostMapping("/don-hang-cua-toi/{id}/huy")
    public String cancel(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            Order o = orderService.cancelByCustomer(id, principal.getName());
            orderNotifier.cancelled(o);
            ra.addFlashAttribute("orderMessage", I18n.t("orders.cancelled.msg", "Đơn {0} đã được huỷ.", o.getOrderCode()));
            return "redirect:/don-hang-cua-toi?tab=rejected";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("orderError", e.getMessage());
            return "redirect:/don-hang-cua-toi";
        }
    }

    /** Khách bấm "Tôi đã nhận được hàng" - chỉ xác nhận được đơn của chính mình */
    @PostMapping("/don-hang-cua-toi/{id}/da-nhan-hang")
    public String confirmReceived(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            Order o = orderService.confirmReceivedByCustomer(id, principal.getName());
            ra.addFlashAttribute("orderMessage", I18n.t("orders.received.thanks", "Cảm ơn bạn! Đơn {0} đã được xác nhận là đã nhận hàng. Bạn có thể bấm \"Đánh giá sản phẩm\" ở từng món để chấm sao.", o.getOrderCode()));
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("orderError", e.getMessage());
        }
        return "redirect:/don-hang-cua-toi?tab=confirmed";
    }

    /** Khách gửi yêu cầu trả hàng cho đơn đã giao (trong hạn đổi trả) */
    @PostMapping("/don-hang-cua-toi/{id}/tra-hang")
    public String requestReturn(@PathVariable Long id, @RequestParam(defaultValue = "") String reason,
                                Principal principal, RedirectAttributes ra) {
        try {
            ReturnRequest r = returnService.request(id, principal.getName(), reason);
            Order o = orderService.findById(id);
            orderNotifier.custom(o, I18n.t("orders.return.mail.subject", "KOSIS - Đã nhận yêu cầu trả hàng đơn {0}", o.getOrderCode()),
                    I18n.t("orders.return.mail.body", "Chúng tôi đã nhận yêu cầu trả hàng của bạn.\nLý do: {0}\nNhân viên sẽ xem xét và phản hồi bạn sớm. Bạn đừng gửi hàng về trước khi yêu cầu được duyệt.", r.getReason()));
            ra.addFlashAttribute("orderMessage", I18n.t("orders.return.sent", "Đã gửi yêu cầu trả hàng cho đơn {0}. Cửa hàng sẽ phản hồi sớm.", r.getOrderCode()));
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("orderError", e.getMessage());
        }
        return "redirect:/don-hang-cua-toi?tab=confirmed";
    }
}