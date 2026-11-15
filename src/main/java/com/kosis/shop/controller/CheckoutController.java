package com.kosis.shop.controller;

import com.kosis.shop.cart.Cart;
import com.kosis.shop.cart.CartView;
import com.kosis.shop.config.BankProperties;
import com.kosis.shop.entity.Order;
import com.kosis.shop.repository.UserRepository;
import com.kosis.shop.service.CartService;
import com.kosis.shop.service.CheckoutForm;
import com.kosis.shop.service.OrderNotifier;
import com.kosis.shop.entity.Voucher;
import com.kosis.shop.service.OrderService;
import com.kosis.shop.service.PricingService;
import com.kosis.shop.service.SettingsService;
import com.kosis.shop.service.VoucherService;
import com.kosis.shop.service.SlipStorageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.regex.Pattern;

/**
 * Thanh toán: /thanh-toan (bắt buộc đăng nhập - cấu hình ở SecurityConfig).
 * Đặt hàng xong -> trừ tồn kho, lưu đơn vào bảng orders/order_items, xoá giỏ hàng.
 */
@Controller
public class CheckoutController {

    private static final Pattern PHONE = Pattern.compile("^(\\+?84|0)\\d{9,10}$");

    /** Thông tin bên bán in trên hóa đơn: chỉnh ở /dashboard/cai-dat (mặc định lấy từ application.properties / biến môi trường KOSIS_INVOICE_*) */
    private final SettingsService settings;

    private final CartService cartService;
    private final OrderService orderService;
    private final UserRepository userRepository;
    private final BankProperties bank;
    private final SlipStorageService slipStorage;
    private final OrderNotifier orderNotifier;
    private final PricingService pricing;
    private final VoucherService voucherService;

    public CheckoutController(CartService cartService, OrderService orderService, UserRepository userRepository,
                              BankProperties bank, SlipStorageService slipStorage, OrderNotifier orderNotifier,
                              PricingService pricing, VoucherService voucherService, SettingsService settings) {
        this.settings = settings;
        this.pricing = pricing;
        this.voucherService = voucherService;
        this.orderNotifier = orderNotifier;
        this.cartService = cartService;
        this.orderService = orderService;
        this.userRepository = userRepository;
        this.bank = bank;
        this.slipStorage = slipStorage;
    }

    // ---------- Trang thanh toán ----------
    @GetMapping("/thanh-toan")
    public String checkoutPage(Principal principal, HttpSession session, Model model, RedirectAttributes ra) {
        CartView cart = cartService.view(session);
        String redirect = guardCart(cart, ra);
        if (redirect != null) return redirect;

        // Điền sẵn thông tin từ tài khoản (nếu đã có)
        CheckoutForm form = new CheckoutForm();
        userRepository.findByUsername(principal.getName()).ifPresent(u -> {
            form.setReceiverName(u.getFullName());
            form.setPhone(u.getPhone());
            form.setAddress(u.getAddress());
        });
        return renderCheckout(model, cart, form, null, principal.getName());
    }

    // ---------- Áp dụng mã giảm giá (hiện lại trang thanh toán với số tiền mới) ----------
    @PostMapping("/thanh-toan/ap-dung")
    public String applyVoucher(@ModelAttribute("form") CheckoutForm form, Principal principal,
                               HttpSession session, Model model, RedirectAttributes ra) {
        CartView cart = cartService.view(session);
        String redirect = guardCart(cart, ra);
        if (redirect != null) return redirect;
        return renderCheckout(model, cart, form, null, principal.getName());
    }

