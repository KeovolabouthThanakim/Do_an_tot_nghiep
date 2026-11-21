package com.kosis.shop.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Không có Spring/MessageSource (như các unit test khác): I18n.t trả về bản tiếng Việt mặc định, đã thay tham số. */
class I18nTest {

    @Test
    void fallsBackToVietnameseDefault() {
        assertEquals("Đã thêm vào giỏ hàng", I18n.t("cart.added", "Đã thêm vào giỏ hàng"));
    }

    @Test
    void formatsArguments() {
        assertEquals("Đã đổi sang size M.", I18n.t("cart.size.changed", "Đã đổi sang size {0}.", "M"));
    }
}
