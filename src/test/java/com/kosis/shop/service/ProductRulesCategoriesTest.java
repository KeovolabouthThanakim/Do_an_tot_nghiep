package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra loại sản phẩm theo danh sách danh mục do ADMIN quản lý (thay cho danh sách cố định). */
class ProductRulesCategoriesTest {

    private static Product withCategory(String category) {
        Product p = new Product();
        p.setCategory(category);
        p.setGender("MEN");
        return p;
    }

    @Test
    void acceptsACustomCategoryAndNormalizesItsCase() {
        Product p = withCategory("mũ nón");
        String error = ProductRules.normalizeClassification(p, List.of("Áo", "Mũ nón"));
        assertNull(error);
        assertEquals("Mũ nón", p.getCategory());
    }

    @Test
    void rejectsACategoryThatIsNotInTheList() {
        String error = ProductRules.normalizeClassification(withCategory("Mũ nón"), List.of("Áo", "Quần"));
        assertNotNull(error);
        assertTrue(error.contains("Áo hoặc Quần"));
    }

    @Test
    void defaultOverloadStillUsesTheBuiltInList() {
        assertNull(ProductRules.normalizeClassification(withCategory("Giày")));
        String error = ProductRules.normalizeClassification(withCategory("Mũ nón"));
        assertEquals("Vui lòng chọn loại sản phẩm (Áo khoác, Áo, Áo thể thao, Quần, Giày, Túi hoặc Phụ kiện).", error);
    }
}
