package com.kosis.shop.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.servlet.LocaleResolver;

import java.time.Duration;
import java.util.Locale;

/**
 * Lưu ngôn ngữ người dùng chọn (vi / en) trong cookie "kosis-lang".
 *  - Chưa chọn => tiếng Việt (ngôn ngữ chính của cửa hàng).
 *  - Trang quản trị (/dashboard/**) luôn tiếng Việt, bất kể cookie, vì khu vực này chưa có bản dịch.
 *  - Chỉ chấp nhận "vi" và "en"; giá trị khác bị bỏ qua.
 */
public class KosisLocaleResolver implements LocaleResolver {

    public static final String COOKIE = "kosis-lang";
    /** Ngôn ngữ vừa được chọn trong CHÍNH request này (cookie mới chỉ có hiệu lực từ request sau) */
    private static final String ATTR = KosisLocaleResolver.class.getName() + ".LANG";

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        if (isStaffArea(request)) return I18n.VI;
        Object chosen = request.getAttribute(ATTR);
        if (chosen != null) return "en".equals(chosen) ? I18n.EN : I18n.VI;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE.equals(c.getName())) {
                    return "en".equals(c.getValue()) ? I18n.EN : I18n.VI;
                }
            }
        }
        return I18n.VI;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (response == null || locale == null) return;
        String lang = locale.getLanguage();
        if (!"en".equals(lang) && !"vi".equals(lang)) return;   // bỏ qua ngôn ngữ không hỗ trợ
        request.setAttribute(ATTR, lang);   // để trang đang render ngay lúc này cũng đổi ngôn ngữ
        ResponseCookie cookie = ResponseCookie.from(COOKIE, lang)
                .path(request.getContextPath().isEmpty() ? "/" : request.getContextPath())
                .maxAge(Duration.ofDays(365))
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static boolean isStaffArea(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) path = path.substring(ctx.length());
        return path.equals("/dashboard") || path.startsWith("/dashboard/");
    }
}
