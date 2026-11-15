package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.StockImport;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.StockImportRepository;
import com.kosis.shop.service.CategoryService;
import com.kosis.shop.service.ProductRules;
import com.kosis.shop.service.StockImportService;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trang "Kho hàng" trong dashboard - nơi DUY NHẤT quản lý toàn bộ sản phẩm
 * (thêm / sửa / xoá / upload nhiều ảnh cho mỗi sản phẩm, tối đa MAX_IMAGES ảnh).
 * Ảnh đầu tiên là ảnh chính, được trang chủ và /san-pham dùng; các ảnh sau là ảnh phụ.
 *
 * Đã được bảo vệ sẵn ở SecurityConfig.java (requestMatchers("/dashboard/**")
 * chỉ cho ADMIN / NHANVIEN).
 */
@Controller
@RequestMapping("/dashboard/kho-hang")
public class InventoryController {

    private final ProductRepository productRepository;
    private final StockImportRepository stockImportRepository;
    private final StockImportService importService;
    private final CategoryService categoryService;

    private static final Path UPLOAD_DIR = Path.of("uploads", "products");

    /** Giới hạn an toàn số ảnh cho mỗi sản phẩm (để không upload vô hạn làm đầy ổ đĩa) */
    private static final int MAX_IMAGES = 30;
    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");
    private static final int LOW_STOCK = 10;

    public InventoryController(ProductRepository productRepository, StockImportRepository stockImportRepository,
                               StockImportService importService, CategoryService categoryService) {
        this.productRepository = productRepository;
        this.stockImportRepository = stockImportRepository;
        this.importService = importService;
        this.categoryService = categoryService;
    }

    // ---------- Thông tin người đăng nhập cho sidebar (tên + vai trò ADMIN / NHANVIEN) ----------
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

    /** Danh sách loại sản phẩm cho ô chọn ở form và bộ lọc (cùng nguồn với menu ngoài cửa hàng) */
    @ModelAttribute("categories")
    public List<String> categories() {
        return categoryService.names();
    }

    // ---------- Danh sách sản phẩm ----------
    @GetMapping
    public String list(Model model) {
        List<Product> products = productRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        // Sản phẩm "chờ nhập hàng" tính riêng: chưa bán nên không phải "hết hàng" / "sắp hết"
        long pending = products.stream().filter(Product::isPendingImport).count();
        long out = products.stream().filter(p -> !p.isPendingImport() && p.getStock() == 0).count();
        long low = products.stream().filter(p -> !p.isPendingImport() && p.getStock() > 0 && p.getStock() <= LOW_STOCK).count();

        model.addAttribute("products", products);
        model.addAttribute("productCount", products.size());
        model.addAttribute("pendingCount", pending);
        model.addAttribute("outCount", out);
        model.addAttribute("lowCount", low);
        model.addAttribute("inCount", products.size() - pending - out - low);
        // Tổng số món đang nằm trong kho + giá trị tồn tính theo GIÁ NHẬP (chỉ dùng cho trang quản trị)
        model.addAttribute("totalUnits", products.stream().mapToLong(Product::getStock).sum());
        model.addAttribute("stockCostValue", products.stream().mapToLong(p -> p.getImportPrice() * p.getStock()).sum());
        model.addAttribute("lowStock", LOW_STOCK);
        model.addAttribute("lastSupplier", lastSuppliers());
        return "dashboard/kho-hang";
    }

    /** Nguồn nhập GẦN NHẤT của từng sản phẩm (productId -> tên nguồn), lấy từ các phiếu ở trang Nhập hàng. */
    private Map<Long, String> lastSuppliers() {
        Map<Long, String> out = new java.util.HashMap<>();
        for (StockImportRepository.ProductSupplier ps : stockImportRepository.findProductSuppliersNewestFirst()) {
            out.putIfAbsent(ps.getProductId(), ps.getSupplier());   // dòng đầu tiên của mỗi sản phẩm = lần nhập mới nhất
        }
        return out;
    }

    // ---------- Thêm sản phẩm ----------
    @GetMapping("/them")
    public String addForm(Model model) {
        return formView(model, new Product(), false, null);
    }

