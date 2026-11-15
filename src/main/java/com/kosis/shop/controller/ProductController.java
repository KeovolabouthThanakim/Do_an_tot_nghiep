package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.kosis.shop.service.ReviewService;
import java.security.Principal;

import java.util.List;
import java.util.Objects;

/**
 * Product detail page (/san-pham/{id}). The product listing pages
 * (/collections, /san-pham, /men, /women, ...) live in {@link ShopController}.
 */
@Controller
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/san-pham/{id}")
    public String productDetail(@PathVariable Long id, Principal principal, Model model) {
        Product product = productRepository.findListedById(id).orElse(null);   // chờ nhập hàng = như không tồn tại
        if (product == null) {
            return "redirect:/collections";
        }

        // Related: same category first, then same gender; never the product itself; max 4
        List<Product> others = productRepository.findAllListed().stream()
                .filter(p -> !p.getId().equals(product.getId()))
                .toList();
        List<Product> related = new java.util.ArrayList<>(others.stream()
                .filter(p -> product.getCategory() != null && Objects.equals(product.getCategory(), p.getCategory()))
                .limit(4).toList());
        for (Product p : others) {
            if (related.size() >= 4) break;
            if (!related.contains(p) && product.getGender() != null && product.getGender().equalsIgnoreCase(p.getGender())) {
                related.add(p);
            }
        }

        model.addAttribute("product", product);
        model.addAttribute("related", related);

        // Đánh giá của khách: điểm trung bình thật, danh sách, và khách này có được đánh giá không
        String me = principal == null ? null : principal.getName();
        model.addAttribute("reviews", reviewService.visibleFor(id));
        model.addAttribute("reviewCount", reviewService.count(id));
        model.addAttribute("reviewAvg", reviewService.average(id));
        model.addAttribute("canReview", me != null && reviewService.hasPurchased(me, id));
        model.addAttribute("myReview", reviewService.findMine(id, me));
        model.addAttribute("activePage", "collections");
        return "products/detail";
    }

    /** Khách gửi / sửa đánh giá (bắt buộc đăng nhập và đã nhận hàng) */
    @PostMapping("/san-pham/{id}/danh-gia")
    public String review(@PathVariable Long id, @RequestParam int rating,
                         @RequestParam(defaultValue = "") String comment,
                         Principal principal, RedirectAttributes ra) {
        try {
            reviewService.submit(id, principal.getName(), rating, comment);
            ra.addFlashAttribute("reviewMessage", "Cảm ơn bạn đã đánh giá sản phẩm!");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("reviewError", e.getMessage());
        }
        return "redirect:/san-pham/" + id + "#danh-gia";
    }
}