    // ---------- Xác nhận đặt hàng ----------
    @PostMapping("/thanh-toan")
    public String placeOrder(@ModelAttribute("form") CheckoutForm form,
                             @RequestParam(value = "slipFile", required = false) MultipartFile slipFile,
                             Principal principal, HttpSession session, Model model, RedirectAttributes ra) {
        CartView cart = cartService.view(session);
        String redirect = guardCart(cart, ra);
        if (redirect != null) return redirect;

        String error = validate(form);
        if (error != null) {
            return renderCheckout(model, cart, form, error, principal.getName());
        }

        // Mã giảm giá không dùng được -> báo để khách sửa/bỏ mã, KHÔNG tự đặt đơn với giá khác dự kiến
        if (form.getVoucherCode() != null && !form.getVoucherCode().isBlank()) {
            try {
                voucherService.validate(form.getVoucherCode(), cart.getSubtotal());
            } catch (IllegalStateException e) {
                return renderCheckout(model, cart, form, e.getMessage()
                        + " Hãy sửa hoặc xoá mã giảm giá rồi đặt hàng lại" + (needsSlip(form) ? " (vui lòng chọn lại ảnh bill)." : "."),
                        principal.getName());
            }
        }

        // Chuyển khoản HOẶC COD có đặt cọc: BẮT BUỘC có ảnh bill (kiểm tra bằng nội dung file thật) trước khi tạo đơn
        String slipName = null;
        try {
            if (needsSlip(form)) {
                slipName = slipStorage.save(slipFile);
            }
        } catch (IllegalArgumentException e) {
            return renderCheckout(model, cart, form, e.getMessage() + " (vui lòng chọn lại ảnh bill)", principal.getName());
        } catch (IOException e) {
            return renderCheckout(model, cart, form, "Không lưu được ảnh bill, vui lòng thử lại.", principal.getName());
        }

        try {
            Order order = orderService.placeOrder(principal.getName(), form, Cart.of(session), slipName);
            cartService.clear(session);
            orderNotifier.placed(order);
            return "redirect:/thanh-toan/thanh-cong/" + order.getId();
        } catch (IllegalStateException e) {
            // Hết hàng / hết tồn kho giữa chừng -> bỏ ảnh bill vừa lưu, hiện lại trang thanh toán kèm lý do
            slipStorage.deleteQuietly(slipName);
            return renderCheckout(model, cartService.view(session), form,
                    e.getMessage() + (slipName != null ? " (vui lòng chọn lại ảnh bill)" : ""), principal.getName());
        } catch (RuntimeException e) {
            slipStorage.deleteQuietly(slipName);
            throw e;
        }
    }

    // ---------- Xem ảnh bill: chỉ chủ đơn hoặc nhân viên/admin ----------
    @GetMapping("/thanh-toan/slip/{id}")
    public ResponseEntity<Resource> slip(@PathVariable Long id, Authentication auth) {
        Order order = orderService.findById(id);
        if (order == null || !canView(order, auth)) {
            return ResponseEntity.notFound().build();
        }
        Path file = slipStorage.resolve(order.getSlipImage());
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(SlipStorageService.contentTypeOf(order.getSlipImage())))
                .header("Cache-Control", "private, max-age=3600")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    // ---------- Trang đặt hàng thành công ----------
    @GetMapping("/thanh-toan/thanh-cong/{id}")
    public String success(@PathVariable Long id, Authentication auth, Model model) {
        Order order = orderService.findById(id);
        if (order == null || !canView(order, auth)) {
            return "redirect:/";
        }
        model.addAttribute("order", order);
        model.addAttribute("popularSearches", HomeController.buildPopularSearches());
        model.addAttribute("activePage", "cart");
        return "checkout/success";
    }

    // ---------- Hóa đơn / biên nhận đơn hàng (chủ đơn hoặc nhân viên/admin, in được) ----------
    @GetMapping("/thanh-toan/hoa-don/{id}")
    public String receipt(@PathVariable Long id, Authentication auth, Model model) {
        Order order = orderService.findById(id);
        if (order == null || !canView(order, auth)) {
            return "redirect:/";
        }
        model.addAttribute("order", order);
        model.addAttribute("bank", bank);
        model.addAttribute("sellerName", settings.get(SettingsService.INVOICE_SELLER_NAME));
        model.addAttribute("sellerTaxCode", settings.get(SettingsService.INVOICE_TAX_CODE));
        model.addAttribute("sellerAddress", settings.sellerAddress());
        model.addAttribute("popularSearches", HomeController.buildPopularSearches());
        model.addAttribute("activePage", "orders");
        return "checkout/receipt";
    }

    // ==================== helper ====================

    private String renderCheckout(Model model, CartView cart, CheckoutForm form, String error, String username) {
        model.addAttribute("bank", bank);
        String memo = transferContent(username);
        PricingService.Quote quote = buildQuote(cart.getSubtotal(), form, model);
        model.addAttribute("quote", quote);
        model.addAttribute("transferContent", memo);
        model.addAttribute("qrPayload", bank.qrPayload(quote.getTotal(), memo));   // dữ liệu QR VietQR, vẽ ngay trên trình duyệt
        // Đặt cọc COD: số tiền cọc / còn lại + QR riêng cho số tiền cọc
        long deposit = pricing.depositFor(quote.getTotal());
        model.addAttribute("depositEnabled", deposit > 0);
        model.addAttribute("depositPercent", pricing.getCodDepositPercent());
        model.addAttribute("depositAmount", deposit);
        model.addAttribute("codRemaining", Math.max(0, quote.getTotal() - deposit));
        model.addAttribute("depositQrPayload", bank.qrPayload(deposit, memo));
        model.addAttribute("cart", cart);
        model.addAttribute("form", form);
        model.addAttribute("errorMessage", error);
        model.addAttribute("popularSearches", HomeController.buildPopularSearches());
        model.addAttribute("activePage", "cart");
        return "checkout/index";
    }

