package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * KOSIS homepage. Product data always comes from the products table
 * (managed in /dashboard/kho-hang); {@link com.kosis.shop.config.DataSeeder}
 * fills it with the KOSIS sample catalogue on first run.
 */
@Controller
public class HomeController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private com.kosis.shop.service.CategoryService categoryService;

    /** Simple view object for category links (kept for the shared helpers below). */
    public static class Category {
        private final String name;
        private final String image;

        public Category(String name, String image) {
            this.name = name;
            this.image = image;
        }

        public String getName() { return name; }
        public String getImage() { return image; }
    }

    // ---------- Shared data used by several controllers ----------

    public static List<Category> buildCategories() {
        List<Category> categories = new ArrayList<>();
        for (String c : Product.CATEGORIES) {
            categories.add(new Category(c, null));
        }
        return categories;
    }

    /** Suggested searches (kept under the old name because other controllers still call it). */
    public static List<String> buildPopularSearches() {
        return List.of("Trench coat", "Sneaker", "Hoodie", "Túi xách", "Kính mát", "Denim");
    }

    @GetMapping("/")
    public String index(Model model) {
        List<Product> all = productRepository.findAllListed();   // bỏ sản phẩm chờ nhập hàng

        model.addAttribute("seasonsMen", seasonPick(all, "MEN", 12));
        model.addAttribute("seasonsWomen", seasonPick(all, "WOMEN", 12));
        model.addAttribute("seasonsKids", seasonPick(all, "KIDS", 12));
        // New arrivals: newest first, adult ranges before kids, and each photo only once
        java.util.Set<String> seenImages = new java.util.HashSet<>();
        model.addAttribute("newArrivals", all.stream()
                .filter(Product::isNew)
                .sorted(Comparator.comparing((Product p) -> "KIDS".equalsIgnoreCase(p.getGender()))
                        .thenComparing(Comparator.comparing(Product::getId).reversed()))
                .filter(p -> p.getImage() == null || seenImages.add(p.getImage()))
                .limit(8)
                .toList());
        // chip danh mục ở trang chủ: các danh mục đang bật hiện trên menu (do ADMIN quản lý)
        model.addAttribute("collectionCategories", categoryService.menuNames().stream().map(n -> new Category(n, null)).toList());
        model.addAttribute("activePage", "home");
        return "home/index";
    }

    /** Products for one tab of "KOSIS SEASONS": that gender first, then unisex pieces (fills the grid up to {@code limit}). */
    static List<Product> seasonPick(List<Product> all, String gender, int limit) {
        List<Product> own = all.stream().filter(p -> gender.equalsIgnoreCase(p.getGender())).toList();
        List<Product> unisex = all.stream().filter(p -> "UNISEX".equalsIgnoreCase(p.getGender())).toList();
        List<Product> out = new ArrayList<>(own);
        // Kids has its own small range; do not pad it with adult unisex pieces
        if (!"KIDS".equals(gender)) out.addAll(unisex);
        // Databases created before the redesign have no gender at all -> fall back to whatever exists
        if (out.isEmpty() && !"KIDS".equals(gender)) out.addAll(all);
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}
