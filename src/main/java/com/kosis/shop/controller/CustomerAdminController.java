package com.kosis.shop.controller;

import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.User;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.repository.UserRepository;
import com.kosis.shop.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quản lý khách hàng trong dashboard: /dashboard/khach-hang
 *  - ADMIN và NHANVIEN: xem danh sách, tìm kiếm, lọc, xem hồ sơ + lịch sử đơn hàng.
 *  - Chỉ ADMIN: khoá / mở khoá tài khoản (chặn ở SecurityConfig, ngoài ra còn kiểm tra lại ở đây).
 *  - KHÔNG có nút xoá: dùng "khoá tài khoản" thay thế (xoá user sẽ làm username bị đăng ký lại
 *    và người mới thấy được lịch sử đơn của người cũ vì đơn hàng chỉ lưu username dạng chữ).
 */
@Controller
@RequestMapping("/dashboard/khach-hang")
public class CustomerAdminController {

    private static final String CUSTOMER_ROLE = "USER";

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final com.kosis.shop.repository.WishlistItemRepository wishlistRepository;
    private final com.kosis.shop.repository.ProductRepository productRepository;

    public CustomerAdminController(UserRepository userRepository, OrderRepository orderRepository, OrderService orderService,
                                   com.kosis.shop.repository.WishlistItemRepository wishlistRepository,
                                   com.kosis.shop.repository.ProductRepository productRepository) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    // ---------- Danh sách khách hàng ----------
    @GetMapping
    public String list(Authentication auth, Model model) {
        // username -> [số đơn, tổng tiền đã xác nhận]
        Map<String, long[]> stats = new HashMap<>();
        for (Object[] row : orderRepository.customerOrderStats(Order.STATUS_CONFIRMED)) {
            stats.put((String) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }

        // username -> số đơn khách KHÔNG NHẬN hàng
        Map<String, Long> refused = new HashMap<>();
        for (Object[] row : orderRepository.refusedCountsByUser(Order.SHIP_REFUSED)) {
            refused.put((String) row[0], ((Number) row[1]).longValue());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        long refusers = 0;
        long active = 0;
        long locked = 0;
        long maxSpent = 0;
        long totalRevenue = 0;
        for (User u : userRepository.findByRoleOrderByIdDesc(CUSTOMER_ROLE)) {
            long[] st = stats.getOrDefault(u.getUsername(), new long[]{0, 0});
            Map<String, Object> r = new HashMap<>();
            r.put("user", u);
            r.put("orderCount", st[0]);
            r.put("totalSpent", st[1]);
            maxSpent = Math.max(maxSpent, st[1]);
            totalRevenue += st[1];
            long rf = refused.getOrDefault(u.getUsername(), 0L);
            r.put("refusedCount", rf);
            if (rf > 0) refusers++;
            rows.add(r);
            if (u.isEnabled()) active++; else locked++;
        }

        addStaffInfo(auth, model);
        model.addAttribute("customers", rows);
        model.addAttribute("totalCount", rows.size());
        model.addAttribute("activeCount", active);
        model.addAttribute("lockedCount", locked);
        model.addAttribute("refuserCount", refusers);
        model.addAttribute("maxSpent", maxSpent);
        model.addAttribute("totalRevenue", totalRevenue);
        return "dashboard/khach-hang";
    }

    // ---------- Hồ sơ 1 khách + lịch sử đơn ----------
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model, RedirectAttributes ra) {
        User u = findCustomer(id);
        if (u == null) {
            ra.addFlashAttribute("customerError", "Không tìm thấy khách hàng.");
            return "redirect:/dashboard/khach-hang";
        }
        List<Order> orders = orderService.findByUsernameNewestFirst(u.getUsername());
        long spent = orders.stream().filter(Order::isConfirmed).mapToLong(Order::getTotalAmount).sum();

        addStaffInfo(auth, model);
        model.addAttribute("customer", u);
        model.addAttribute("orders", orders);
        model.addAttribute("orderCount", orders.size());
        model.addAttribute("totalSpent", spent);
        model.addAttribute("refusedCount", orders.stream().filter(Order::isRefused).count());
        // sản phẩm khách đã lưu vào danh sách yêu thích (dữ liệu thị hiếu cho nhân viên)
        List<Long> wishIds = wishlistRepository.findByUsernameOrderByCreatedAtDesc(u.getUsername()).stream()
                .map(com.kosis.shop.entity.WishlistItem::getProductId).toList();
        model.addAttribute("wishlist", productRepository.findAllById(wishIds));
        return "dashboard/khach-hang-chi-tiet";
    }

    // ---------- Khoá / mở khoá (chỉ ADMIN) ----------
    @PostMapping("/{id}/khoa")
    public String lock(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return setEnabled(id, false, auth, ra);
    }

    @PostMapping("/{id}/mo-khoa")
    public String unlock(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return setEnabled(id, true, auth, ra);
    }

    private String setEnabled(Long id, boolean enabled, Authentication auth, RedirectAttributes ra) {
        // phòng thủ thêm: SecurityConfig đã chặn, nhưng kiểm tra lại cho chắc
        if (!isAdmin(auth)) {
            ra.addFlashAttribute("customerError", "Chỉ ADMIN mới được khoá / mở khoá tài khoản khách hàng.");
            return "redirect:/dashboard/khach-hang/" + id;
        }
        User u = findCustomer(id);
        if (u == null) {
            ra.addFlashAttribute("customerError", "Không tìm thấy khách hàng.");
            return "redirect:/dashboard/khach-hang";
        }
        u.setEnabled(enabled);
        userRepository.save(u);
        ra.addFlashAttribute("customerMessage", (enabled ? "Đã mở khoá" : "Đã khoá") + " tài khoản \"" + u.getUsername() + "\".");
        return "redirect:/dashboard/khach-hang/" + id;
    }

    // ==================== helper ====================

    /** Chỉ lấy tài khoản có role USER - trang này không đụng tới tài khoản nhân viên/admin */
    private User findCustomer(Long id) {
        return userRepository.findById(id).filter(u -> CUSTOMER_ROLE.equals(u.getRole())).orElse(null);
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
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
