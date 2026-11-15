package com.kosis.shop.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class VoucherTest {

    private static Voucher percent(long value, long maxDiscount) {
        Voucher v = new Voucher();
        v.setDiscountType(Voucher.PERCENT);
        v.setDiscountValue(value);
        v.setMaxDiscount(maxDiscount);
        v.setActive(true);
        return v;
    }

    private static Voucher fixed(long value) {
        Voucher v = new Voucher();
        v.setDiscountType(Voucher.FIXED);
        v.setDiscountValue(value);
        v.setActive(true);
        return v;
    }

    @Test
    void normalizeRemovesSpacesAndUppercases() {
        assertEquals("SALE10", Voucher.normalize("  sale 10 "));
        assertEquals("", Voucher.normalize(null));
    }

    @Test
    void percentDiscount() {
        assertEquals(40_000, percent(10, 0).discountFor(400_000));
    }

    @Test
    void percentDiscountIsCappedByMaxDiscount() {
        assertEquals(50_000, percent(20, 50_000).discountFor(1_000_000));
    }

    @Test
    void fixedDiscount() {
        assertEquals(30_000, fixed(30_000).discountFor(400_000));
    }

    @Test
    void discountNeverExceedsSubtotal() {
        assertEquals(20_000, fixed(30_000).discountFor(20_000));
    }

    @Test
    void noDiscountOnEmptyOrder() {
        assertEquals(0, fixed(30_000).discountFor(0));
    }

    @Test
    void expiredAndExhausted() {
        Voucher v = fixed(10_000);
        v.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertTrue(v.isExpired());
        assertFalse(v.isUsable());

        Voucher w = fixed(10_000);
        w.setUsageLimit(2);
        w.setUsedCount(2);
        assertTrue(w.isExhausted());
        assertFalse(w.isUsable());
    }

    @Test
    void zeroUsageLimitMeansUnlimited() {
        Voucher v = fixed(10_000);
        v.setUsageLimit(0);
        v.setUsedCount(9_999);
        assertFalse(v.isExhausted());
        assertTrue(v.isUsable());
    }

    @Test
    void inactiveVoucherIsNotUsable() {
        Voucher v = fixed(10_000);
        v.setActive(false);
        assertFalse(v.isUsable());
    }
}
