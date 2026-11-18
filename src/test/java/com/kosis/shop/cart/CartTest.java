package com.kosis.shop.cart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    @Test
    void keyRoundTrip() {
        String k = Cart.key(12, "M", "Đen");
        assertEquals("12|M|Đen", k);
        assertEquals(12L, Cart.productIdOf(k));
        assertEquals("M", Cart.sizeOf(k));
        assertEquals("Đen", Cart.colorOf(k));
    }

    @Test
    void keyWithoutSizeAndColor() {
        String k = Cart.key(5, null, null);
        assertEquals("5||", k);
        assertEquals("", Cart.sizeOf(k));
        assertEquals("", Cart.colorOf(k));
    }

    @Test
    void legacyKeyWithoutColorStillReadable() {
        assertEquals(7L, Cart.productIdOf("7|XL"));
        assertEquals("XL", Cart.sizeOf("7|XL"));
        assertEquals("", Cart.colorOf("7|XL"));
    }

    @Test
    void differentSizeOrColorAreSeparateLines() {
        Cart c = new Cart();
        c.set(1, "M", "Đen", 1);
        c.set(1, "L", "Đen", 1);
        c.set(1, "M", "Trắng", 1);
        assertEquals(3, c.getItems().size());
        assertEquals(3, c.totalQuantity());
    }

    @Test
    void quantityIsCappedAtMaximum() {
        Cart c = new Cart();
        c.set(1, "M", "", 500);
        assertEquals(Cart.MAX_QTY_PER_ITEM, c.quantityOf(1, "M", ""));
    }

    @Test
    void zeroOrNegativeQuantityRemovesLine() {
        Cart c = new Cart();
        c.set(1, "M", "", 3);
        c.set(1, "M", "", 0);
        assertTrue(c.isEmpty());

        c.set(2, "", "", 2);
        c.set(2, "", "", -1);
        assertTrue(c.isEmpty());
    }

    @Test
    void quantityOfSizeSumsAllColors() {
        Cart c = new Cart();
        c.set(1, "M", "Đen", 2);
        c.set(1, "M", "Trắng", 3);
        c.set(1, "L", "Đen", 4);
        assertEquals(5, c.quantityOfSize(1, "M"));
        assertEquals(9, c.quantityOfProduct(1));
        assertEquals(0, c.quantityOfProduct(99));
    }

    @Test
    void removeAndClear() {
        Cart c = new Cart();
        c.set(1, "M", "", 2);
        c.set(2, "", "", 1);
        c.remove(1, "M", "");
        assertEquals(1, c.getItems().size());
        c.clear();
        assertTrue(c.isEmpty());
        assertEquals(0, c.totalQuantity());
    }
}
