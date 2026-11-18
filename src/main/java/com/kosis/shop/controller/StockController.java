package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.StockMovement;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.StockMovementRepository;
import com.kosis.shop.service.StockMovementService;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Trang "Tồn kho": xem tồn kho từng sản phẩm / size, sổ kho (nhập, bán, hoàn kho, điều chỉnh)
 * và điều chỉnh GIẢM tồn kho kèm lý do. Tăng tồn kho chỉ qua phiếu nhập.
 * Đã được bảo vệ sẵn ở SecurityConfig (/dashboard/** chỉ cho ADMIN / NHANVIEN).
 */
@Controller
@RequestMapping("/dashboard/ton-kho")
public class StockController {

    private static final int LOW_STOCK = 10;

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final StockMovementService movementService;

    public StockController(ProductRepository productRepository, StockMovementRepository movementRepository,
                           StockMovementService movementService) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.movementService = movementService;
    }

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

    @GetMapping
    public String page(@RequestParam(value = "product", required = false) Long productId, Model model) {
        List<Product> products = productRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        long pending = products.stream().filter(Product::isPendingImport).count();
        long out = products.stream().filter(p -> !p.isPendingImport() && p.getStock() == 0).count();
        long low = products.stream().filter(p -> !p.isPendingImport() && p.getStock() > 0 && p.getStock() <= LOW_STOCK).count();

        Product focus = productId == null ? null : products.stream().filter(p -> p.getId().equals(productId)).findFirst().orElse(null);
        List<StockMovement> movements = focus == null
                ? movementRepository.findTop100ByOrderByCreatedAtDescIdDesc()
                : movementRepository.findTop200ByProductIdOrderByCreatedAtDescIdDesc(focus.getId());

        model.addAttribute("products", products);
        model.addAttribute("productCount", products.size());
        model.addAttribute("pendingCount", pending);
        model.addAttribute("outCount", out);
        model.addAttribute("lowCount", low);
        model.addAttribute("inCount", products.size() - pending - out - low);
        model.addAttribute("totalUnits", products.stream().mapToLong(Product::getStock).sum());
        model.addAttribute("stockCostValue", products.stream().mapToLong(p -> p.getImportPrice() * p.getStock()).sum());
        model.addAttribute("lowStock", LOW_STOCK);
        model.addAttribute("movements", movements);
        model.addAttribute("focus", focus);
        return "dashboard/ton-kho";
    }

    /** Điều chỉnh giảm tồn kho (bắt buộc có lý do). */
    @PostMapping("/dieu-chinh")
    public String adjust(@RequestParam Long productId,
                         @RequestParam(defaultValue = "") String size,
                         @RequestParam(defaultValue = "") String color,
                         @RequestParam(defaultValue = "0") int quantity,
                         @RequestParam(defaultValue = "") String reason,
                         Authentication auth, RedirectAttributes ra) {
        try {
            StockMovement m = movementService.adjustDown(productId, size, color, quantity, reason, auth == null ? null : auth.getName());
            ra.addFlashAttribute("stockMessage", "Đã giảm " + quantity + " \"" + m.getProductName() + "\""
                    + (m.getColor() == null ? "" : " (màu " + m.getColor() + ")")
                    + (m.getSize() == null ? "" : " (size " + m.getSize() + ")") + " khỏi tồn kho.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("stockError", e.getMessage());
        }
        return "redirect:/dashboard/ton-kho" + (productId == null ? "" : "?product=" + productId);
    }
}
