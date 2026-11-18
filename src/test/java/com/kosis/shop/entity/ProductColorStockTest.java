package com.kosis.shop.entity;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tồn kho tách riêng theo từng màu (và size). */
class ProductColorStockTest {

    private Product coloured() {
        Product p = new Product();
        p.setCategory("Áo");
        p.setSizes("S,M");
        p.setColors("Olive:#6b7a46,Black:#111111");
        p.setSizeStockMap(new LinkedHashMap<>());
        p.adjustStock("S", "Olive", 5);
        p.adjustStock("S", "Black", 2);
        p.adjustStock("M", "Black", 4);
        return p;
    }

    @Test
    void eachColourHasItsOwnStock() {
        Product p = coloured();
        assertTrue(p.isPerColorStock());
        assertEquals(5, p.getStockOf("S", "Olive"));
        assertEquals(2, p.getStockOf("S", "Black"));
        assertEquals(0, p.getStockOf("M", "Olive"));
        assertEquals(7, p.getStockOf("S"));
        assertEquals(11, p.getStock());
    }

    @Test
    void sellingOneColourDoesNotTouchTheOther() {
        Product p = coloured();
        p.adjustStock("S", "Olive", -5);
        assertEquals(0, p.getStockOf("S", "Olive"));
        assertEquals(2, p.getStockOf("S", "Black"));
        assertEquals(6, p.getStock());
        p.adjustStock("S", "Olive", -3);   // không âm
        assertEquals(0, p.getStockOf("S", "Olive"));
    }

    @Test
    void legacyProductSharesStockUntilConverted() {
        Product p = new Product();
        p.setSizes("S");
        p.setColors("A:#111111,B:#222222");
        p.setSizeStock("S:5");
        p.setStock(5);
        assertEquals(5, p.getStockOf("S", "B"));
        p.convertToPerColorStock();
        assertEquals(3, p.getStockOf("S", "A"));
        assertEquals(2, p.getStockOf("S", "B"));
        assertEquals(5, p.getStock());
    }

    @Test
    void sizelessColouredProduct() {
        Product p = new Product();
        p.setSizes("-");
        p.setColors("Den:#111111,Trang:#ffffff");
        p.setStock(7);
        p.convertToPerColorStock();
        assertEquals(4, p.getStockOf("", "Den"));
        assertEquals(3, p.getStockOf("", "Trang"));
        p.adjustStock("", "Trang", -3);
        assertEquals(0, p.getStockOfColor("Trang"));
        assertEquals(4, p.getStock());
    }

    @Test
    void reshapeKeepsExistingCellsAndZeroesNewOnes() {
        Product p = coloured();
        p.reshape("S,M,L", "Olive:#6b7a46,Black:#111111,Red:#ff0000", "Áo");
        assertEquals(5, p.getStockOf("S", "Olive"));
        assertEquals(0, p.getStockOf("S", "Red"));
        assertEquals(0, p.getStockOf("L", "Black"));
        assertEquals(11, p.getStock());
    }

    @Test
    void productWithoutColoursIgnoresColourArgument() {
        Product p = new Product();
        p.setSizes("S,M");
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("S", 2);
        m.put("M", 5);
        p.setSizeStockMap(m);
        assertEquals(2, p.getStockOf("S", "anything"));
        p.adjustStock("S", null, -1);
        assertEquals(6, p.getStock());
    }
}
