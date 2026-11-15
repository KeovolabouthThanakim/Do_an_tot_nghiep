package com.kosis.shop.controller;

import com.kosis.shop.entity.Order;
import com.kosis.shop.service.OrderNotifier;
import com.kosis.shop.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Webhook nhận thông báo "tiền đã về tài khoản" từ dịch vụ đối soát ngân hàng (định dạng SePay / Casso:
 * JSON có transferType, transferAmount, content). Khi tiền khớp với đúng 1 đơn chuyển khoản đang chờ kiểm tra,
 * đơn được tự xác nhận mà không cần nhân viên đối chiếu tay.
 *
 * Bảo mật: chỉ chạy khi đã đặt khoá shop.payment.webhook-key (biến môi trường KOSIS_PAYMENT_WEBHOOK_KEY);
 * dịch vụ đối soát phải gửi header "Authorization: Apikey &lt;khoá&gt;" (hoặc "X-Api-Key: &lt;khoá&gt;").
 * Chưa đặt khoá = endpoint tắt, mọi đơn vẫn do nhân viên xác nhận tay như cũ.
 */
@RestController
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final OrderService orderService;
    private final OrderNotifier orderNotifier;

    @Value("${shop.payment.webhook-key:}")
    private String webhookKey;

    public PaymentWebhookController(OrderService orderService, OrderNotifier orderNotifier) {
        this.orderService = orderService;
        this.orderNotifier = orderNotifier;
    }

    @PostMapping("/api/payment/webhook")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestBody Map<String, Object> body) {

        if (webhookKey == null || webhookKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("success", false, "message", "Webhook chưa được bật"));
        }
        String given = apiKey != null ? apiKey : authorization;
        if (given != null && given.regionMatches(true, 0, "Apikey ", 0, 7)) {
            given = given.substring(7);
        }
        if (given == null || !MessageDigest.isEqual(
                given.trim().getBytes(StandardCharsets.UTF_8), webhookKey.trim().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Sai khoá"));
        }

        // Chỉ xét giao dịch tiền VÀO tài khoản
        Object type = body.get("transferType");
        if (type != null && !"in".equalsIgnoreCase(String.valueOf(type))) {
            return ResponseEntity.ok(Map.of("success", true, "matched", false));
        }
        long amount = toLong(body.get("transferAmount"));
        String content = String.valueOf(body.getOrDefault("content", body.getOrDefault("description", "")));

        Order confirmed = orderService.autoConfirmByTransfer(amount, content);
        if (confirmed != null) {
            log.info("Tự xác nhận đơn {} nhờ webhook ngân hàng ({} ₫).", confirmed.getOrderCode(), amount);
            orderNotifier.confirmed(confirmed);
        } else {
            log.info("Webhook ngân hàng: {} ₫ \"{}\" - không khớp duy nhất đơn nào, để nhân viên xử lý tay.", amount, content);
        }
        return ResponseEntity.ok(Map.of("success", true, "matched", confirmed != null));
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try {
            return v == null ? 0 : Math.round(Double.parseDouble(String.valueOf(v).trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
