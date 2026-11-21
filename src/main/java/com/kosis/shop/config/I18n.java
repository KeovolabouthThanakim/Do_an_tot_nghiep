package com.kosis.shop.config;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * Dịch thông báo phía máy chủ (flash message, lỗi nghiệp vụ, email...) theo ngôn ngữ của request hiện tại.
 *
 * Cách dùng:  I18n.t("cart.added", "Đã thêm vào giỏ hàng")
 *             I18n.t("cart.size.changed", "Đã đổi sang size {0}.", newSize)
 *
 *  - Tham số 2 là bản TIẾNG VIỆT mặc định (cũng được ghi trong messages.properties). Dùng cú pháp MessageFormat:
 *    {0}, {1}... cho tham số và '' cho dấu nháy đơn.
 *  - Bản tiếng Anh nằm trong messages_en.properties (cùng key).
 *  - Không có request (tác vụ nền, email gửi từ scheduler, unit test không khởi tạo Spring) => dùng tiếng Việt.
 */
@Component
public class I18n {

    public static final Locale VI = Locale.forLanguageTag("vi");
    public static final Locale EN = Locale.ENGLISH;

    private static volatile MessageSource source;

    public I18n(MessageSource messageSource) {
        source = messageSource;
    }

    /** Ngôn ngữ đang dùng: "en" hoặc "vi" (mặc định vi). */
    public static Locale current() {
        if (LocaleContextHolder.getLocaleContext() == null) return VI;
        Locale l = LocaleContextHolder.getLocale();
        return l != null && "en".equals(l.getLanguage()) ? EN : VI;
    }

    public static boolean isEnglish() {
        return "en".equals(current().getLanguage());
    }

    public static String t(String code, String viDefault, Object... args) {
        return t(current(), code, viDefault, args);
    }

    public static String t(Locale locale, String code, String viDefault, Object... args) {
        MessageSource ms = source;
        if (ms != null) {
            try {
                String s = ms.getMessage(code, args, viDefault, locale);
                if (s != null) return s;
            } catch (NoSuchMessageException | IllegalArgumentException ignored) {
                // rơi xuống bản mặc định bên dưới
            }
        }
        return format(viDefault, args);
    }

    private static String format(String pattern, Object... args) {
        try {
            return new MessageFormat(pattern, VI).format(args == null ? new Object[0] : args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }
}
