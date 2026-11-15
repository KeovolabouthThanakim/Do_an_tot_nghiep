package com.kosis.shop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Chặn sớm POST /login và POST /register khi 1 IP thử quá nhiều lần (xem {@link AuthRateLimiter}).
 * KHÔNG đánh dấu @Component: filter này được gắn thẳng vào chuỗi Spring Security (SecurityConfig),
 * nếu đăng ký thêm như servlet filter thường thì sẽ chạy sau Spring Security và không chặn được gì.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimiter limiter;

    public AuthRateLimitFilter(AuthRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            String ip = request.getRemoteAddr();

            if ("/login".equals(path) && limiter.isLoginBlocked(ip, request.getParameter("username"))) {
                response.sendRedirect(request.getContextPath() + "/login?error=ratelimit");
                return;
            }
            if ("/register".equals(path) && limiter.registerAttemptBlocked(ip)) {
                response.sendRedirect(request.getContextPath() + "/register?error=ratelimit");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
