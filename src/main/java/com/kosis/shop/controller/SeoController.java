package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * robots.txt và sitemap.xml cho công cụ tìm kiếm (Google, Bing).
 * Địa chỉ website lấy từ kosis.base-url (biến môi trường KOSIS_BASE_URL) - nhớ đặt thành tên miền thật khi chạy thật.
 * Cả hai đường dẫn đã được mở công khai (SecurityConfig: anyRequest().permitAll()).
 */
@Controller
public class SeoController {

    /** Các trang tĩnh công khai đưa vào sitemap. */
    private static final List<String> PAGES = List.of(
            "/", "/collections", "/men", "/women", "/kids", "/sale", "/gifts",
            "/about", "/our-story", "/contact", "/shipping", "/returns", "/faq", "/privacy", "/terms");

    private final ProductRepository productRepository;

    @Value("${kosis.base-url:http://localhost:8080}")
    private String baseUrl;

    public SeoController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String body = String.join("\n",
                "User-agent: *",
                "Disallow: /dashboard/",
                "Disallow: /gio-hang",
                "Disallow: /thanh-toan",
                "Disallow: /don-hang-cua-toi",
                "Disallow: /account",
                "Disallow: /wishlist",
                "Disallow: /search",
                "Disallow: /login",
                "Disallow: /register",
                "Disallow: /forgot-password",
                "Disallow: /reset-password",
                "Disallow: /api/",
                "",
                "Sitemap: " + root() + "/sitemap.xml",
                "");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        String root = root();
        for (String path : PAGES) {
            url(sb, "/".equals(path) ? root + "/" : root + path);
        }
        // Chỉ sản phẩm đang bán (không đưa sản phẩm "chờ nhập hàng" lên Google)
        for (Product p : productRepository.findAllListed()) {
            url(sb, root + "/san-pham/" + p.getId());
        }
        sb.append("</urlset>\n");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                .body(sb.toString());
    }

    private static void url(StringBuilder sb, String loc) {
        sb.append("  <url><loc>").append(escape(loc)).append("</loc></url>\n");
    }

    private String root() {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
