package com.kosis.shop.service;

import com.kosis.shop.entity.PasswordResetToken;
import com.kosis.shop.entity.User;
import com.kosis.shop.repository.PasswordResetTokenRepository;
import com.kosis.shop.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Quên mật khẩu: gửi link đặt lại (hiệu lực 30 phút, dùng 1 lần) qua email đã đăng ký. */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Link còn hiệu lực bao lâu */
    static final Duration TTL = Duration.ofMinutes(30);
    /** Mỗi tài khoản chỉ được yêu cầu mã mới sau khoảng này (chống spam email) */
    static final Duration THROTTLE = Duration.ofMinutes(1);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${kosis.base-url:http://localhost:8080}")
    private String baseUrl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
                                PasswordEncoder passwordEncoder, EmailService emailService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Khách nhập email (hoặc tên đăng nhập). Nếu khớp tài khoản đang hoạt động có email thì gửi link đặt lại.
     * KHÔNG báo cho người gọi biết có tài khoản hay không (tránh dò xem email nào đã đăng ký).
     */
    @Transactional
    public void requestReset(String identifier) {
        String q = identifier == null ? "" : identifier.trim();
        if (q.isEmpty() || q.length() > 150) return;

        tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        List<User> users = new ArrayList<>();
        if (q.contains("@")) {
            users.addAll(userRepository.findByEmailIgnoreCase(q));
        } else {
            userRepository.findByUsername(q).ifPresent(users::add);
        }

        if (users.isEmpty()) {
            // Chỉ ghi vào log của server (không báo cho người dùng) để quản trị viên biết vì sao không có email
            log.info("Quên mật khẩu: không có tài khoản nào khớp với \"{}\" (kiểm tra email này đã đăng ký trong bảng users chưa).", q);
        }
        for (User u : users) {
            if (!u.isEnabled() || u.getEmail() == null || u.getEmail().isBlank()) {
                log.info("Quên mật khẩu: bỏ qua tài khoản \"{}\" (đã bị khoá hoặc chưa có email).", u.getUsername());
                continue;
            }

            Optional<PasswordResetToken> last = tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(u.getId());
            if (last.isPresent() && last.get().getCreatedAt().isAfter(LocalDateTime.now().minus(THROTTLE))) {
                log.info("Quên mật khẩu: tài khoản \"{}\" vừa yêu cầu cách đây chưa tới 1 phút, bỏ qua (chờ 1 phút rồi thử lại).", u.getUsername());
                continue;
            }

            tokenRepository.deleteByUserId(u.getId());   // mỗi tài khoản chỉ có 1 mã còn hiệu lực
            String raw = newToken();
            tokenRepository.save(new PasswordResetToken(u.getId(), sha256(raw), LocalDateTime.now().plus(TTL)));

            String link = trimSlash(baseUrl) + "/reset-password?token=" + raw;
            log.info("Quên mật khẩu: đã tạo link cho tài khoản \"{}\", đang gửi email tới {}", u.getUsername(), u.getEmail());
            emailService.send(u.getEmail(), "KOSIS - Đặt lại mật khẩu",
                    "Xin chào " + (u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername() : u.getFullName()) + ",\n\n"
                            + "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản \"" + u.getUsername() + "\".\n"
                            + "Bấm vào link bên dưới để đặt mật khẩu mới (link có hiệu lực " + TTL.toMinutes() + " phút, chỉ dùng được 1 lần):\n\n"
                            + link + "\n\n"
                            + "Nếu bạn không yêu cầu, hãy bỏ qua email này - mật khẩu của bạn vẫn giữ nguyên.\n\nKOSIS");
        }
    }

    /** Link còn dùng được không (mã tồn tại, chưa hết hạn, tài khoản còn hoạt động) */
    @Transactional(readOnly = true)
    public boolean isValid(String rawToken) {
        return findUser(rawToken).isPresent();
    }

    /**
     * Đặt mật khẩu mới bằng mã trong link. Thành công thì huỷ mã (dùng 1 lần).
     * @return false nếu mã sai / hết hạn / tài khoản bị khoá
     */
    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        Optional<User> found = findUser(rawToken);
        if (found.isEmpty()) return false;
        User u = found.get();
        u.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(u);
        tokenRepository.deleteByUserId(u.getId());
        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            emailService.send(u.getEmail(), "KOSIS - Mật khẩu đã được thay đổi",
                    "Mật khẩu của tài khoản \"" + u.getUsername() + "\" vừa được đặt lại.\n"
                            + "Nếu không phải bạn thực hiện, hãy liên hệ cửa hàng ngay.\n\nKOSIS");
        }
        return true;
    }

    private Optional<User> findUser(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 100) return Optional.empty();
        return tokenRepository.findByTokenHash(sha256(rawToken.trim()))
                .filter(t -> !t.isExpired())
                .flatMap(t -> userRepository.findById(t.getUserId()))
                .filter(User::isEnabled);
    }

    private String newToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trimSlash(String s) {
        String t = s == null ? "" : s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }
}
