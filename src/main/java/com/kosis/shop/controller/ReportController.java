package com.kosis.shop.controller;

import com.kosis.shop.entity.Order;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.service.ProfitCalculator;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Báo cáo doanh thu theo khoảng thời gian (/dashboard/bao-cao) + xuất file CSV (mở được bằng Excel).
 * Doanh thu = tổng tiền các đơn ĐÃ XÁC NHẬN, tính theo ngày xác nhận (cùng cách tính với trang tổng quan).
 * Giá vốn / lợi nhuận gộp: xem {@link ProfitCalculator} - chỉ tính các đơn có đủ giá vốn đã chụp lúc bán
 * (cột order_items.unit_cost); đơn cũ chưa có giá vốn được đếm riêng và nêu rõ trên báo cáo.
 * Quyền: ADMIN và NHANVIEN (luật chung /dashboard/** ở SecurityConfig).
 */
@Controller
public class ReportController {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;
    private static final int TOP_PRODUCTS = 10;

    private final OrderRepository orderRepository;

    public ReportController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** Số liệu đã tính sẵn cho 1 khoảng thời gian */
    private record Report(LocalDate from, LocalDate to, String warning,
                          List<DayRow> days, List<ProductRow> products,
                          long orders, long revenue, long discount, long shipping,
                          long bankOrders, long codOrders,
                          long costedOrders, long costedNet, long cost, long uncostedRevenue) {
        long average() { return orders == 0 ? 0 : Math.round((double) revenue / orders); }
        long uncostedOrders() { return orders - costedOrders; }
        boolean hasProfit() { return costedOrders > 0; }
        /** Lợi nhuận gộp của các đơn có giá vốn */
        long profit() { return costedNet - cost; }
        /** Biên lợi nhuận gộp (%) trên tiền hàng sau giảm giá của các đơn có giá vốn */
        double margin() { return costedNet <= 0 ? 0 : profit() * 100.0 / costedNet; }
    }

    /** cost/profit chỉ gồm các đơn có giá vốn trong ngày (costedOrders = số đơn đó) */
    private record DayRow(LocalDate date, long orders, long revenue, long discount, long shipping,
                          long costedOrders, long cost, long profit) {}

    /** cost/profit/hasCost: chỉ gồm các dòng hàng có giá vốn; profit chưa trừ voucher */
    private record ProductRow(String name, String code, long quantity, long revenue,
                              long cost, long profit, boolean hasCost) {}

    @GetMapping("/dashboard/bao-cao")
    public String report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         Authentication authentication, Model model) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_NHANVIEN").replace("ROLE_", "");
        model.addAttribute("username", authentication.getName());
        model.addAttribute("role", role);

        Report r = build(from, to);
        model.addAttribute("from", r.from().toString());
        model.addAttribute("to", r.to().toString());
        model.addAttribute("warning", r.warning());
        model.addAttribute("totalRevenue", money(r.revenue()));
        model.addAttribute("totalOrders", r.orders());
        model.addAttribute("averageOrder", money(r.average()));
        model.addAttribute("totalDiscount", money(r.discount()));
        model.addAttribute("totalShipping", money(r.shipping()));
        model.addAttribute("hasProfit", r.hasProfit());
        model.addAttribute("totalCost", r.hasProfit() ? money(r.cost()) : NO_DATA);
        model.addAttribute("totalProfit", r.hasProfit() ? money(r.profit()) : NO_DATA);
        model.addAttribute("profitMargin", r.hasProfit() ? percent(r.margin()) : NO_DATA);
        model.addAttribute("profitNegative", r.hasProfit() && r.profit() < 0);
        model.addAttribute("uncostedOrders", r.uncostedOrders());
        model.addAttribute("uncostedRevenue", money(r.uncostedRevenue()));

        long max = r.days().stream().mapToLong(DayRow::revenue).max().orElse(0);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DayRow d : r.days()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.date().getDayOfMonth() + "/" + d.date().getMonthValue() + "/" + d.date().getYear());
            row.put("orders", d.orders());
            row.put("revenue", money(d.revenue()));
            row.put("discount", money(d.discount()));
            row.put("shipping", money(d.shipping()));
            row.put("cost", d.costedOrders() > 0 ? money(d.cost()) : NO_DATA);
            row.put("profit", d.costedOrders() > 0 ? money(d.profit()) : NO_DATA);
            row.put("profitNegative", d.costedOrders() > 0 && d.profit() < 0);
            row.put("pct", max == 0 ? 0 : Math.round(d.revenue() * 100.0 / max));
            rows.add(row);
        }
        model.addAttribute("rows", rows);

        List<Map<String, Object>> top = new ArrayList<>();
        for (ProductRow p : r.products()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", p.name());
            row.put("code", p.code());
            row.put("quantity", p.quantity());
            row.put("revenue", money(p.revenue()));
            row.put("profit", p.hasCost() ? money(p.profit()) : NO_DATA);
            row.put("profitNegative", p.hasCost() && p.profit() < 0);
            top.add(row);
        }
        model.addAttribute("topProducts", top);

        // ----- dữ liệu cho biểu đồ (Chart.js đọc ở bao-cao.html) -----
        List<String> labels = new ArrayList<>();
        List<Long> chartRevenue = new ArrayList<>();
        List<Long> chartOrders = new ArrayList<>();
        List<Long> chartProfit = new ArrayList<>();
        for (DayRow d : r.days()) {
            labels.add(d.date().getDayOfMonth() + "/" + d.date().getMonthValue());
            chartRevenue.add(d.revenue());
            chartOrders.add(d.orders());
            chartProfit.add(d.costedOrders() > 0 ? d.profit() : null);   // null = ngày không có dữ liệu giá vốn (đứt đoạn trên biểu đồ)
        }
        model.addAttribute("chartLabels", labels);
        model.addAttribute("chartRevenue", chartRevenue);
        model.addAttribute("chartOrders", chartOrders);
        model.addAttribute("chartProfit", chartProfit);
        model.addAttribute("productLabels", r.products().stream().map(ProductRow::name).toList());
        model.addAttribute("productRevenue", r.products().stream().map(ProductRow::revenue).toList());
        model.addAttribute("paymentCounts", List.of(r.codOrders(), r.bankOrders()));
        model.addAttribute("hasData", r.orders() > 0);
        return "dashboard/bao-cao";
    }

    @GetMapping("/dashboard/bao-cao/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Report r = build(from, to);
        StringBuilder sb = new StringBuilder("\uFEFF");   // BOM để Excel đọc đúng tiếng Việt
        sb.append("Báo cáo doanh thu,").append(r.from()).append(',').append(r.to()).append("\r\n\r\n");
        sb.append("Ngày,Số đơn,Doanh thu (VND),Giảm giá (VND),Phí vận chuyển (VND),Giá vốn (VND),Lợi nhuận gộp (VND)\r\n");
        for (DayRow d : r.days()) {
            sb.append(d.date()).append(',').append(d.orders()).append(',').append(d.revenue()).append(',')
              .append(d.discount()).append(',').append(d.shipping()).append(',')
              .append(d.costedOrders() > 0 ? String.valueOf(d.cost()) : "").append(',')
              .append(d.costedOrders() > 0 ? String.valueOf(d.profit()) : "").append("\r\n");
        }
        sb.append("Tổng,").append(r.orders()).append(',').append(r.revenue()).append(',')
          .append(r.discount()).append(',').append(r.shipping()).append(',')
          .append(r.hasProfit() ? String.valueOf(r.cost()) : "").append(',')
          .append(r.hasProfit() ? String.valueOf(r.profit()) : "").append("\r\n");
        sb.append("Ghi chú,Giá vốn và lợi nhuận chỉ tính ").append(r.costedOrders()).append('/').append(r.orders())
          .append(" đơn có giá vốn (lợi nhuận gộp = tiền hàng - giảm giá - giá vốn; không gồm phí vận chuyển)\r\n\r\n");
        sb.append("Sản phẩm bán chạy,Mã,Số lượng,Tiền hàng (VND),Giá vốn (VND),Lợi nhuận (VND; chưa trừ voucher)\r\n");
        for (ProductRow p : r.products()) {
            sb.append(csv(p.name())).append(',').append(csv(p.code())).append(',')
              .append(p.quantity()).append(',').append(p.revenue()).append(',')
              .append(p.hasCost() ? String.valueOf(p.cost()) : "").append(',')
              .append(p.hasCost() ? String.valueOf(p.profit()) : "").append("\r\n");
        }
        String filename = "doanh-thu_" + r.from() + "_" + r.to() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---------------- tính số liệu ----------------

    private Report build(LocalDate from, LocalDate to) {
        String warning = null;
        LocalDate today = LocalDate.now();
        if (to == null) to = today;
        if (from == null) from = to.minusDays(DEFAULT_DAYS - 1);
        if (from.isAfter(to)) {
            LocalDate tmp = from; from = to; to = tmp;
            warning = "Ngày bắt đầu lớn hơn ngày kết thúc nên hệ thống đã đổi chỗ hai ngày.";
        }
        if (from.plusDays(MAX_DAYS - 1).isBefore(to)) {
            from = to.minusDays(MAX_DAYS - 1);
            warning = "Chỉ xem được tối đa " + MAX_DAYS + " ngày mỗi lần; hệ thống đã lấy " + MAX_DAYS + " ngày kết thúc ở ngày " + to + ".";
        }

        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();

        // mọi ngày trong khoảng đều có dòng (ngày không có đơn = 0)
        // [số đơn, doanh thu, giảm giá, phí ship, số đơn có giá vốn, giá vốn, lợi nhuận gộp]
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new long[7]);
        }
        long orders = 0, revenue = 0, discount = 0, shipping = 0, bankOrders = 0, codOrders = 0;
        long costedOrders = 0, costedNet = 0, cost = 0, uncostedRevenue = 0;
        for (Order o : orderRepository.findConfirmedWithItemsBetween(Order.STATUS_CONFIRMED, start, end)) {
            long[] a = byDay.get(o.getReviewedAt().toLocalDate());
            if (a == null) continue;
            a[0]++;
            a[1] += o.getTotalAmount();
            a[2] += o.getDiscountAmount();
            a[3] += o.getShippingFee();
            orders++;
            if (o.isBank()) bankOrders++; else codOrders++;
            revenue += o.getTotalAmount();
            discount += o.getDiscountAmount();
            shipping += o.getShippingFee();

            ProfitCalculator.OrderProfit pr = ProfitCalculator.of(o);
            if (pr.costKnown()) {
                a[4]++;
                a[5] += pr.cost();
                a[6] += pr.profit();
                costedOrders++;
                costedNet += pr.netGoods();
                cost += pr.cost();
            } else {
                uncostedRevenue += o.getTotalAmount();
            }
        }
        List<DayRow> days = new ArrayList<>();
        byDay.forEach((d, a) -> days.add(new DayRow(d, a[0], a[1], a[2], a[3], a[4], a[5], a[6])));

        List<ProductRow> products = new ArrayList<>();
        for (Object[] row : orderRepository.topProductsBetween(Order.STATUS_CONFIRMED, start, end, PageRequest.of(0, TOP_PRODUCTS))) {
            long costOfKnown = row[4] == null ? 0 : ((Number) row[4]).longValue();
            long revenueOfKnown = row[5] == null ? 0 : ((Number) row[5]).longValue();
            products.add(new ProductRow((String) row[0], row[1] == null ? "" : (String) row[1],
                    ((Number) row[2]).longValue(), ((Number) row[3]).longValue(),
                    costOfKnown, revenueOfKnown - costOfKnown, revenueOfKnown > 0));
        }
        return new Report(from, to, warning, days, products, orders, revenue, discount, shipping, bankOrders, codOrders,
                costedOrders, costedNet, cost, uncostedRevenue);
    }

    private static final String NO_DATA = "—";

    private static String percent(double v) {
        return NumberFormat.getNumberInstance(Locale.GERMANY).format(Math.round(v * 10) / 10.0) + "%";
    }

    private static String money(long amount) {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(amount) + "₫";
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
}
