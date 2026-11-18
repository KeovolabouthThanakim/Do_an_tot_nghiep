package com.kosis.shop.entity;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductPendingImportTest {

    @Test
    void legacyProductWithoutFlagIsListed() {
        Product p = new Product();            // cột pending_import = NULL (hàng cũ)
        assertFalse(p.isPendingImport());
        assertTrue(p.isListed());
    }

    @Test
    void pendingProductIsNotListed() {
        Product p = new Product();
        p.setPendingImport(true);
        assertTrue(p.isPendingImport());
        assertFalse(p.isListed());
    }

    @Test
    void newSizedProductStartsAtZeroInEverySize() {
        Product p = new Product();
        p.setCategory("Áo");
        p.setSizes("S,M");
        p.setPendingImport(true);
        p.setSizeStockMap(new LinkedHashMap<>());
        assertEquals(0, p.getStock());
        Map<String, Integer> m = p.getSizeStockMap();
        assertEquals(0, m.get("S"));
        assertEquals(0, m.get("M"));
    }
}
