package com.kosis.shop.controller;

import com.kosis.shop.config.I18n;
import com.kosis.shop.cart.Cart;
import com.kosis.shop.cart.CartView;
import com.kosis.shop.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.Map;

/**
 * Giỏ hàng: xem, thêm, đổi số lượng, xoá.
 * Ai cũng dùng được (không cần đăng nhập) - chỉ bước thanh toán mới yêu cầu đăng nhập.
 */
@Controller
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // ---------- Trang giỏ hàng ----------
    @GetMapping("/gio-hang")
    public String cartPage(HttpSession session, Model model) {
        CartView cart = cartService.view(session);
        model.addAttribute("cart", cart);
        model.addAttribute("popularSearches", HomeController.buildPopularSearches());
        model.addAttribute("activePage", "cart");
        return "cart/index";
    }

    // ---------- Thêm vào giỏ: gọi bằng JS (fetch) -> trả JSON, không tải lại trang ----------
    @PostMapping(value = "/gio-hang/them/{id}", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addAjax(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "1") int quantity,
                                                        @RequestParam(defaultValue = "") String size,
                                                        @RequestParam(defaultValue = "") String color,
                                                        HttpSession session) {
        try {
            int count = cartService.add(session, id, size, color, quantity);
            return ResponseEntity.ok(Map.of("ok", true, "count", count, "message", I18n.t("cart.added", "Đã thêm vào giỏ hàng")));
        } catch (CartService.SizeRequiredException e) {
            // Chưa chọn size -> báo lỗi + gửi kèm đường dẫn để JS chuyển khách sang trang chi tiết chọn size
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage(),
                    "redirect", "/san-pham/" + id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    // ---------- Thêm vào giỏ: form thường (nút "Mua ngay" hoặc khi JS không chạy) ----------
    @PostMapping("/gio-hang/them/{id}")
    public String add(@PathVariable Long id,
                      @RequestParam(defaultValue = "1") int quantity,
                      @RequestParam(defaultValue = "") String size,
                      @RequestParam(defaultValue = "") String color,
                      @RequestParam(defaultValue = "false") boolean buyNow,
                      HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        try {
            if (buyNow) {
                // "Mua ngay": chỉ đảm bảo sản phẩm có trong giỏ, KHÔNG cộng dồn thêm lần nữa
                cartService.buyNow(session, id, size, color, quantity);
                return "redirect:/gio-hang";
            }
            cartService.add(session, id, size, color, quantity);
            ra.addFlashAttribute("cartMessage", I18n.t("cart.added", "Đã thêm vào giỏ hàng"));
        } catch (CartService.SizeRequiredException e) {
            ra.addFlashAttribute("cartError", e.getMessage());
            return "redirect:/san-pham/" + id;      // sang trang chi tiết để chọn size
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("cartError", e.getMessage());
            if (buyNow) {
                return "redirect:/gio-hang";
            }
        }
        return "redirect:" + backPath(request);
    }

    // ---------- Đổi số lượng (nút − / +) ----------
    @PostMapping("/gio-hang/cap-nhat/{id}")
    public String update(@PathVariable Long id, @RequestParam int quantity,
                         @RequestParam(defaultValue = "") String size,
                         @RequestParam(defaultValue = "") String color,
                         HttpSession session, RedirectAttributes ra) {
        cartService.setQuantity(session, id, size, color, quantity);
        int now = Cart.of(session).quantityOf(id, size.trim().toUpperCase(), color.trim());
        if (quantity <= 0) {
            ra.addFlashAttribute("cartMessage", I18n.t("cart.removed", "Đã xoá sản phẩm khỏi giỏ hàng."));
        } else if (now < quantity) {
            // số lượng bị giới hạn theo tồn kho -> báo để khách không tưởng là bấm không ăn
            ra.addFlashAttribute("cartNotice", now > 0
                    ? I18n.t("cart.limited", "Chỉ còn {0} sản phẩm có thể đặt, số lượng đã được điều chỉnh thành {0}.", now)
                    : I18n.t("cart.not.enough", "Sản phẩm này hiện không còn đủ hàng."));
        }
        return "redirect:/gio-hang";
    }

    // ---------- Đổi size của 1 dòng ----------
    @PostMapping("/gio-hang/doi-size/{id}")
    public String changeSize(@PathVariable Long id,
                             @RequestParam(defaultValue = "") String size,
                             @RequestParam(defaultValue = "") String color,
                             @RequestParam String newSize,
                             HttpSession session, RedirectAttributes ra) {
        String sz = newSize.trim().toUpperCase();
        int before = Cart.of(session).quantityOf(id, sz, color.trim());
        cartService.changeSize(session, id, size, color, newSize);
        int after = Cart.of(session).quantityOf(id, sz, color.trim());
        if (!size.equals(newSize)) {
            if (after > before) ra.addFlashAttribute("cartMessage", I18n.t("cart.size.changed", "Đã đổi sang size {0}.", newSize));
            else ra.addFlashAttribute("cartError", I18n.t("cart.size.unavailable", "Không thể đổi sang size {0} (size này đã hết hàng hoặc không có).", newSize));
        }
        return "redirect:/gio-hang";
    }

    // ---------- Xoá 1 sản phẩm / xoá hết ----------
    @PostMapping("/gio-hang/xoa/{id}")
    public String remove(@PathVariable Long id,
                         @RequestParam(defaultValue = "") String size,
                         @RequestParam(defaultValue = "") String color,
                         HttpSession session, RedirectAttributes ra) {
        cartService.remove(session, id, size, color);
        ra.addFlashAttribute("cartMessage", I18n.t("cart.removed", "Đã xoá sản phẩm khỏi giỏ hàng."));
        return "redirect:/gio-hang";
    }

    @PostMapping("/gio-hang/xoa-het")
    public String clear(HttpSession session, RedirectAttributes ra) {
        boolean had = !Cart.of(session).isEmpty();
        cartService.clear(session);
        if (had) ra.addFlashAttribute("cartMessage", I18n.t("cart.cleared", "Đã xoá toàn bộ sản phẩm trong giỏ hàng."));
        return "redirect:/gio-hang";
    }

    /** Quay lại trang khách đang xem (chỉ lấy phần đường dẫn nội bộ, không redirect ra web ngoài) */
    private String backPath(HttpServletRequest request) {
        String ref = request.getHeader("Referer");
        if (ref != null) {
            try {
                URI u = URI.create(ref);
                String path = u.getRawPath();
                if (path != null && path.startsWith("/") && !path.startsWith("//")) {
                    return u.getRawQuery() == null ? path : path + "?" + u.getRawQuery();
                }
            } catch (IllegalArgumentException ignored) {
                // Referer lỗi -> về trang chủ
            }
        }
        return "/";
    }
}
