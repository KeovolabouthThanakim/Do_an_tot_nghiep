package com.kosis.shop.controller;

import com.kosis.shop.entity.Product;
import com.kosis.shop.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeoControllerTest {

    private SeoController controller(List<Product> products) {
        ProductRepository repo = Mockito.mock(ProductRepository.class);
        Mockito.when(repo.findAllListed()).thenReturn(products);
        SeoController c = new SeoController(repo);
        ReflectionTestUtils.setField(c, "baseUrl", "https://kosis.vn/");
        return c;
    }

    @Test
    void robotsBlocksPrivateAreasAndPointsToSitemap() {
        String body = controller(List.of()).robots().getBody();
        assertTrue(body.contains("Disallow: /dashboard/"));
        assertTrue(body.contains("Disallow: /thanh-toan"));
        assertTrue(body.contains("Sitemap: https://kosis.vn/sitemap.xml"));
    }

    @Test
    void sitemapListsPagesAndListedProducts() {
        Product p = new Product();
        p.setId(7L);
        String xml = controller(List.of(p)).sitemap().getBody();
        assertTrue(xml.contains("<loc>https://kosis.vn/</loc>"));
        assertTrue(xml.contains("<loc>https://kosis.vn/men</loc>"));
        assertTrue(xml.contains("<loc>https://kosis.vn/san-pham/7</loc>"));
        assertFalse(xml.contains("/dashboard"));
    }
}
