package com.kosis.shop.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn số lần thử ở trang đăng nhập / đăng ký (chống dò mật khẩu và tạo tài khoản hàng loạt).
 * Lưu trong bộ nhớ (1 server). Nếu chạy nhiều server phía sau load balancer thì cần chuyển sang Redis.
 *
 * - Đăng nhập: chỉ đếm lần SAI mật khẩu. Đăng nhập đúng thì xoá bộ đếm của cặp IP + tên đăng nhập.
 *   Khoá theo (IP + tên đăng nhập) và theo IP chung.
 * - Đăng ký: đếm mọi lần gửi form theo IP.
 *
 * Địa chỉ IP lấy từ request.getRemoteAddr(). Chạy sau reverse proxy (nginx...) thì đặt
 * server.forward-headers-strategy=native để IP là IP khách thật, KHÔNG tự đọc header X-Forwarded-For (dễ bị giả).
 */
@Component
public class AuthRateLimiter {

    private final int loginMaxPerUser;
    private final int loginMaxPerIp;
    private final long loginWindowMs;
    private final int registerMaxPerIp;
    private final long registerWindowMs;

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AuthRateLimiter(
            @Value("${kosis.security.rate-limit.login.max-per-user:5}") int loginMaxPerUser,
            @Value("${kosis.security.rate-limit.login.max-per-ip:20}") int loginMaxPerIp,
            @Value("${kosis.security.rate-limit.login.window-seconds:600}") long loginWindowSeconds,
            @Value("${kosis.security.rate-limit.register.max-per-ip:5}") int registerMaxPerIp,
            @Value("${kosis.security.rate-limit.register.window-seconds:3600}") long registerWindowSeconds) {
        this.loginMaxPerUser = loginMaxPerUser;
        this.loginMaxPerIp = loginMaxPerIp;
        this.loginWindowMs = loginWindowSeconds * 1000;
        this.registerMaxPerIp = registerMaxPerIp;
        this.registerWindowMs = registerWindowSeconds * 1000;
    }

    // ---------------- đăng nhập ----------------

    /** true nếu IP hoặc cặp (IP + tên đăng nhập) đã sai quá số lần cho phép trong khung thời gian */
    public boolean isLoginBlocked(String ip, String username) {
        long now = System.currentTimeMillis();
        return count(userKey(ip, username), now, loginWindowMs) >= loginMaxPerUser
                || count(ipKey("login", ip), now, loginWindowMs) >= loginMaxPerIp;
    }

    @EventListener
    public void onLoginFailure(AuthenticationFailureBadCredentialsEvent event) {
        String ip = remoteIp(event.getAuthentication());
        if (ip == null) return;
        long now = System.currentTimeMillis();
        record(userKey(ip, event.getAuthentication().getName()), now);
        record(ipKey("login", ip), now);
    }

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        String ip = remoteIp(event.getAuthentication());
        if (ip == null) return;
        hits.remove(userKey(ip, event.getAuthentication().getName()));
    }

    // ---------------- đăng ký ----------------

    /** Đếm 1 lần gửi form đăng ký; trả true nếu IP này đã vượt giới hạn (lần này bị chặn) */
    public boolean registerAttemptBlocked(String ip) {
        long now = System.currentTimeMillis();
        String key = ipKey("register", ip);
        if (count(key, now, registerWindowMs) >= registerMaxPerIp) {
            return true;
        }
        record(key, now);
        return false;
    }

    // ---------------- nội bộ ----------------

    private static String userKey(String ip, String username) {
        return "login-user:" + ip + "|" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String kind, String ip) {
        return kind + "-ip:" + ip;
    }

    private static String remoteIp(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof WebAuthenticationDetails d) {
            return d.getRemoteAddress();
        }
        return null;
    }

    private void record(String key, long now) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(now);
        }
    }

    /** Số lần còn nằm trong khung thời gian (đồng thời bỏ các lần đã quá hạn) */
    private int count(String key, long now, long windowMs) {
        Deque<Long> q = hits.get(key);
        if (q == null) return 0;
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
            return q.size();
        }
    }

    /** Dọn các bộ đếm đã hết hạn để map không phình mãi (mỗi 10 phút) */
    @Scheduled(fixedDelay = 600_000)
    void cleanup() {
        long now = System.currentTimeMillis();
        long maxWindow = Math.max(loginWindowMs, registerWindowMs);
        hits.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                return q.isEmpty() || now - q.peekLast() > maxWindow;
            }
        });
    }
}
