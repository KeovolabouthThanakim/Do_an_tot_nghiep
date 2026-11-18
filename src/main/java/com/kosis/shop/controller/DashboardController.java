package com.kosis.shop.controller;

import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.OrderItem;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trang quản trị (dashboard) - chỉ ADMIN và NHANVIEN vào được
 * (đã chặn ở SecurityConfig.java, requestMatchers("/dashboard/**")).
 * Số liệu ở trang tổng quan lấy THẬT từ database (đơn hàng, doanh thu, sản phẩm, khách hàng).
 */
@Controller
public class DashboardController {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public DashboardController(ProductRepository productRepository,
                               OrderRepository orderRepository,
                               UserRepository userRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_NHANVIEN")
                .replace("ROLE_", "");

        model.addAttribute("username", authentication.getName());
        model.addAttribute("role", role);

        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime yesterday = today.minusDays(1);

        // ----- Doanh thu hôm nay (đơn đã xác nhận) + so với hôm qua -----
        long revenueToday = orderRepository.sumRevenue(Order.STATUS_CONFIRMED, today, tomorrow);
        long revenueYesterday = orderRepository.sumRevenue(Order.STATUS_CONFIRMED, yesterday, today);
        model.addAttribute("revenueToday", money(revenueToday));
        model.addAttribute("revenueTrend", trend(revenueToday, revenueYesterday, "so với hôm qua"));

        // ----- Số đơn đặt hôm nay + so với hôm qua -----
        long ordersToday = orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(today, tomorrow);
        long ordersYesterday = orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(yesterday, today);
        model.addAttribute("ordersToday", ordersToday);
        model.addAttribute("ordersTrend", trend(ordersToday, ordersYesterday, "so với hôm qua"));

        model.addAttribute("productCount", productRepository.count());
        model.addAttribute("customerCount", userRepository.countByRole("USER"));

        // ----- 5 đơn hàng gần đây -----
        List<Map<String, Object>> recent = new ArrayList<>();
        for (Order o : orderRepository.findTop5ByOrderByCreatedAtDesc()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", o.getId());
            row.put("code", o.getOrderCode());
            row.put("customer", o.getReceiverName());
            row.put("product", productSummary(o));
            row.put("total", money(o.getTotalAmount()));
            row.put("status", o.isConfirmed() ? "done" : (o.isRejected() ? "rejected" : "review"));
            row.put("statusLabel", o.getStatusLabel());
            recent.add(row);
        }
        model.addAttribute("recentOrders", recent);

        // ----- Doanh thu + số đơn 7 ngày gần nhất (biểu đồ) -----
        List<String> weekLabels = new ArrayList<>();
        List<Long> weekRevenue = new ArrayList<>();
        List<Long> weekOrders = new ArrayList<>();
        long weekTotal = 0;
        for (int i = 6; i >= 0; i--) {
            LocalDateTime from = today.minusDays(i);
            LocalDateTime to = from.plusDays(1);
            long rev = orderRepository.sumRevenue(Order.STATUS_CONFIRMED, from, to);
            weekLabels.add(from.getDayOfMonth() + "/" + from.getMonthValue());
            weekRevenue.add(rev);
            weekOrders.add(orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to));
            weekTotal += rev;
        }
        model.addAttribute("weekLabels", weekLabels);
        model.addAttribute("weekRevenue", weekRevenue);
        model.addAttribute("weekOrders", weekOrders);
        model.addAttribute("weekTotal", money(weekTotal));

        // ----- Tỷ lệ trạng thái đơn (biểu đồ tròn): chờ / đã xác nhận / từ chối -----
        long waiting = orderRepository.countByStatusIn(List.of(Order.STATUS_PENDING, Order.STATUS_PAYMENT_REVIEW));
        long confirmed = orderRepository.countByStatusIn(List.of(Order.STATUS_CONFIRMED));
        long rejected = orderRepository.countByStatusIn(List.of(Order.STATUS_REJECTED));
        model.addAttribute("statusCounts", List.of(waiting, confirmed, rejected));
        model.addAttribute("statusTotal", waiting + confirmed + rejected);

        return "dashboard/index";
    }

    /** "Áo khoác kẻ caro" hoặc "Áo khoác kẻ caro +2 sản phẩm khác" */
    private static String productSummary(Order o) {
        List<OrderItem> items = o.getItems();
        if (items == null || items.isEmpty()) return "—";
        String first = items.get(0).getProductName();
        return items.size() > 1 ? first + " +" + (items.size() - 1) + " sản phẩm khác" : first;
    }

    /** 1.250.000 -> "1.250.000₫" (dấu chấm phân cách hàng nghìn như phần còn lại của web) */
    private static String money(long amount) {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(amount) + "₫";
    }

    /** Dòng xu hướng: {"dir":"up|down|flat", "text":"12% so với hôm qua"} */
    private static Map<String, String> trend(long now, long before, String suffix) {
        Map<String, String> t = new HashMap<>();
        if (before == 0) {
            t.put("dir", "flat");
            t.put("text", now == 0 ? "Chưa có số liệu hôm nay" : "Hôm qua chưa có");
        } else {
            long pct = Math.round((now - before) * 100.0 / before);
            t.put("dir", pct > 0 ? "up" : (pct < 0 ? "down" : "flat"));
            t.put("text", (pct == 0 ? "Bằng" : Math.abs(pct) + "%") + " " + suffix);
        }
        return t;
    }
}
