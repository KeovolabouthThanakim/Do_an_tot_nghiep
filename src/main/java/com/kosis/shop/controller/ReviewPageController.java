package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.Review;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.ReviewRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trang công khai "Điểm đánh giá" (/danh-gia): điểm trung bình, số lượng theo từng mức sao và danh sách đánh giá của khách.
 * Chỉ tính các đánh giá đang hiện (đánh giá bị nhân viên ẩn không xuất hiện và không tính điểm). Tên khách luôn được che bớt.
 * (Trang quản lý của nhân viên là /dashboard/danh-gia - khác trang này.)
 */
@Controller
public class ReviewPageController {

    /** Số đánh giá tối đa hiển thị trên một trang (mới nhất trước) */
    private static final int MAX_SHOWN = 100;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    public ReviewPageController(ReviewRepository reviewRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    /** Các khoảng thời gian cho bộ lọc danh sách (số ngày; 0 = tất cả) */
    private static final List<Integer> PERIODS = List.of(0, 30, 90, 180);

    /** Xếp hạng shop theo điểm trung bình quy về thang 10 (cần ít nhất 1 đánh giá) */
    static String tierFor(double score10) {
        if (score10 >= 9.0) return "AA+";
        if (score10 >= 8.5) return "AA";
        if (score10 >= 8.0) return "A+";
        if (score10 >= 7.0) return "A";
        if (score10 >= 6.0) return "B";
        return "C";
    }

    @GetMapping("/danh-gia")
    public String reviews(@RequestParam(name = "sao", required = false) Integer sao,
                          @RequestParam(name = "ky", required = false) Integer ky, Model model) {
        List<Review> visible = reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> !r.isHidden())
                .toList();

        // Phân bố số sao (5 -> 1) và phần trăm để vẽ thanh
        Map<Integer, Long> ratingCounts = new LinkedHashMap<>();
        Map<Integer, Integer> ratingPercents = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            final int st = star;
            long n = visible.stream().filter(r -> r.getRating() == st).count();
            ratingCounts.put(st, n);
            ratingPercents.put(st, visible.isEmpty() ? 0 : (int) Math.round(n * 100.0 / visible.size()));
        }
        double avg = visible.stream().mapToInt(Review::getRating).average().orElse(0);

        // Lọc theo số sao (?sao=5); giá trị lạ thì bỏ qua = xem tất cả
        Integer filter = (sao != null && sao >= 1 && sao <= 5) ? sao : null;
        // Lọc theo khoảng thời gian (?ky=30|90|180); giá trị lạ = tất cả
        int period = (ky != null && PERIODS.contains(ky)) ? ky : 0;
        LocalDateTime since = period > 0 ? LocalDateTime.now().minusDays(period) : null;
        List<Review> shown = visible.stream()
                .filter(r -> filter == null || r.getRating() == filter)
                .filter(r -> since == null || (r.getCreatedAt() != null && r.getCreatedAt().isAfter(since)))
                .limit(MAX_SHOWN)
                .toList();

        // Các chỉ số tổng quan (tính trên toàn bộ đánh giá đang hiện)
        double score10 = avg * 2;
        long satisfied = visible.stream().filter(r -> r.getRating() >= 4).count();
        long withComment = visible.stream().filter(r -> r.getComment() != null && !r.getComment().isBlank()).count();
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        long last30 = visible.stream().filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(monthAgo)).count();
        int total = visible.size();

        Map<Long, String> productNames = new HashMap<>();
        Map<Long, String> productImages = new HashMap<>();
        for (Product p : productRepository.findAllById(shown.stream().map(Review::getProductId).distinct().toList())) {
            productNames.put(p.getId(), p.getName());
            productImages.put(p.getId(), p.getImage());
        }

        model.addAttribute("totalCount", visible.size());
        model.addAttribute("avgRating", avg);
        model.addAttribute("avgText", String.format(Locale.US, "%.1f", avg));
        model.addAttribute("ratingCounts", ratingCounts);
        model.addAttribute("ratingPercents", ratingPercents);
        model.addAttribute("starLevels", List.of(5, 4, 3, 2, 1));
        model.addAttribute("activeStar", filter);
        model.addAttribute("activePeriod", period);
        model.addAttribute("periods", PERIODS);
        model.addAttribute("score10Text", String.format(Locale.US, "%.2f", score10));
        model.addAttribute("tier", total == 0 ? "–" : tierFor(score10));
        model.addAttribute("satisfiedPercent", total == 0 ? 0 : (int) Math.round(satisfied * 100.0 / total));
        model.addAttribute("commentPercent", total == 0 ? 0 : (int) Math.round(withComment * 100.0 / total));
        model.addAttribute("last30Count", last30);
        model.addAttribute("reviews", shown);
        model.addAttribute("productNames", productNames);
        model.addAttribute("productImages", productImages);
        model.addAttribute("activePage", "reviews");
        return "pages/reviews";
    }
}
