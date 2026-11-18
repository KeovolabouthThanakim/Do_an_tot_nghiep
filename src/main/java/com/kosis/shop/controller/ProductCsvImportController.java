package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.StockImport;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.service.ProductCsvParser;
import com.kosis.shop.service.StockImportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thêm HÀNG LOẠT sản phẩm mới từ file CSV (hợp khi có từ vài chục sản phẩm trở lên).
 *
 * <p>Hai bước để không tạo nhầm hàng chục sản phẩm: (1) tải file lên -> kiểm tra toàn bộ và hiện bản xem trước
 * (hoặc danh sách lỗi theo từng dòng, không lưu gì); (2) bấm xác nhận -> tạo sản phẩm. Tồn kho vẫn chỉ tăng qua
 * phiếu nhập: dòng có số lượng sinh ra các phiếu "Tồn đầu kỳ" (xem {@link StockImportService#createProducts}).
 * File chỉ để thêm sản phẩm MỚI, không sửa / ghi đè sản phẩm đã có.
 */
@Controller
@RequestMapping("/dashboard/kho-hang/nhap-csv")
public class ProductCsvImportController {

    private static final String SESSION_TOKEN = "productCsvToken";
    private static final String SESSION_TEXT = "productCsvText";
    private static final int SHOW_ERRORS = 50;

    private final ProductRepository productRepository;
    private final StockImportService importService;
    private final com.kosis.shop.service.CategoryService categoryService;

    public ProductCsvImportController(ProductRepository productRepository, StockImportService importService,
                                      com.kosis.shop.service.CategoryService categoryService) {
        this.categoryService = categoryService;
        this.productRepository = productRepository;
        this.importService = importService;
    }

    /** Các loại sản phẩm hợp lệ (hiện ở phần hướng dẫn cột category) */
    @ModelAttribute("categories")
    public java.util.List<String> categories() {
        return categoryService.names();
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

    /** Một dòng của bản xem trước (một sản phẩm sẽ được tạo). */
    public record PreviewRow(String code, String name, String category, long price, int variants,
                             long qty, long cost, boolean listed) {
    }

    @GetMapping
    public String page() {
        return "dashboard/kho-hang-csv";
    }

    /** File mẫu (UTF-8 có BOM để Excel đọc đúng tiếng Việt). */
    @GetMapping("/mau")
    public ResponseEntity<byte[]> sample() {
        byte[] body = ("\uFEFF" + ProductCsvParser.SAMPLE_CSV).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kosis-san-pham-mau.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    // ---------- Bước 1: tải lên + kiểm tra ----------
    @PostMapping
    public String check(@RequestParam("file") MultipartFile file, HttpSession session, Model model) throws IOException {
        clearPending(session);

        if (file == null || file.isEmpty()) {
            return fail(model, "Vui lòng chọn file CSV.");
        }
        if (file.getSize() > ProductCsvParser.MAX_BYTES) {
            return fail(model, "File quá lớn (tối đa " + (ProductCsvParser.MAX_BYTES / 1024 / 1024) + "MB). Hãy chia nhỏ file.");
        }
        String text;
        try {
            text = ProductCsvParser.decode(file.getBytes());
        } catch (IllegalArgumentException e) {
            return fail(model, e.getMessage());
        }

        ProductCsvParser.Result result = ProductCsvParser.parse(text, productRepository::existsByCode, categoryService.names());
        if (!result.ok()) {
            List<String> errors = result.errors();
            model.addAttribute("csvErrors", errors.size() > SHOW_ERRORS ? errors.subList(0, SHOW_ERRORS) : errors);
            model.addAttribute("moreErrors", Math.max(0, errors.size() - SHOW_ERRORS));
            model.addAttribute("fileName", file.getOriginalFilename());
            return "dashboard/kho-hang-csv";
        }

        // Hợp lệ: hiện bản xem trước + giữ nội dung trong session để bước xác nhận không bắt chọn lại file
        List<PreviewRow> rows = new ArrayList<>();
        long totalQty = 0, totalCost = 0;
        int listed = 0;
        for (StockImportService.NewProduct np : result.products()) {
            Product p = np.product();
            long qty = 0, cost = 0;
            for (StockImportService.OpeningLine o : np.opening()) {
                qty += o.quantity();
                cost += (long) o.quantity() * o.unitCost();
            }
            int variants = Math.max(1, p.getColorList().size()) * Math.max(1, p.getSizeList().size());
            boolean isListed = qty > 0;
            if (isListed) listed++;
            totalQty += qty;
            totalCost += cost;
            rows.add(new PreviewRow(p.getCode(), p.getName(), p.getCategory(), p.getPrice(), variants, qty, cost, isListed));
        }
        String token = UUID.randomUUID().toString();
        session.setAttribute(SESSION_TOKEN, token);
        session.setAttribute(SESSION_TEXT, text);

        model.addAttribute("preview", rows);
        model.addAttribute("previewListed", listed);
        model.addAttribute("previewPending", rows.size() - listed);
        model.addAttribute("previewQty", totalQty);
        model.addAttribute("previewCost", totalCost);
        model.addAttribute("token", token);
        model.addAttribute("fileName", file.getOriginalFilename());
        return "dashboard/kho-hang-csv";
    }

    // ---------- Bước 2: xác nhận -> tạo sản phẩm ----------
    @PostMapping("/xac-nhan")
    public String confirm(@RequestParam(defaultValue = "") String token, HttpSession session,
                          Authentication auth, RedirectAttributes ra) {
        Object savedToken = session.getAttribute(SESSION_TOKEN);
        Object savedText = session.getAttribute(SESSION_TEXT);
        clearPending(session);   // dùng một lần: bấm đúp / gửi lại (F5) không tạo sản phẩm lần hai
        if (!(savedToken instanceof String) || !(savedText instanceof String) || token.isBlank() || !token.equals(savedToken)) {
            ra.addFlashAttribute("csvError", "Phiên nhập CSV đã hết hạn hoặc đã được xử lý. Hãy kiểm tra kho hàng rồi tải file lên lại nếu cần.");
            return "redirect:/dashboard/kho-hang/nhap-csv";
        }

        // Kiểm tra lại lần nữa (trong lúc xem trước có thể đã có người tạo sản phẩm trùng mã)
        ProductCsvParser.Result result = ProductCsvParser.parse((String) savedText, productRepository::existsByCode, categoryService.names());
        if (!result.ok()) {
            ra.addFlashAttribute("csvError", "Dữ liệu đã thay đổi nên không nhập được: " + result.errors().get(0)
                    + " Hãy tải file lên và kiểm tra lại.");
            return "redirect:/dashboard/kho-hang/nhap-csv";
        }

        List<StockImport> slips;
        try {
            slips = importService.createProducts(result.products(), auth == null ? null : auth.getName());
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            ra.addFlashAttribute("csvError", "Không nhập được, chưa có sản phẩm nào được tạo"
                    + (e instanceof IllegalArgumentException ? ": " + e.getMessage() : " (có thể mã sản phẩm bị trùng). Hãy kiểm tra lại file."));
            return "redirect:/dashboard/kho-hang/nhap-csv";
        }

        long units = 0;
        for (StockImport s : slips) units += s.getTotalQty();
        int withStock = 0;
        for (StockImportService.NewProduct np : result.products()) if (!np.opening().isEmpty()) withStock++;
        int total = result.products().size();

        String msg = "Đã tạo " + total + " sản phẩm từ file CSV.";
        if (withStock > 0) {
            msg += " " + withStock + " sản phẩm có tồn đầu kỳ (" + units + " cái, " + slips.size()
                    + " phiếu \"" + StockImportService.OPENING_SUPPLIER + "\") đã lên kệ bán.";
        }
        if (total - withStock > 0) {
            msg += " " + (total - withStock) + " sản phẩm chưa có hàng đang ở trạng thái Chờ nhập hàng.";
        }
        ra.addFlashAttribute("inventoryMessage", msg);
        return "redirect:/dashboard/kho-hang";
    }

    // ==================== helper ====================

    private static void clearPending(HttpSession session) {
        session.removeAttribute(SESSION_TOKEN);
        session.removeAttribute(SESSION_TEXT);
    }

    private static String fail(Model model, String message) {
        model.addAttribute("csvErrors", List.of(message));
        model.addAttribute("moreErrors", 0);
        return "dashboard/kho-hang-csv";
    }
}
