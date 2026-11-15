package com.kosis.shop.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PricingServiceTest {

    private final PricingService pricing = PricingService.of(30_000, 500_000);

    @Test
    void shippingIsChargedBelowFreeThreshold() {
        assertEquals(30_000, pricing.shippingFor(499_999));
    }

    @Test
    void shippingIsFreeAtOrAboveThreshold() {
        assertEquals(0, pricing.shippingFor(500_000));
        assertEquals(0, pricing.shippingFor(2_000_000));
    }

    @Test
    void emptyOrderHasNoShipping() {
        assertEquals(0, pricing.shippingFor(0));
    }

    @Test
    void zeroThresholdMeansNeverFree() {
        PricingService never = PricingService.of(30_000, 0);
        assertEquals(30_000, never.shippingFor(10_000_000));
    }

    @Test
    void zeroFeeMeansAlwaysFree() {
        PricingService free = PricingService.of(0, 500_000);
        assertEquals(0, free.shippingFor(100_000));
    }

    @Test
    void remainingForFreeShipping() {
        assertEquals(300_000, pricing.remainingForFreeShipping(200_000));
        assertEquals(0, pricing.remainingForFreeShipping(500_000));
        assertEquals(0, PricingService.of(30_000, 0).remainingForFreeShipping(1));
    }

    @Test
    void quoteSubtractsDiscountAndAddsShipping() {
        PricingService.Quote q = pricing.quote(400_000, 40_000, "SALE10");
        assertEquals(400_000, q.getSubtotal());
        assertEquals(40_000, q.getDiscount());
        assertEquals(30_000, q.getShipping());      // 360.000 < 500.000 nên vẫn tính phí
        assertEquals(390_000, q.getTotal());
        assertEquals("SALE10", q.getVoucherCode());
    }

    @Test
    void discountCannotExceedSubtotal() {
        PricingService.Quote q = pricing.quote(100_000, 150_000, "BIG");
        assertEquals(100_000, q.getDiscount());
        assertEquals(0, q.getShipping());
        assertEquals(0, q.getTotal());
    }

    @Test
    void freeShippingIsDecidedAfterDiscount() {
        // Tiền hàng 520.000 nhưng sau giảm 50.000 còn 470.000 < 500.000 -> mất miễn phí vận chuyển
        PricingService.Quote q = pricing.quote(520_000, 50_000, "X");
        assertEquals(30_000, q.getShipping());
        assertEquals(500_000, q.getTotal());
    }

    // ---------------------------------------------------------------- đặt cọc COD

    @Test
    void depositIsPercentOfTotalRoundedUpToThousand() {
        PricingService p = PricingService.of(30_000, 500_000, 30);
        assertEquals(129_000, p.depositFor(430_000));      // đúng 129.000
        assertEquals(122_000, p.depositFor(405_000));      // 121.500 -> làm tròn lên 122.000
        assertEquals(1_000, p.depositFor(1_000));          // 300 -> 1.000 (không vượt quá tổng đơn)
    }

    @Test
    void depositIsOffWhenPercentIsZeroOrTotalIsZero() {
        assertEquals(0, PricingService.of(30_000, 500_000, 0).depositFor(430_000));
        assertEquals(0, PricingService.of(30_000, 500_000, 30).depositFor(0));
        assertFalse(PricingService.of(30_000, 500_000).isCodDepositEnabled());
    }

    @Test
    void depositNeverExceedsTotal() {
        assertEquals(430_000, PricingService.of(30_000, 500_000, 100).depositFor(430_000));
        assertEquals(430_000, PricingService.of(30_000, 500_000, 250).depositFor(430_000));   // % > 100 bị chặn ở 100
    }
}
