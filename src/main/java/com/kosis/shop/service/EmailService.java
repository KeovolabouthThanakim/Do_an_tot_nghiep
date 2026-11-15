package com.kosis.shop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gửi email (văn bản thuần) qua SMTP. Chạy NỀN (@Async) nên request của khách không phải chờ máy chủ mail.
 * Lỗi gửi chỉ được ghi log, không làm hỏng đơn hàng / thao tác đang làm.
 *
 * Chưa cấu hình SMTP (spring.mail.host): nội dung email được IN RA CONSOLE thay vì gửi đi,
 * để khi đang phát triển vẫn lấy được link đặt lại mật khẩu. Cấu hình ở application.properties / local.properties.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;

    /** Địa chỉ người gửi hiển thị (để trống = dùng spring.mail.username) */
    @Value("${kosis.mail.from:}")
    private String from;

    @Value("${spring.mail.username:}")
    private String smtpUser;

    public EmailService(ObjectProvider<JavaMailSender> senderProvider) {
        this.senderProvider = senderProvider;
    }

    /** Đã cấu hình SMTP chưa? */
    public boolean isConfigured() {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) return false;
        return !(sender instanceof JavaMailSenderImpl impl) || (impl.getHost() != null && !impl.getHost().isBlank());
    }

    @Async
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) return;
        if (!isConfigured()) {
            log.warn("\n[EMAIL - chưa cấu hình SMTP nên chỉ in ra console]\nTo: {}\nSubject: {}\n{}\n", to, subject, body);
            return;
        }
        try {
            if (senderProvider.getObject() instanceof JavaMailSenderImpl impl) {
                log.info("Đang gửi email \"{}\" tới {} qua {}:{} (tài khoản {})", subject, to, impl.getHost(), impl.getPort(), impl.getUsername());
            }
            SimpleMailMessage msg = new SimpleMailMessage();
            String sender = (from != null && !from.isBlank()) ? from : smtpUser;
            if (sender != null && !sender.isBlank()) msg.setFrom(sender);
            msg.setTo(to.trim());
            msg.setSubject(subject);
            msg.setText(body);
            senderProvider.getObject().send(msg);
            log.info("Đã gửi email tới {} thành công.", to);
        } catch (Exception e) {
            // In đủ nguyên nhân (sai mật khẩu ứng dụng, sai host/port, bị chặn mạng...)
            log.error("Không gửi được email tới " + to, e);
        }
    }
}