    /** Tính tiền theo mã giảm giá khách nhập (mã sai thì bỏ qua và báo lỗi riêng cho ô mã) */
    private PricingService.Quote buildQuote(long subtotal, CheckoutForm form, Model model) {
        long discount = 0;
        String applied = null;
        String code = form.getVoucherCode();
        if (code != null && !code.isBlank()) {
            try {
                Voucher v = voucherService.validate(code, subtotal);
                discount = v.discountFor(subtotal);
                applied = v.getCode();
                model.addAttribute("voucherMessage", "Đã áp dụng mã " + applied + (v.getDescription() == null ? "" : " – " + v.getDescription()));
            } catch (IllegalStateException e) {
                model.addAttribute("voucherError", e.getMessage());
            }
        }
        return pricing.quote(subtotal, discount, applied);
    }

    /** Nội dung chuyển khoản gợi ý: "KS" + tên tài khoản (chỉ chữ/số, tối đa 20 ký tự) để nhân viên đối chiếu */
    private String transferContent(String username) {
        String clean = username == null ? "" : username.replaceAll("[^A-Za-z0-9]", "");
        if (clean.length() > 20) clean = clean.substring(0, 20);
        return ("KS " + clean).trim();
    }

    /** Cần ảnh bill chuyển khoản? Chuyển khoản luôn cần; COD cần khi cửa hàng đang bắt đặt cọc */
    private boolean needsSlip(CheckoutForm form) {
        return Order.PAYMENT_BANK.equals(form.getPaymentMethod())
                || (Order.PAYMENT_COD.equals(form.getPaymentMethod()) && pricing.isCodDepositEnabled());
    }

    /** Giỏ trống hoặc còn hàng hết -> quay lại giỏ hàng; trả null nếu hợp lệ */
    private String guardCart(CartView cart, RedirectAttributes ra) {
        if (cart.isEmpty()) {
            ra.addFlashAttribute("cartError", "Giỏ hàng đang trống, hãy chọn sản phẩm trước khi thanh toán.");
            return "redirect:/gio-hang";
        }
        if (cart.isHasUnavailable() || cart.getTotalQuantity() == 0) {
            ra.addFlashAttribute("cartError", "Trong giỏ có sản phẩm đã hết hàng. Vui lòng xoá sản phẩm đó trước khi thanh toán.");
            return "redirect:/gio-hang";
        }
        return null;
    }

    private String validate(CheckoutForm f) {
        if (isBlank(f.getReceiverName()) || f.getReceiverName().trim().length() < 2) {
            return "Vui lòng nhập họ tên người nhận.";
        }
        String phone = f.getPhone() == null ? "" : f.getPhone().replaceAll("[\\s.-]", "");
        if (!PHONE.matcher(phone).matches()) {
            return "Số điện thoại không hợp lệ (VD: 0901234567).";
        }
        f.setPhone(phone);
        if (isBlank(f.getAddress()) || f.getAddress().trim().length() < 8) {
            return "Vui lòng nhập địa chỉ giao hàng đầy đủ (số nhà, đường, phường/xã, quận/huyện, tỉnh/thành).";
        }
        if (f.getAddress().trim().length() > 255) {
            return "Địa chỉ quá dài (tối đa 255 ký tự).";
        }
        if (f.getNote() != null && f.getNote().length() > 500) {
            return "Ghi chú quá dài (tối đa 500 ký tự).";
        }
        if (!Order.PAYMENT_COD.equals(f.getPaymentMethod()) && !Order.PAYMENT_BANK.equals(f.getPaymentMethod())) {
            return "Vui lòng chọn phương thức thanh toán.";
        }
        return null;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    /** Chỉ chủ đơn hoặc nhân viên/admin mới xem được trang đơn hàng */
    private boolean canView(Order order, Authentication auth) {
        if (auth == null) return false;
        if (order.getUsername().equals(auth.getName())) return true;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_NHANVIEN")) return true;
        }
        return false;
    }
}
