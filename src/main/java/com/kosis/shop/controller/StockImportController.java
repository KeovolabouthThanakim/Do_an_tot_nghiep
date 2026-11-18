package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.StockImport;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.StockImportRepository;
import com.kosis.shop.service.CategoryService;
import com.kosis.shop.service.StockImportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Trang "Nhập hàng" trong dashboard: ghi lại mỗi lần nhập hàng (nguồn hàng, ngày, sản phẩm, số lượng, GIÁ NHẬP)
 * và tự cộng vào tồn kho. Xem lịch sử + tổng tiền theo từng nguồn hàng.
 *
 * Đã được bảo vệ sẵn ở SecurityConfig (/dashboard/** chỉ cho ADMIN / NHANVIEN).
 */
@Controller
@RequestMapping("/dashboard/nhap-hang")
public class StockImportController {

    private final StockImportRepository importRepository;
    private final ProductRepository productRepository;
    private final StockImportService importService;
    private final CategoryService categoryService;

    public StockImportController(StockImportRepository importRepository, ProductRepository productRepository,
                                 StockImportService importService, CategoryService categoryService) {
        this.categoryService = categoryService;
        this.importRepository = importRepository;
        this.productRepository = productRepository;
        this.importService = importService;
    }

    // ---------- Thông tin người đăng nhập cho sidebar ----------
    @ModelAttribute("username")
    public String currentUsername(Authentication auth) {
        return auth == null ? "" : auth.getName();
    }

    @ModelAttribute("role")
    public String currentRole(Authentication auth) {
        if (auth == null) return "";
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_NHANVIEN")
                .replace("ROLE_", "");
    }

    /** Danh sách loại sản phẩm cho ô chọn trong popup "+ Sản phẩm mới" */
    @ModelAttribute("categories")
    public List<String> categories() {
        return categoryService.names();
    }

    /** Tổng hợp theo từng nguồn hàng (một dòng của bảng "Theo nguồn hàng"). */
    public record SupplierSummary(String name, String contact, int importCount, long totalQty, long totalCost,
                                  LocalDate lastDate) {
    }

    // ---------- Lịch sử nhập hàng ----------
    @GetMapping
    public String list(Model model) {
        List<StockImport> imports = importRepository.findAllByOrderByImportDateDescIdDesc();

        YearMonth thisMonth = YearMonth.now();
        long totalCost = 0, monthCost = 0, totalQty = 0;
        int monthCount = 0;
        // Gom theo tên nguồn (không phân biệt hoa/thường); giữ thứ tự: phiếu mới nhất xuất hiện trước
        Map<String, long[]> sums = new LinkedHashMap<>();      // [số phiếu, tổng số lượng, tổng tiền]
        Map<String, String> names = new HashMap<>();
        Map<String, String> contacts = new HashMap<>();
        Map<String, LocalDate> last = new HashMap<>();

        for (StockImport s : imports) {
            long cost = s.getTotalCost();
            long qty = s.getTotalQty();
            totalCost += cost;
            totalQty += qty;
            if (YearMonth.from(s.getImportDate()).equals(thisMonth)) {
                monthCost += cost;
                monthCount++;
            }
            String key = s.getSupplier().toLowerCase(Locale.ROOT);
            long[] a = sums.computeIfAbsent(key, k -> new long[3]);
            a[0]++;
            a[1] += qty;
            a[2] += cost;
            names.putIfAbsent(key, s.getSupplier());
            if (s.getSupplierContact() != null) contacts.putIfAbsent(key, s.getSupplierContact());
            last.merge(key, s.getImportDate(), (x, y) -> x.isAfter(y) ? x : y);
        }
        List<SupplierSummary> suppliers = new ArrayList<>();
        for (Map.Entry<String, long[]> e : sums.entrySet()) {
            suppliers.add(new SupplierSummary(names.get(e.getKey()), contacts.get(e.getKey()),
                    (int) e.getValue()[0], e.getValue()[1], e.getValue()[2], last.get(e.getKey())));
        }
        suppliers.sort(Comparator.comparingLong(SupplierSummary::totalCost).reversed());

        model.addAttribute("imports", imports);
        model.addAttribute("importCount", imports.size());
        model.addAttribute("supplierCount", suppliers.size());
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("totalCost", totalCost);
        model.addAttribute("totalQty", totalQty);
        model.addAttribute("monthCost", monthCost);
        model.addAttribute("monthCount", monthCount);
        return "dashboard/nhap-hang";
    }

    // ---------- Phiếu nhập hàng để in / lưu PDF ----------
    @GetMapping("/{id}/phieu")
    public String slip(@PathVariable Long id, Model model, RedirectAttributes ra) {
        StockImport s = importRepository.findWithItemsById(id).orElse(null);
        if (s == null) {
            ra.addFlashAttribute("importInfo", "Không tìm thấy phiếu nhập này.");
            return "redirect:/dashboard/nhap-hang";
        }
        model.addAttribute("imp", s);
        return "dashboard/nhap-hang-phieu";
    }

    // ---------- Tạo phiếu nhập ----------
    @GetMapping("/them")
    public String addForm(@RequestParam(value = "product", required = false) Long productId, Model model, HttpSession session) {
        // Bấm "Nhập thêm hàng" ở trang Kho hàng -> điền sẵn sản phẩm đó vào dòng đầu tiên
        List<Map<String, Object>> rows = new ArrayList<>();
        if (productId != null && productRepository.existsById(productId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", String.valueOf(productId));
            row.put("size", "");
            row.put("color", "");
            row.put("qty", "");
            row.put("cost", "");
            rows.add(row);
        }
        return formView(model, session, "", "", "", LocalDate.now().toString(), "", rows, null);
    }

    @PostMapping("/them")
    public String addSubmit(@RequestParam(defaultValue = "") String supplier,
                            @RequestParam(defaultValue = "") String supplierContact,
                            @RequestParam(defaultValue = "") String reference,
                            @RequestParam(defaultValue = "") String importDate,
                            @RequestParam(defaultValue = "") String note,
                            @RequestParam(value = "productId", required = false) List<String> productIds,
                            @RequestParam(value = "size", required = false) List<String> sizes,
                            @RequestParam(value = "color", required = false) List<String> colors,
                            @RequestParam(value = "qty", required = false) List<String> qtys,
                            @RequestParam(value = "cost", required = false) List<String> costs,
                            @RequestParam(value = "formToken", defaultValue = "") String formToken,
                            Authentication auth, Model model, RedirectAttributes ra, HttpSession session) {

        // Mỗi lần mở form có 1 mã dùng một lần: bấm đúp / gửi lại form (F5) không tạo phiếu thứ hai, tồn kho không bị cộng 2 lần
        if (!consumeToken(session, formToken)) {
            ra.addFlashAttribute("importInfo", "Phiếu này đã được gửi đi (hoặc trang đã hết hạn). Hãy kiểm tra lịch sử nhập hàng trước khi tạo lại.");
            return "redirect:/dashboard/nhap-hang";
        }

        // Đọc từng dòng form; dòng chưa chọn sản phẩm (dòng trống) thì bỏ qua
        List<Map<String, Object>> rows = new ArrayList<>();
        List<StockImportService.Line> lines = new ArrayList<>();
        int count = productIds == null ? 0 : productIds.size();
        for (int i = 0; i < count; i++) {
            Long pid = parseLong(at(productIds, i));
            String size = at(sizes, i);
            String color = at(colors, i);
            long qty = digits(at(qtys, i));
            long cost = digits(at(costs, i));
            if (pid == null && qty == 0 && cost == 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", pid == null ? "" : String.valueOf(pid));
            row.put("size", size);
            row.put("color", color);
            row.put("qty", qty == 0 ? "" : String.valueOf(qty));
            row.put("cost", cost == 0 ? "" : String.valueOf(cost));
            rows.add(row);
            lines.add(new StockImportService.Line(pid, size, color, (int) Math.min(qty, Integer.MAX_VALUE), cost));
        }

        LocalDate date = null;
        try {
            date = importDate.isBlank() ? null : LocalDate.parse(importDate.trim());
        } catch (DateTimeParseException ignored) {
            // để service báo "vui lòng chọn ngày"
        }

        try {
            StockImport saved = importService.create(supplier, supplierContact, reference, date, note, lines,
                    auth == null ? null : auth.getName());
            ra.addFlashAttribute("importMessage", "Đã lưu phiếu " + saved.getCode() + " từ \"" + saved.getSupplier()
                    + "\" - tồn kho đã được cộng thêm " + saved.getTotalQty() + " sản phẩm.");
            return "redirect:/dashboard/nhap-hang";
        } catch (IllegalArgumentException e) {
            return formView(model, session, supplier, supplierContact, reference, importDate, note, rows, e.getMessage());
        }
    }

    // ==================== helper ====================

    private static final String TOKEN_ATTR = "importFormTokens";
    private static final int MAX_TOKENS = 20;

    /** Cấp mã mới cho một form (giữ tối đa MAX_TOKENS mã đang mở, để mở nhiều tab vẫn dùng được). */
    @SuppressWarnings("unchecked")
    private static String newToken(HttpSession session) {
        Set<String> tokens;
        synchronized (session) {
            tokens = (Set<String>) session.getAttribute(TOKEN_ATTR);
            if (tokens == null) {
                tokens = Collections.synchronizedSet(new LinkedHashSet<>());
                session.setAttribute(TOKEN_ATTR, tokens);
            }
        }
        String t = UUID.randomUUID().toString();
        synchronized (tokens) {
            tokens.add(t);
            while (tokens.size() > MAX_TOKENS) {
                tokens.remove(tokens.iterator().next());
            }
        }
        return t;
    }

    /** true nếu mã còn hiệu lực (và bị huỷ ngay, chỉ dùng được một lần). */
    @SuppressWarnings("unchecked")
    private static boolean consumeToken(HttpSession session, String token) {
        Set<String> tokens = (Set<String>) session.getAttribute(TOKEN_ATTR);
        return tokens != null && !token.isBlank() && tokens.remove(token);
    }

    private String formView(Model model, HttpSession session, String supplier, String supplierContact, String reference, String importDate,
                            String note, List<Map<String, Object>> rows, String error) {
        List<Product> products = productRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        // Gợi ý tên nguồn đã nhập trước đây (để gõ nhanh và giữ tên thống nhất)
        Set<String> supplierNames = new LinkedHashSet<>();
        for (StockImport s : importRepository.findAllByOrderByImportDateDescIdDesc()) supplierNames.add(s.getSupplier());

        model.addAttribute("products", products);
        model.addAttribute("supplierNames", supplierNames);
        model.addAttribute("f_supplier", supplier);
        model.addAttribute("f_contact", supplierContact);
        model.addAttribute("f_reference", reference);
        model.addAttribute("f_date", importDate);
        model.addAttribute("f_note", note);
        model.addAttribute("rowsInit", toJson(rows));
        model.addAttribute("errorMessage", error);
        model.addAttribute("formToken", newToken(session));
        return "dashboard/nhap-hang-form";
    }

    private static String at(List<String> list, int i) {
        return list != null && i < list.size() && list.get(i) != null ? list.get(i).trim() : "";
    }

    private static Long parseLong(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Lấy phần chữ số của ô tiền / số lượng (bỏ dấu chấm ngăn cách); rỗng hoặc quá dài -> 0 */
    private static long digits(String s) {
        String d = s == null ? "" : s.replaceAll("\\D", "");
        if (d.isEmpty() || d.length() > 12) return 0;
        return Long.parseLong(d);
    }

    private static String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "[]";
        }
    }
}
