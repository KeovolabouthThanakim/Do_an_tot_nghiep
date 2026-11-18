package com.kosis.shop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chạy định kỳ: đơn mà vận chuyển đã báo giao tới khách nhưng khách không bấm xác nhận
 * trong kosis.shipping.auto-complete-days ngày (mặc định 5) thì tự chuyển sang "Đã giao thành công".
 */
@Component
public class ShippingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShippingScheduler.class);

    private final OrderService orderService;

    public ShippingScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(initialDelayString = "${kosis.shipping.auto-check-initial-ms:60000}",
            fixedDelayString = "${kosis.shipping.auto-check-ms:3600000}")
    public void autoComplete() {
        int n = orderService.autoCompleteArrived();
        if (n > 0) {
            log.info("[KOSIS] Tự hoàn tất {} đơn đã giao tới khách quá {} ngày không phản hồi.", n, orderService.getAutoCompleteDays());
        }
    }
}
