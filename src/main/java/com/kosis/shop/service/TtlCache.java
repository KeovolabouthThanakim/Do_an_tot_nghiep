package com.kosis.shop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Bản chụp dữ liệu giữ trong bộ nhớ, tự nạp lại từ nguồn (DB) sau mỗi kosis.settings.refresh-seconds giây
 * (mặc định 30; 0 = tắt) để nhiều máy chủ cùng chạy vẫn thấy thay đổi của nhau mà không cần khởi động lại.
 * Chỉ MỘT luồng nạp lại tại một thời điểm, các luồng khác dùng bản đang có. Nạp lỗi thì giữ bản cũ.
 */
public final class TtlCache<T> {

    private static final Logger log = LoggerFactory.getLogger(TtlCache.class);
    private static final long DEFAULT_SECONDS = 30;

    private final Supplier<T> loader;
    private final LongSupplier clock;
    private final long refreshMillis;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile T value;
    private volatile long loadedAt;

    public TtlCache(Supplier<T> loader, T initial, long refreshMillis, LongSupplier clock) {
        this.loader = loader;
        this.value = initial;
        this.refreshMillis = refreshMillis;
        this.clock = clock;
    }

    /** Đọc kosis.settings.refresh-seconds (dùng chung cho mọi bộ nhớ đệm cài đặt / menu), trả về mili-giây. */
    public static long refreshMillis(Environment env) {
        String v = env.getProperty("kosis.settings.refresh-seconds");
        long seconds = DEFAULT_SECONDS;
        if (v != null && !v.isBlank()) {
            try {
                seconds = Math.max(0, Long.parseLong(v.trim()));
            } catch (NumberFormatException ignored) {
                // giữ mặc định
            }
        }
        return seconds * 1000L;
    }

    public T get() {
        if (refreshMillis > 0 && clock.getAsLong() - loadedAt >= refreshMillis && refreshing.compareAndSet(false, true)) {
            try {
                reload();
            } finally {
                refreshing.set(false);
            }
        }
        return value;
    }

    public void reload() {
        loadedAt = clock.getAsLong();
        try {
            value = loader.get();
        } catch (RuntimeException e) {
            log.warn("Không nạp lại được dữ liệu menu/danh mục từ DB, giữ bản cũ: {}", e.toString());
        }
    }
}
