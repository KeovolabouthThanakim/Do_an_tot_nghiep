package com.kosis.shop.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bản tiếng Anh phải đủ key như bản tiếng Việt, cùng số tham số {n}, và mọi thông báo phải là MessageFormat hợp lệ. */
class MessagesBundleTest {

    private static Properties load(String name) throws Exception {
        try (InputStream in = MessagesBundleTest.class.getResourceAsStream("/" + name)) {
            assertTrue(in != null, name + " không có trên classpath");
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        }
    }

    @Test
    void englishHasSameKeysAsVietnamese() throws Exception {
        Set<String> vi = new TreeSet<>(load("messages.properties").stringPropertyNames());
        Set<String> en = new TreeSet<>(load("messages_en.properties").stringPropertyNames());
        Set<String> missingEn = new TreeSet<>(vi); missingEn.removeAll(en);
        Set<String> extraEn = new TreeSet<>(en); extraEn.removeAll(vi);
        assertTrue(missingEn.isEmpty(), "Thiếu bản tiếng Anh: " + missingEn);
        assertTrue(extraEn.isEmpty(), "Key tiếng Anh thừa (không có bản tiếng Việt): " + extraEn);
    }

    @Test
    void messagesAreValidMessageFormatWithSameParameterCount() throws Exception {
        Properties vi = load("messages.properties");
        Properties en = load("messages_en.properties");
        for (String key : vi.stringPropertyNames()) {
            MessageFormat mv = new MessageFormat(vi.getProperty(key), Locale.forLanguageTag("vi"));
            MessageFormat me = new MessageFormat(en.getProperty(key), Locale.ENGLISH);
            assertEquals(mv.getFormatsByArgumentIndex().length, me.getFormatsByArgumentIndex().length,
                    "Số tham số khác nhau giữa vi và en ở key " + key);
        }
    }
}
