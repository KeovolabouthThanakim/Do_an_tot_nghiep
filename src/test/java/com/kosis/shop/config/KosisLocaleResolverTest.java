package com.kosis.shop.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KosisLocaleResolverTest {

    private final KosisLocaleResolver resolver = new KosisLocaleResolver();

    @Test
    void defaultsToVietnamese() {
        assertEquals("vi", resolver.resolveLocale(new MockHttpServletRequest("GET", "/")).getLanguage());
    }

    @Test
    void usesEnglishFromCookie() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/men");
        req.setRequestURI("/men");
        req.setCookies(new Cookie(KosisLocaleResolver.COOKIE, "en"));
        assertEquals("en", resolver.resolveLocale(req).getLanguage());
    }

    @Test
    void dashboardIsAlwaysVietnamese() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard/don-hang");
        req.setRequestURI("/dashboard/don-hang");
        req.setCookies(new Cookie(KosisLocaleResolver.COOKIE, "en"));
        assertEquals("vi", resolver.resolveLocale(req).getLanguage());
    }

    @Test
    void ignoresUnsupportedLanguage() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse res = new MockHttpServletResponse();
        resolver.setLocale(req, res, Locale.FRENCH);
        assertNull(res.getHeader("Set-Cookie"));
    }

    @Test
    void storesChosenLanguageInCookie() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse res = new MockHttpServletResponse();
        resolver.setLocale(req, res, Locale.ENGLISH);
        String header = res.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains(KosisLocaleResolver.COOKIE + "=en"), header);
    }

    @Test
    void newLanguageAppliesToTheSameRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse res = new MockHttpServletResponse();
        resolver.setLocale(req, res, Locale.ENGLISH);
        assertEquals("en", resolver.resolveLocale(req).getLanguage());
    }
}
