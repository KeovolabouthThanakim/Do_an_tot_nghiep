package com.kosis.shop.service;

import com.kosis.shop.entity.MenuLink;
import com.kosis.shop.repository.MenuLinkRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Liên kết ADMIN thêm vào thanh menu cửa hàng (vd "Bộ sưu tập Thu Đông" -> /collections?category=Áo khoác).
 * Chỉ cho phép đường dẫn trong cửa hàng (/...) hoặc địa chỉ http(s):// - không nhận javascript: hay data:.
 */
@Service
public class MenuLinkService {

    public static final int MAX_LINKS = 8;
    public static final int MAX_LABEL_LENGTH = 40;
    public static final int MAX_URL_LENGTH = 300;
    private static final Pattern EXTERNAL = Pattern.compile("(?i)^https?://[^/?#\\s]+.*$");

    private final MenuLinkRepository repository;
    private final TtlCache<List<MenuLink>> cache;

    @Autowired
    public MenuLinkService(MenuLinkRepository repository, Environment env) {
        this(repository, env, System::currentTimeMillis);
    }

    /** Dùng trong test: tự điều khiển đồng hồ */
    MenuLinkService(MenuLinkRepository repository, Environment env, LongSupplier clock) {
        this.repository = repository;
        this.cache = new TtlCache<>(() -> List.copyOf(repository.findAllByOrderBySortOrderAscIdAsc()),
                List.of(), TtlCache.refreshMillis(env), clock);
    }

    @PostConstruct
    void init() {
        cache.reload();
    }

    // ------------------------------------------------------------------ đọc

    /** Liên kết đang bật, theo thứ tự (hiện trên thanh menu; đọc từ bộ nhớ). */
    public List<MenuLink> visibleLinks() {
        return cache.get().stream().filter(MenuLink::isVisible).toList();
    }

    /** Mọi liên kết cho trang quản lý (đọc thẳng DB). */
    public List<MenuLink> items() {
        return repository.findAllByOrderBySortOrderAscIdAsc();
    }

    // ------------------------------------------------------------------ ghi

    @Transactional
    public void add(String rawLabel, String rawUrl) {
        String label = cleanLabel(rawLabel);
        String url = cleanUrl(rawUrl);
        List<MenuLink> all = repository.findAllByOrderBySortOrderAscIdAsc();
        if (all.size() >= MAX_LINKS) {
            throw new IllegalArgumentException("Tối đa " + MAX_LINKS + " liên kết thêm. Hãy xoá bớt trước khi thêm.");
        }
        int next = all.stream().mapToInt(MenuLink::getSortOrder).max().orElse(-1) + 1;
        repository.save(new MenuLink(label, url, next, true));
        cache.reload();
    }

    @Transactional
    public void update(long id, String rawLabel, String rawUrl) {
        MenuLink l = find(id);
        // kiểm tra cả hai trước rồi mới gán, để lỗi ở một ô không làm thay đổi ô kia
        String label = cleanLabel(rawLabel);
        String url = cleanUrl(rawUrl);
        l.setLabel(label);
        l.setUrl(url);
        repository.save(l);
        cache.reload();
    }

    @Transactional
    public void setVisible(long id, boolean visible) {
        MenuLink l = find(id);
        l.setVisible(visible);
        repository.save(l);
        cache.reload();
    }

    /** delta = -1: lên, +1: xuống */
    @Transactional
    public void move(long id, int delta) {
        List<MenuLink> all = new ArrayList<>(repository.findAllByOrderBySortOrderAscIdAsc());
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId() != null && all.get(i).getId() == id) index = i;
        }
        if (index < 0) throw new IllegalArgumentException("Không tìm thấy liên kết.");
        MenuOrdering.move(all, index, delta);
        repository.saveAll(all);
        cache.reload();
    }

    @Transactional
    public void delete(long id) {
        repository.delete(find(id));
        cache.reload();
    }

    private MenuLink find(long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy liên kết."));
    }

    // ------------------------------------------------------------------ kiểm tra

    static String cleanLabel(String raw) {
        String s = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC);
        s = s.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Tên hiển thị của liên kết không được để trống.");
        }
        if (s.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Tên hiển thị tối đa " + MAX_LABEL_LENGTH + " ký tự.");
        }
        return s;
    }

    /** Chỉ nhận "/đường-dẫn" trong cửa hàng hoặc "http(s)://..." ; không khoảng trắng, không dấu \ (tránh "/\\evil.com"). */
    static String cleanUrl(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Địa chỉ liên kết không được để trống.");
        }
        if (s.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Địa chỉ liên kết tối đa " + MAX_URL_LENGTH + " ký tự.");
        }
        boolean bad = s.chars().anyMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '\\');
        boolean internal = s.startsWith("/") && !s.startsWith("//");
        if (bad || !(internal || EXTERNAL.matcher(s).matches())) {
            throw new IllegalArgumentException("Địa chỉ liên kết phải bắt đầu bằng / (trang trong cửa hàng, vd /sale) "
                    + "hoặc https:// (trang bên ngoài) và không chứa khoảng trắng.");
        }
        return s;
    }
}
