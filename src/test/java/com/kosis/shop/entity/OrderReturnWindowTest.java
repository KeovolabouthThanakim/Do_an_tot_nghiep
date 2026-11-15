package com.kosis.shop.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Thời hạn đổi trả được chụp lại trong từng đơn: đổi cài đặt sau này không làm đổi hoá đơn / quyền trả hàng của đơn cũ.
 */
class OrderReturnWindowTest {

    private static Order deliveredDaysAgo(int days) {
        Order o = new Order();
        o.setStatus(Order.STATUS_CONFIRMED);
        o.setShippingStatus(Order.SHIP_DELIVERED);
        o.setDeliveredAt(LocalDateTime.now().minusDays(days));
        return o;
    }

    @Test
    void oldOrderWithoutSnapshotUsesCurrentSetting() {
        Order o = deliveredDaysAgo(10);
        assertNull(o.getReturnWindowDays());
        assertEquals(14, o.effectiveReturnDays(14));
        assertTrue(o.canRequestReturn(14));
        assertFalse(o.canRequestReturn(7));
    }

    @Test
    void snapshotBeatsCurrentSetting() {
        Order o = deliveredDaysAgo(10);
        o.setReturnWindowDays(7);

        assertEquals(7, o.effectiveReturnDays(60));
        assertFalse(o.canRequestReturn(60));     // admin nới lên 60 ngày cũng không mở lại hạn của đơn này

        o.setReturnWindowDays(14);
        assertTrue(o.canRequestReturn(1));       // admin siết xuống 1 ngày cũng không đóng hạn của đơn này
    }

    @Test
    void deadlineTextUsesSnapshot() {
        Order o = new Order();
        o.setDeliveredAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        o.setReturnWindowDays(7);
        assertEquals("08/11/2026", o.returnDeadlineText(30));
    }

    @Test
    void invalidSnapshotFallsBackToCurrentSetting() {
        Order o = new Order();
        o.setReturnWindowDays(0);
        assertEquals(9, o.effectiveReturnDays(9));
    }
}