    @PostMapping("/them")
    public String addSubmit(@ModelAttribute Product product,
                            @RequestParam(value = "listPrice", defaultValue = "0") long listPrice,
                            @RequestParam(value = "newImages", required = false) List<MultipartFile> newImages,
                            @RequestParam(value = "imageOrder", required = false) String imageOrder,
                            @RequestParam Map<String, String> params,
                            Authentication auth, Model model, RedirectAttributes ra) throws IOException {

        // Giá vốn + số lượng "không chia màu / size" do người dùng nhập: đọc TRƯỚC khi đặt lại về 0 bên dưới
        final long openingCost = product.getImportPrice();
        final int plainQty = Math.max(0, product.getStock());

        if (product.getName() == null || product.getName().isBlank()) {
            return addFormError(model, product, params, openingCost, plainQty, "Vui lòng nhập tên sản phẩm.");
        }
        if (product.getCode() == null || product.getCode().isBlank()) {
            return addFormError(model, product, params, openingCost, plainQty, "Vui lòng nhập mã sản phẩm.");
        }
        String classifyError = normalizeClassification(product);
        if (classifyError != null) {
            return addFormError(model, product, params, openingCost, plainQty, classifyError);
        }
        applyPricing(product, listPrice);

        // Tồn đầu kỳ (không bắt buộc): số lượng từng màu / size + giá vốn. Có số lượng thì phải có giá vốn.
        List<StockImportService.OpeningLine> opening;
        try {
            opening = readOpeningStock(product, plainQty, openingCost, params);
        } catch (IllegalArgumentException e) {
            return addFormError(model, product, params, openingCost, plainQty, e.getMessage());
        }

        if (productRepository.existsByCode(product.getCode())) {
            return addFormError(model, product, params, openingCost, plainQty,
                    "Mã sản phẩm \"" + product.getCode() + "\" đã tồn tại.");
        }

        try {
            applyImages(product, newImages, imageOrder);
        } catch (IllegalArgumentException e) {
            return addFormError(model, product, params, openingCost, plainQty, e.getMessage());
        }

        // Tồn kho + giá nhập chỉ được ghi qua phiếu nhập (mỗi lô đều có nguồn và giá). Service đặt sản phẩm về
        // "chờ nhập hàng" / tồn 0, rồi nếu có tồn đầu kỳ thì tạo phiếu "Tồn đầu kỳ" (sản phẩm lên kệ ngay).
        StockImport slip;
        try {
            slip = importService.createProduct(product, opening, auth == null ? null : auth.getName());
        } catch (IllegalArgumentException e) {
            return addFormError(model, product, params, openingCost, plainQty, e.getMessage());
        }

        if (slip != null) {
            ra.addFlashAttribute("inventoryMessage", "Đã thêm sản phẩm \"" + product.getName() + "\" với tồn đầu kỳ "
                    + slip.getTotalQty() + " sản phẩm (phiếu " + slip.getCode() + "). Sản phẩm đã lên kệ bán.");
            return "redirect:/dashboard/kho-hang";
        }
        // Không khai báo tồn đầu kỳ: đi thẳng tới phiếu nhập với sản phẩm này đã được chọn sẵn
        ra.addFlashAttribute("importInfo", "Đã thêm sản phẩm \"" + product.getName()
                + "\" ở trạng thái Chờ nhập hàng. Nhập số lượng và giá nhập bên dưới để sản phẩm lên kệ bán.");
        return "redirect:/dashboard/nhap-hang/them?product=" + product.getId();
    }

