package com.kosis.shop.entity;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductStockTest {

    private static Product withSizes(String sizes, int s, int m) {
        Product p = new Product();
        p.setSizes(sizes);
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("S", s);
        map.put("M", m);
        p.setSizeStockMap(map);
        return p;
    }

    @Test
    void perSizeStockIsTrackedSeparately() {
        Product p = withSizes("S,M", 2, 5);
        assertTrue(p.isPerSizeStock());
        assertEquals(2, p.getStockOf("S"));
        assertEquals(5, p.getStockOf("M"));
        assertEquals(7, p.getStock());   // tổng các size
    }

    @Test
    void sizeLookupIgnoresCaseAndSpaces() {
        Product p = withSizes("S,M", 2, 5);
        assertEquals(5, p.getStockOf(" m "));
    }

    @Test
    void unknownSizeHasNoStock() {
        Product p = withSizes("S,M", 2, 5);
        assertEquals(0, p.getStockOf("XXL"));
    }

    @Test
    void adjustOnlyAffectsThatSizeAndUpdatesTotal() {
        Product p = withSizes("S,M", 2, 5);
        p.adjustStock("S", -1);
        assertEquals(1, p.getStockOf("S"));
        assertEquals(5, p.getStockOf("M"));
        assertEquals(6, p.getStock());
        p.adjustStock("S", 3);   // hoàn kho
        assertEquals(4, p.getStockOf("S"));
    }

    @Test
    void stockNeverGoesBelowZero() {
        Product p = withSizes("S,M", 2, 5);
        p.adjustStock("S", -10);
        assertEquals(0, p.getStockOf("S"));
    }

    @Test
    void productWithoutSizesUsesSharedStock() {
        Product p = new Product();
        p.setSizes("-");
        p.setStock(10);
        assertFalse(p.isHasSizes());
        assertEquals(10, p.getStockOf(""));
        p.adjustStock("", -4);
        assertEquals(6, p.getStock());
        p.adjustStock("", -100);
        assertEquals(0, p.getStock());
    }

    @Test
    void legacyProductSharesOneStockAcrossSizes() {
        Product p = new Product();
        p.setSizes("S,M");
        p.setStock(8);
        assertTrue(p.isLegacySizeStock());
        assertEquals(8, p.getStockOf("S"));
        assertEquals(8, p.getStockOf("M"));
        p.adjustStock("S", -3);
        assertEquals(5, p.getStockOf("M"));
    }
}
