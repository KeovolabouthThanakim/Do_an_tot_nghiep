package com.kosis.shop.service;

import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.OrderItem;

/**
 * Tính giá vốn / lợi nhuận gộp của 1 đơn cho báo cáo.
 * <ul>
 *   <li>Lợi nhuận gộp = tiền hàng - giảm giá (voucher) - giá vốn. KHÔNG tính phí vận chuyển (khoản thu hộ,
 *       hệ thống chưa biết chi phí vận chuyển thật).</li>
 *   <li>Chỉ đơn mà MỌI dòng hàng đều có giá vốn mới được tính ({@link OrderProfit#costKnown()});
 *       đơn thiếu giá vốn (đơn cũ / sản phẩm chưa nhập giá nhập) bị loại để không báo lãi cao hơn thực tế.</li>
 * </ul>
 */
public final class ProfitCalculator {

    private ProfitCalculator() {
    }

    /**
     * @param goods     tiền hàng (trước giảm giá, không gồm phí ship)
     * @param discount  số tiền voucher đã giảm
     * @param cost      tổng giá vốn các dòng hàng (chỉ có nghĩa khi costKnown)
     * @param costKnown true nếu mọi dòng hàng đều có giá vốn
     */
    public record OrderProfit(long goods, long discount, long cost, boolean costKnown) {
        /** Tiền hàng thực thu sau giảm giá (không gồm phí ship) */
        public long netGoods() { return goods - discount; }

        /** Lợi nhuận gộp (chỉ có nghĩa khi costKnown) */
        public long profit() { return netGoods() - cost; }
    }

    public static OrderProfit of(Order order) {
        long goods = 0;
        long cost = 0;
        boolean known = !order.getItems().isEmpty();
        for (OrderItem it : order.getItems()) {
            goods += it.getLineTotal();
            if (it.getUnitCost() == null) {
                known = false;
            } else {
                cost += it.getUnitCost() * it.getQuantity();
            }
        }
        return new OrderProfit(goods, order.getDiscountAmount(), known ? cost : 0, known);
    }
}
