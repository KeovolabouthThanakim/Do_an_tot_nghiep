package com.kosis.shop.service;

import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.OrderItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfitCalculatorTest {

    private static OrderItem item(long price, Long cost, int qty) {
        OrderItem it = new OrderItem();
        it.setProductName("Áo");
        it.setUnitPrice(price);
        it.setUnitCost(cost);
        it.setQuantity(qty);
        return it;
    }

    private static Order order(long discount, OrderItem... items) {
        Order o = new Order();
        o.setDiscountAmount(discount);
        for (OrderItem it : items) o.addItem(it);
        return o;
    }

    @Test
    void profitIsGoodsMinusDiscountMinusCost() {
        // 2 x 200.000 (vốn 120.000) + 1 x 100.000 (vốn 60.000); voucher giảm 50.000
        Order o = order(50_000, item(200_000, 120_000L, 2), item(100_000, 60_000L, 1));
        ProfitCalculator.OrderProfit p = ProfitCalculator.of(o);

        assertTrue(p.costKnown());
        assertEquals(500_000, p.goods());
        assertEquals(50_000, p.discount());
        assertEquals(300_000, p.cost());                    // 2*120.000 + 60.000
        assertEquals(450_000, p.netGoods());
        assertEquals(150_000, p.profit());
    }

    @Test
    void shippingFeeIsNotPartOfProfit() {
        Order o = order(0, item(200_000, 120_000L, 1));
        o.setShippingFee(30_000);
        o.setTotalAmount(230_000);

        assertEquals(80_000, ProfitCalculator.of(o).profit());
    }

    @Test
    void orderWithAnyUnknownCostIsNotCounted() {
        Order o = order(0, item(200_000, 120_000L, 1), item(100_000, null, 1));
        ProfitCalculator.OrderProfit p = ProfitCalculator.of(o);

        assertFalse(p.costKnown());
        assertEquals(0, p.cost());                          // không cộng dở dang
    }

    @Test
    void orderWithoutItemsIsNotCounted() {
        assertFalse(ProfitCalculator.of(new Order()).costKnown());
    }

    @Test
    void profitCanBeNegativeWhenDiscountExceedsMargin() {
        Order o = order(100_000, item(200_000, 150_000L, 1));

        assertEquals(-50_000, ProfitCalculator.of(o).profit());
    }
}