    /**
     * Tạo nhanh sản phẩm từ ô "+ Sản phẩm mới" ở trang Nhập hàng (không rời trang). Chỉ các trường cần thiết;
     * sản phẩm ở trạng thái "chờ nhập hàng" và lên kệ khi phiếu nhập đang soạn được lưu.
     * Trả về JSON: {@code {ok:true, id, name, code, label, sizes}} hoặc {@code {ok:false, error}} (HTTP 400).
     */
    @PostMapping("/them-nhanh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickCreate(@RequestParam(defaultValue = "") String name,
                                                           @RequestParam(defaultValue = "") String code,
                                                           @RequestParam(defaultValue = "") String category,
                                                           @RequestParam(defaultValue = "") String gender,
                                                           @RequestParam(defaultValue = "") String colors,
                                                           @RequestParam(defaultValue = "") String sizes,
                                                           @RequestParam(defaultValue = "0") String listPrice,
                                                           Authentication auth) {
        Product p = new Product();
        p.setName(name.trim());
        p.setCode(code.trim());
        p.setCategory(category);
        p.setGender(gender);
        p.setColors(colors);
        p.setSizes(sizes.trim().isEmpty() ? null : sizes.trim());

        String error = null;
        if (p.getName().isEmpty()) error = "Vui lòng nhập tên sản phẩm.";
        else if (p.getName().length() > 150) error = "Tên sản phẩm quá dài (tối đa 150 ký tự).";
        else if (p.getCode().isEmpty()) error = "Vui lòng nhập mã sản phẩm.";
        else if (p.getCode().length() > 30) error = "Mã sản phẩm quá dài (tối đa 30 ký tự).";
        else if (sizes.trim().length() > 100) error = "Danh sách size quá dài (tối đa 100 ký tự).";
        if (error == null) error = ProductRules.normalizeClassification(p, categoryService.names());
        long list = 0;
        if (error == null) {
            String digits = listPrice.replaceAll("\\D", "");
            list = digits.isEmpty() || digits.length() > 12 ? 0 : Long.parseLong(digits);
            if (list < 1) error = "Vui lòng nhập giá bán niêm yết (lớn hơn 0) để sản phẩm bán được sau khi nhập hàng.";
        }
        if (error == null && productRepository.existsByCode(p.getCode())) {
            error = "Mã sản phẩm \"" + p.getCode() + "\" đã tồn tại.";
        }
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", error));
        }

