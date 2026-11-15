package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.Review;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.ReviewRepository;
import com.kosis.shop.service.ReviewService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quản lý đánh giá sản phẩm: /dashboard/danh-gia (ADMIN và NHANVIEN; đã được SecurityConfig chặn chung ở /dashboard/**).
 * Nhân viên ẩn đánh giá không phù hợp: đánh giá bị ẩn không hiện ở trang sản phẩm và không tính vào điểm trung bình.
 */
@Controller
@RequestMapping("/dashboard/danh-gia")
public class ReviewAdminController {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewService reviewService;

    public ReviewAdminController(ReviewRepository reviewRepository, ProductRepository productRepository, ReviewService reviewService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.reviewService = reviewService;
    }

    @GetMapping
    public String list(Authentication auth, Model model) {
        List<Review> all = reviewRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> productNames = new HashMap<>();
        Map<Long, String> productImages = new HashMap<>();
        for (Product p : productRepository.findAllById(all.stream().map(Review::getProductId).distinct().toList())) {
            productNames.put(p.getId(), p.getName());
            productImages.put(p.getId(), p.getImage());
        }
        long hidden = all.stream().filter(Review::isHidden).count();

        // Điểm trung bình + phân bố số sao: chỉ tính đánh giá đang hiện (giống điểm khách thấy ở trang sản phẩm)
        List<Review> visible = all.stream().filter(r -> !r.isHidden()).toList();
        Map<Integer, Long> ratingCounts = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            final int st = star;
            ratingCounts.put(st, visible.stream().filter(r -> r.getRating() == st).count());
        }
        double avg = visible.stream().mapToInt(Review::getRating).average().orElse(0);
        model.addAttribute("avgRating", String.format(Locale.US, "%.1f", avg));
        model.addAttribute("avgRounded", (int) Math.round(avg));
        model.addAttribute("ratingCounts", ratingCounts);
        model.addAttribute("starLevels", List.of(5, 4, 3, 2, 1));

        model.addAttribute("reviews", all);
        model.addAttribute("productNames", productNames);
        model.addAttribute("productImages", productImages);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("hiddenCount", hidden);
        model.addAttribute("visibleCount", all.size() - hidden);
        model.addAttribute("username", auth.getName());
        model.addAttribute("role", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_NHANVIEN").replace("ROLE_", ""));
        return "dashboard/danh-gia";
    }

    /** Ẩn / hiện lại một đánh giá (bấm lại để hoàn tác) */
    @PostMapping("/{id}/an")
    public String toggleHidden(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean hidden, RedirectAttributes ra) {
        try {
            reviewService.setHidden(id, hidden);
            ra.addFlashAttribute("reviewAdminMessage", hidden ? "Đã ẩn đánh giá." : "Đã hiện lại đánh giá.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("reviewAdminError", e.getMessage());
        }
        return "redirect:/dashboard/danh-gia";
    }
}
