package com.kosis.shop.controller;

import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.ReturnRequest;
import com.kosis.shop.service.OrderNotifier;
import com.kosis.shop.service.OrderService;
import com.kosis.shop.service.ReturnService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Xử lý yêu cầu trả hàng / hoàn tiền trong dashboard - chỉ ADMIN và NHANVIEN
 * (đã chặn ở SecurityConfig: requestMatchers("/dashboard/**")).
 */
@Controller
@RequestMapping("/dashboard/tra-hang")
public class ReturnAdminController {

    private final ReturnService returnService;
    private final OrderService orderService;
    private final OrderNotifier orderNotifier;

    public ReturnAdminController(ReturnService returnService, OrderService orderService, OrderNotifier orderNotifier) {
        this.returnService = returnService;
        this.orderService = orderService;
        this.orderNotifier = orderNotifier;
    }

    @GetMapping
    public String list(Authentication auth, Model model) {
        List<ReturnRequest> all = returnService.findAllNewestFirst();
        model.addAttribute("returns", all);
        model.addAttribute("openCount", all.stream().filter(ReturnRequest::isOpen).count());
        model.addAttribute("requestedCount", all.stream().filter(ReturnRequest::isRequested).count());
        model.addAttribute("approvedCount", all.stream().filter(ReturnRequest::isApproved).count());
        model.addAttribute("refundedCount", all.stream().filter(ReturnRequest::isRefunded).count());
        model.addAttribute("rejectedCount", all.stream().filter(ReturnRequest::isRejected).count());
        model.addAttribute("refundedTotal", all.stream().filter(ReturnRequest::isRefunded)
                .mapToLong(ReturnRequest::getRefundAmount).sum());
        model.addAttribute("windowDays", returnService.getWindowDays());
        model.addAttribute("username", auth.getName());
        model.addAttribute("role", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_NHANVIEN").replace("ROLE_", ""));
        return "dashboard/tra-hang";
    }

    @PostMapping("/{id}/duyet")
    public String approve(@PathVariable Long id, @RequestParam(defaultValue = "") String note,
                          Authentication auth, RedirectAttributes ra) {
        try {
            ReturnRequest r = returnService.approve(id, auth.getName(), note);
            tell(r, "KOSIS - Yêu cầu trả hàng đơn " + r.getOrderCode() + " đã được duyệt",
                    "Yêu cầu trả hàng của bạn đã được duyệt. Vui lòng gửi hàng về cửa hàng (còn nguyên tem mác, chưa sử dụng)."
                            + (r.getStaffNote() == null ? "" : "\nHướng dẫn: " + r.getStaffNote())
                            + "\nSau khi nhận lại hàng, chúng tôi sẽ hoàn tiền " + money(r.getRefundAmount()) + " cho bạn.");
            ra.addFlashAttribute("returnMessage", "Đã duyệt yêu cầu trả hàng đơn " + r.getOrderCode() + ".");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("returnError", e.getMessage());
        }
        return "redirect:/dashboard/tra-hang";
    }

    @PostMapping("/{id}/tu-choi")
    public String reject(@PathVariable Long id, @RequestParam(defaultValue = "") String note,
                         Authentication auth, RedirectAttributes ra) {
        try {
            ReturnRequest r = returnService.reject(id, auth.getName(), note);
            tell(r, "KOSIS - Yêu cầu trả hàng đơn " + r.getOrderCode() + " không được duyệt",
                    "Rất tiếc, yêu cầu trả hàng của bạn chưa thể được chấp nhận.\nLý do: " + r.getStaffNote()
                            + "\nNếu cần hỗ trợ thêm, vui lòng liên hệ cửa hàng.");
            ra.addFlashAttribute("returnMessage", "Đã từ chối yêu cầu trả hàng đơn " + r.getOrderCode() + ".");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("returnError", e.getMessage());
        }
        return "redirect:/dashboard/tra-hang";
    }

    @PostMapping("/{id}/hoan-tien")
    public String refund(@PathVariable Long id, @RequestParam(defaultValue = "") String note,
                         @RequestParam(defaultValue = "false") boolean restock,
                         Authentication auth, RedirectAttributes ra) {
        try {
            ReturnRequest r = returnService.refund(id, auth.getName(), note, restock);
            tell(r, "KOSIS - Đã hoàn tiền đơn " + r.getOrderCode(),
                    "Chúng tôi đã nhận lại hàng và hoàn " + money(r.getRefundAmount()) + " cho bạn."
                            + (r.getStaffNote() == null ? "" : "\nGhi chú: " + r.getStaffNote())
                            + "\nTiền sẽ về phương thức thanh toán ban đầu trong 5–7 ngày làm việc.");
            ra.addFlashAttribute("returnMessage", "Đã ghi nhận hoàn tiền cho đơn " + r.getOrderCode() + (restock ? " và cộng lại tồn kho." : "."));
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("returnError", e.getMessage());
        }
        return "redirect:/dashboard/tra-hang";
    }

    private void tell(ReturnRequest r, String subject, String intro) {
        Order o = orderService.findById(r.getOrderId());
        if (o != null) orderNotifier.custom(o, subject, intro);
    }

    private static String money(long v) {
        return String.format(java.util.Locale.GERMANY, "%,d", v) + " ₫";
    }
}