        ProductRules.applyPricing(p, list);
        importService.createProduct(p, List.of(), auth == null ? null : auth.getName());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("id", p.getId());
        body.put("name", p.getName());
        body.put("code", p.getCode());
        body.put("label", p.getName() + " (" + p.getCode() + ")");
        body.put("sizes", String.join(",", p.getImportVariants()));   // cùng định dạng data-sizes của ô chọn sản phẩm
        return ResponseEntity.ok(body);
    }

    // ---------- Sửa sản phẩm ----------
    @GetMapping("/sua/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        return formView(model, find(id), true, null);
    }

    @PostMapping("/sua/{id}")
    public String editSubmit(@PathVariable Long id,
                             @ModelAttribute Product product,
                             @RequestParam(value = "listPrice", defaultValue = "0") long listPrice,
                             @RequestParam(value = "newImages", required = false) List<MultipartFile> newImages,
                             @RequestParam(value = "imageOrder", required = false) String imageOrder,
                             @RequestParam Map<String, String> params,
                             Model model, RedirectAttributes ra) throws IOException {

        Product existing = find(id);
        if (product.getName() == null || product.getName().isBlank()) {
            return formError(model, existing, true, "Vui lòng nhập tên sản phẩm.");
        }
        if (product.getCode() == null || product.getCode().isBlank()) {
            return formError(model, existing, true, "Vui lòng nhập mã sản phẩm.");
        }
        String classifyError = normalizeClassification(product);
        if (classifyError != null) {
            return formError(model, existing, true, classifyError);
        }

        String raiseError = stockChangeError(existing, product, params);
        if (raiseError != null) {
            return formError(model, existing, true, raiseError);
        }

        // Lần đầu chia kho theo màu: cho nhập số lượng từng (màu, size), nhưng tổng mỗi size phải bằng tồn kho hiện tại
        Map<String, Integer> colorSplit;
        try {
            colorSplit = readColorSplit(existing, product, params);
        } catch (IllegalArgumentException e) {
            return formError(model, existing, true, e.getMessage());
        }

        existing.setName(product.getName());
        existing.setCode(product.getCode());
        existing.setImportPrice(product.getImportPrice());
        existing.setDiscountPercent(product.getDiscountPercent());
        applyPricing(existing, listPrice);
        existing.setNew(product.isNew());
        existing.setRating(product.getRating());
        existing.setGender(product.getGender());
        // "section" (FEATURED/PROMOTION) không còn dùng ở trang chủ mới -> giữ nguyên giá trị cũ, không ghi đè
        existing.setDescription(product.getDescription());
        // Đổi loại / size / màu: giữ nguyên tồn kho từng (màu, size) đang có, ô mới = 0 (không đọc số từ form: tồn kho chỉ đổi qua nhập hàng / điều chỉnh giảm)
        existing.reshape(product.getSizes(), product.getColors(), product.getCategory());
        if (colorSplit != null) existing.setVariantStockMap(colorSplit);   // ghi đè phần chia đều mặc định bằng số người dùng nhập
        if (!existing.isHasSizes() && !existing.isHasColors()) existing.setStock(product.getStock());

        try {
            applyImages(existing, newImages, imageOrder);
        } catch (IllegalArgumentException e) {
            return formError(model, find(id), true, e.getMessage());
        }

        productRepository.save(existing);
        ra.addFlashAttribute("inventoryMessage", "Đã lưu thay đổi cho sản phẩm \"" + existing.getName() + "\".");
        return "redirect:/dashboard/kho-hang";
    }

    // ---------- Xoá sản phẩm ----------
    @PostMapping("/xoa/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Product p = productRepository.findById(id).orElse(null);
        if (p == null) {
            ra.addFlashAttribute("inventoryError", "Không tìm thấy sản phẩm cần xoá (có thể đã bị xoá trước đó).");
            return "redirect:/dashboard/kho-hang";
        }
        try {
            productRepository.delete(p);
            ra.addFlashAttribute("inventoryMessage", "Đã xoá sản phẩm \"" + p.getName() + "\".");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("inventoryError", "Không thể xoá sản phẩm \"" + p.getName() + "\". Vui lòng thử lại.");
        }
        return "redirect:/dashboard/kho-hang";
    }

    // ==================== helper ====================

    /** Tính giá bán từ giá niêm yết + % giảm (xem ProductRules). */
    private void applyPricing(Product p, long listPrice) {
        ProductRules.applyPricing(p, listPrice);
    }

    /** Kiểm tra + chuẩn hoá loại / đối tượng / màu (xem ProductRules). Trả về thông báo lỗi, hoặc null nếu hợp lệ. */
    private String normalizeClassification(Product p) {
        return ProductRules.normalizeClassification(p, categoryService.names());
    }

    /**
     * Trang sửa KHÔNG đổi tồn kho: tăng phải tạo phiếu nhập (để có nguồn + giá), giảm phải dùng trang Tồn kho
     * (để có lý do + người làm). Số lượng từng (màu, size) không đọc từ form mà giữ nguyên số đang lưu, nên ở đây chỉ cần
     * chặn việc bỏ một size / màu còn hàng (làm mất tồn kho). Trả về thông báo lỗi, hoặc null nếu hợp lệ.
     */
    private String stockChangeError(Product existing, Product submitted, Map<String, String> params) {
        final String hint = " Muốn tăng tồn kho, hãy tạo phiếu ở trang Nhập hàng; muốn giảm, dùng trang Tồn kho (điều chỉnh giảm, có ghi lý do).";
        Product after = new Product();            // size / màu sau khi sửa (không đụng vào sản phẩm thật)
        after.setSizes(submitted.getSizes());
        after.setCategory(submitted.getCategory());
        after.setColors(submitted.getColors());

        java.util.Set<String> newSizes = new java.util.LinkedHashSet<>(after.getSizeList());
        java.util.Set<String> newColors = new java.util.LinkedHashSet<>();
        for (Product.ColorOption c : after.getColorList()) newColors.add(c.getName().toLowerCase(java.util.Locale.ROOT));

        if (existing.isHasColors()) {
            Map<String, Integer> byColor = new LinkedHashMap<>();
            Map<String, Integer> bySize = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : existing.getVariantStockForForm().entrySet()) {
                int bar = e.getKey().lastIndexOf('|');
                byColor.merge(e.getKey().substring(0, bar), e.getValue(), Integer::sum);
                String sz = e.getKey().substring(bar + 1);
                if (!sz.isEmpty()) bySize.merge(sz, e.getValue(), Integer::sum);
            }
            for (Map.Entry<String, Integer> e : byColor.entrySet()) {
                if (!newColors.contains(e.getKey().toLowerCase(java.util.Locale.ROOT)) && e.getValue() > 0) {
                    return "Màu " + e.getKey() + " còn " + e.getValue() + " trong kho nên không thể bỏ hoặc đổi tên."
                            + " Hãy điều chỉnh giảm về 0 ở trang Tồn kho trước.";
                }
            }
            for (Map.Entry<String, Integer> e : bySize.entrySet()) {
                if (!newSizes.contains(e.getKey()) && e.getValue() > 0) {
                    return sizeRemovedMessage(e.getKey(), e.getValue());
                }
            }
            return null;
        }
        if (existing.isHasSizes()) {
            for (Map.Entry<String, Integer> e : existing.getSizeStockForForm().entrySet()) {   // bỏ một size còn hàng = làm mất tồn kho
                if (!newSizes.contains(e.getKey()) && e.getValue() > 0) {
                    return sizeRemovedMessage(e.getKey(), e.getValue());
                }
            }
            return null;
        }
        if (!after.isHasSizes() && !after.isHasColors() && submitted.getStock() != existing.getStock()) {
            return "Không thể sửa trực tiếp tồn kho (" + existing.getStock() + " -> " + submitted.getStock() + ")." + hint;
        }
        return null;
    }

    /**
     * Số lượng từng (màu, size) người dùng nhập ở form sửa (ô "varQty_{chỉ số màu}_{size}"). Chỉ áp dụng khi sản phẩm CHƯA chia kho
     * theo màu (lần chia đầu tiên); đã chia rồi thì số lượng chỉ đổi qua phiếu nhập / điều chỉnh giảm nên bỏ qua.
     * Tổng các màu của mỗi size phải đúng bằng tồn kho hiện tại của size đó (không tăng / giảm tổng).
     * Trả về null nếu không áp dụng; ném IllegalArgumentException (thông báo tiếng Việt) nếu dữ liệu sai.
     */
    private Map<String, Integer> readColorSplit(Product existing, Product submitted, Map<String, String> params) {
        if (existing.isPerColorStock()) return null;
        Product after = new Product();
        after.setSizes(submitted.getSizes());
        after.setCategory(submitted.getCategory());
        after.setColors(submitted.getColors());
        if (!after.isHasColors()) return null;

        java.util.List<Product.ColorOption> colors = after.getColorList();
        java.util.List<String> sizes = after.isHasSizes() ? after.getSizeList() : java.util.List.of("");
        Map<String, Integer> base = existing.isHasSizes() ? existing.getSizeStockForForm() : Map.of("", existing.getStock());

        int present = 0;
        for (int ci = 0; ci < colors.size(); ci++) {
            for (String sz : sizes) if (params.containsKey("varQty_" + ci + "_" + sz)) present++;
        }
        if (present == 0) return null;                                       // form không gửi số -> giữ cách chia đều mặc định
        if (present != colors.size() * sizes.size()) {
            throw new IllegalArgumentException("Dữ liệu số lượng từng màu không đầy đủ. Hãy tải lại trang rồi thử lại.");
        }

        Map<String, Integer> plan = new LinkedHashMap<>();
        for (String sz : sizes) {
            int sum = 0;
            for (int ci = 0; ci < colors.size(); ci++) {
                String raw = params.get("varQty_" + ci + "_" + sz);
                int q;
                try {
                    q = Integer.parseInt(raw == null ? "" : raw.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Số lượng của màu " + colors.get(ci).getName()
                            + (sz.isEmpty() ? "" : " (size " + sz + ")") + " không hợp lệ.");
                }
                if (q < 0 || q > 1_000_000) {
                    throw new IllegalArgumentException("Số lượng của màu " + colors.get(ci).getName()
                            + (sz.isEmpty() ? "" : " (size " + sz + ")") + " phải từ 0 đến 1.000.000.");
                }
                plan.put(Product.variantKey(colors.get(ci).getName(), sz), q);
                sum += q;
            }
            int want = base.getOrDefault(sz, 0);
            if (sum != want) {
                throw new IllegalArgumentException((sz.isEmpty() ? "Tổng số lượng các màu (" : "Tổng số lượng size " + sz + " của các màu (")
                        + sum + ") phải bằng tồn kho hiện tại (" + want + "). Muốn tăng tồn kho hãy tạo phiếu nhập hàng; muốn giảm, dùng trang Tồn kho.");
            }
        }
        return plan;
    }

    private static String sizeRemovedMessage(String size, int qty) {
        return "Size " + size + " còn " + qty + " trong kho nên không thể bỏ khỏi danh sách size."
                + " Hãy điều chỉnh giảm về 0 ở trang Tồn kho trước.";
    }

    // ---------- Tồn đầu kỳ khi tạo sản phẩm ----------

    /** Một ô nhập số lượng trên form thêm sản phẩm: tên ô, màu, size, nhãn hiển thị trong thông báo lỗi. */
    private interface SlotVisitor {
        void visit(String param, String color, String size, String label);
    }

    /**
     * Duyệt các ô số lượng theo đúng quy ước tên ô của form: có màu -> "varQty_{chỉ số màu}_{size}",
     * chỉ có size -> "sizeQty_{size}". Không màu, không size: không có ô nào (dùng ô "stock").
     */
    private static void forEachSlot(Product p, SlotVisitor v) {
        if (p.isHasColors()) {
            List<Product.ColorOption> colors = p.getColorList();
            List<String> sizes = p.isHasSizes() ? p.getSizeList() : List.of("");
            for (int ci = 0; ci < colors.size(); ci++) {
                for (String sz : sizes) {
                    String name = colors.get(ci).getName();
                    v.visit("varQty_" + ci + "_" + sz, name, sz, "màu " + name + (sz.isEmpty() ? "" : " (size " + sz + ")"));
                }
            }
        } else if (p.isHasSizes()) {
            for (String sz : p.getSizeList()) v.visit("sizeQty_" + sz, null, sz, "size " + sz);
        }
    }

    /** Đọc + kiểm tra tồn đầu kỳ. Ném IllegalArgumentException (tiếng Việt) nếu sai; danh sách rỗng = không khai báo. */
    private List<StockImportService.OpeningLine> readOpeningStock(Product p, int plainQty, long cost, Map<String, String> params) {
        List<StockImportService.OpeningLine> out = new java.util.ArrayList<>();
        if (p.isHasColors() || p.isHasSizes()) {
            forEachSlot(p, (param, color, size, label) -> {
                int q = parseOpeningQty(params.get(param), label);
                if (q > 0) out.add(new StockImportService.OpeningLine(size.isEmpty() ? null : size, color, q, cost));
            });
        } else if (plainQty > 0) {
            if (plainQty > StockImportService.MAX_QTY) {
                throw new IllegalArgumentException("Số lượng tồn đầu kỳ phải từ 0 đến " + StockImportService.MAX_QTY + ".");
            }
            out.add(new StockImportService.OpeningLine(null, null, plainQty, cost));
        }
        if (!out.isEmpty() && (cost < 1 || cost > StockImportService.MAX_COST)) {
            throw new IllegalArgumentException("Bạn đã nhập số lượng tồn đầu kỳ, vui lòng nhập thêm Giá nhập (giá vốn) lớn hơn 0.");
        }
        return out;
    }

    private static int parseOpeningQty(String raw, String label) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return 0;
        int q;
        try {
            q = Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Số lượng của " + label + " không hợp lệ.");
        }
        if (q < 0 || q > StockImportService.MAX_QTY) {
            throw new IllegalArgumentException("Số lượng của " + label + " phải từ 0 đến " + StockImportService.MAX_QTY + ".");
        }
        return q;
    }

    /** Hiện lại form thêm sản phẩm kèm lỗi, GIỮ NGUYÊN các số lượng / giá vốn đã gõ (lỗi nhẹ: đọc bỏ qua giá trị sai). */
    private String addFormError(Model model, Product product, Map<String, String> params,
                                long cost, int plainQty, String message) {
        product.setImportPrice(cost);
        product.setStock(plainQty);
        Map<String, Integer> typed = new LinkedHashMap<>();
        forEachSlot(product, (param, color, size, label) -> {
            int q = 0;
            try {
                q = Math.max(0, Integer.parseInt(params.getOrDefault(param, "0").trim()));
            } catch (NumberFormatException ignored) {
                // ô sai -> coi như 0, thông báo lỗi chính đã được hiện ở trên
            }
            typed.put(color == null ? size : Product.variantKey(color, size), q);
        });
        formView(model, product, false, message);
        model.addAttribute("sizeQtyInit", toJson(typed));
        return "dashboard/kho-hang-form";
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm id=" + id));
    }

    private String formError(Model model, Product product, boolean isEdit, String message) {
        return formView(model, product, isEdit, message);
    }

    /** Dựng trang form thêm/sửa, kèm số liệu tồn kho từng size (JSON) để JavaScript vẽ các ô nhập */
    private String formView(Model model, Product product, boolean isEdit, String error) {
        model.addAttribute("product", product);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("errorMessage", error);
        // Có màu: lưới "màu x size"; không có màu: lưới theo size như trước
        model.addAttribute("sizeQtyInit", toJson(product.isHasColors() ? product.getVariantStockForForm() : product.getSizeStockForForm()));
        // Tồn kho theo size (cộng mọi màu) / tổng tồn kho: để form tự chia đều khi vừa thêm màu cho sản phẩm cũ
        model.addAttribute("sizeQtyTotals", toJson(product.isHasSizes() ? product.getSizeStockForForm()
                : Map.of("", product.getStock())));
        return "dashboard/kho-hang-form";
    }

    private String toJson(Map<String, Integer> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * Áp dụng thay đổi bộ ảnh của sản phẩm (không giới hạn 3 ảnh).
     * <p>{@code order} do trang quản trị gửi lên, là danh sách theo thứ tự hiển thị, ngăn cách bằng dấu phẩy:
     * {@code e:K} = giữ ảnh hiện có thứ K (tính từ 0), {@code n:K} = file mới thứ K trong {@code files}.
     * Ảnh hiện có không nằm trong danh sách = đã bị xoá. {@code order == null} = giữ nguyên ảnh cũ và chỉ thêm file mới.
     * Ảnh đầu tiên sau khi sắp xếp là ảnh chính.
     */
    private void applyImages(Product p, List<MultipartFile> files, String order) throws IOException {
        List<MultipartFile> uploads = new java.util.ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) uploads.add(f);
            }
        }
        for (MultipartFile f : uploads) validate(f);   // kiểm tra hết trước khi lưu file nào

        List<String> current = p.getImages();
        List<Object> plan = new java.util.ArrayList<>();   // String = ảnh cũ giữ lại, Integer = chỉ số file mới
        java.util.Set<Integer> usedExisting = new java.util.HashSet<>();
        java.util.Set<Integer> usedUploads = new java.util.HashSet<>();

        if (order == null) {
            plan.addAll(current);
        } else {
            for (String tok : order.split(",")) {
                tok = tok.trim();
                if (tok.length() < 3) continue;
                int n;
                try {
                    n = Integer.parseInt(tok.substring(2));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (tok.startsWith("e:") && n >= 0 && n < current.size() && usedExisting.add(n)) {
                    plan.add(current.get(n));
                } else if (tok.startsWith("n:") && n >= 0 && n < uploads.size() && usedUploads.add(n)) {
                    plan.add(Integer.valueOf(n));
                }
            }
        }
        // file mới chưa có trong thứ tự -> thêm vào cuối
        for (int i = 0; i < uploads.size(); i++) {
            if (!usedUploads.contains(i)) plan.add(Integer.valueOf(i));
        }
        if (plan.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Mỗi sản phẩm tối đa " + MAX_IMAGES + " ảnh.");
        }

        List<String> result = new java.util.ArrayList<>();
        for (Object o : plan) {
            if (o instanceof Integer) result.add(saveImage(uploads.get((Integer) o)));
            else result.add((String) o);
        }
        p.replaceImages(result);
    }

    private String extensionOf(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        return original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        String type = file.getContentType();
        if (type == null || !type.startsWith("image/") || !ALLOWED_EXT.contains(extensionOf(file))) {
            throw new IllegalArgumentException("Chỉ chấp nhận file ảnh JPG, PNG, WEBP hoặc GIF.");
        }
    }

    /** Lưu file ảnh thật vào thư mục uploads/products, trả về đường dẫn URL để lưu vào DB */
    private String saveImage(MultipartFile file) throws IOException {
        Files.createDirectories(UPLOAD_DIR);
        String filename = UUID.randomUUID() + extensionOf(file);
        file.transferTo(UPLOAD_DIR.resolve(filename).toAbsolutePath());
        return "/uploads/products/" + filename;
    }
}