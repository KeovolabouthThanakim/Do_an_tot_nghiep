package com.kosis.shop.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sau khi đăng nhập thành công, kiểm tra role của user để quyết định
 * chuyển hướng đi đâu:
 *   - ROLE_ADMIN hoặc ROLE_NHANVIEN -> vào trang quản trị (/dashboard)
 *   - ROLE_USER (khách hàng)        -> vào trang chủ website (/), TRỪ KHI trước đó đang
 *     định vào trang cần đăng nhập (VD /thanh-toan) thì quay lại đúng trang đó
 */
@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {

        boolean isStaff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_NHANVIEN"));

        SavedRequest saved = requestCache.getRequest(request, response);
        if (!isStaff && saved != null) {
            requestCache.removeRequest(request, response);
            response.sendRedirect(saved.getRedirectUrl());
            return;
        }

        String redirectUrl = isStaff ? "/dashboard" : "/";
        response.sendRedirect(request.getContextPath() + redirectUrl);
    }
}