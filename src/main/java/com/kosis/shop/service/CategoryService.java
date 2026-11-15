package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.ProductCategory;
import com.kosis.shop.repository.ProductCategoryRepository;
import com.kosis.shop.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Danh mục sản phẩm do ADMIN quản lý (thêm / đổi tên / sắp xếp / ẩn khỏi menu / xoá).
 * Là nguồn duy nhất cho: thanh menu, trang chủ, bộ lọc ở cửa hàng, ô chọn loại ở kho hàng / nhập hàng / nhập CSV
 * và việc kiểm tra loại sản phẩm khi lưu (thay cho danh sách cố định Product.CATEGORIES, nay chỉ là danh sách khởi tạo).
 *
 * Mọi lỗi nghiệp vụ ném IllegalArgumentException với thông báo tiếng Việt để hiện thẳng cho người dùng.
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    public static final int MAX_CATEGORIES = 30;
    public static final int MAX_NAME_LENGTH = 40;
    private static final Pattern NAME_OK = Pattern.compile("^[\\p{L}\\p{M}\\p{N} &/.'+()-]+$");

    /** Một dòng ở trang quản lý (lớp thường có getter để Thymeleaf đọc được) */
    public static final class Item {
        private final long id;
        private final String name;
        private final boolean visible;
        private final long productCount;

        Item(long id, String name, boolean visible, long productCount) {
            this.id = id;
            this.name = name;
            this.visible = visible;
            this.productCount = productCount;
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public boolean isVisible() { return visible; }
        public long getProductCount() { return productCount; }
    }

    private final ProductCategoryRepository repository;
    private final ProductRepository products;
    private final TtlCache<List<ProductCategory>> cache;

    @Autowired
    public CategoryService(ProductCategoryRepository repository, ProductRepository products, Environment env) {
        this(repository, products, env, System::currentTimeMillis);
    }

    /** Dùng trong test: tự điều khiển đồng hồ */
    CategoryService(ProductCategoryRepository repository, ProductRepository products, Environment env, LongSupplier clock) {
        this.repository = repository;
        this.products = products;
        this.cache = new TtlCache<>(() -> List.copyOf(repository.findAllByOrderBySortOrderAscIdAsc()),
                List.of(), TtlCache.refreshMillis(env), clock);
    }

    /** Lần chạy đầu (bảng trống): nạp danh sách mặc định + các loại đang có trên sản phẩm cũ để không mất dữ liệu. */
    @PostConstruct
    void init() {
        try {
            if (repository.count() == 0) {
                LinkedHashSet<String> names = new LinkedHashSet<>(Product.CATEGORIES);
                for (String used : products.findDistinctCategories()) {
                    String c = used == null ? "" : used.trim();
                    if (!c.isEmpty() && names.stream().noneMatch(n -> n.equalsIgnoreCase(c))) {
                        names.add(c);
                    }
                }
                List<ProductCategory> seed = new ArrayList<>();
                int i = 0;
                for (String n : names) {
                    seed.add(new ProductCategory(n, i++, true));
                }
                repository.saveAll(seed);
                log.info("Đã khởi tạo {} danh mục sản phẩm mặc định", seed.size());
            }
        } catch (RuntimeException e) {
            log.warn("Không khởi tạo được bảng danh mục, tạm dùng danh sách mặc định: {}", e.toString());
        }
        cache.reload();
    }

    // ------------------------------------------------------------------ đọc (dùng ở mọi request nên đọc từ bộ nhớ)

    /** Mọi danh mục theo thứ tự (cho ô chọn loại, bộ lọc, kiểm tra). Chưa nạp được thì dùng danh sách mặc định. */
    public List<String> names() {
        List<ProductCategory> all = cache.get();
        if (all.isEmpty()) return Product.CATEGORIES;
        return all.stream().map(ProductCategory::getName).toList();
    }

    /** Các danh mục hiện trên thanh menu và trang chủ. */
    public List<String> menuNames() {
        List<ProductCategory> all = cache.get();
        if (all.isEmpty()) return Product.CATEGORIES;
        return all.stream().filter(ProductCategory::isVisible).map(ProductCategory::getName).toList();
    }

    /** Danh sách cho trang quản lý, kèm số sản phẩm của từng danh mục (đọc thẳng DB). */
    public List<Item> items() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : products.countGroupedByCategory()) {
            if (row[0] == null) continue;
            counts.merge(((String) row[0]).trim().toLowerCase(Locale.ROOT), ((Number) row[1]).longValue(), Long::sum);
        }
        List<Item> out = new ArrayList<>();
        for (ProductCategory c : repository.findAllByOrderBySortOrderAscIdAsc()) {
            out.add(new Item(c.getId(), c.getName(), c.isVisible(), counts.getOrDefault(c.getName().toLowerCase(Locale.ROOT), 0L)));
        }
        return out;
    }

    // ------------------------------------------------------------------ ghi
    // Ghi xong nạp lại bộ nhớ ngay (máy khác tự cập nhật sau tối đa kosis.settings.refresh-seconds giây).

    @Transactional
    public void add(String rawName) {
        String name = cleanName(rawName);
        List<ProductCategory> all = repository.findAllByOrderBySortOrderAscIdAsc();
        if (all.size() >= MAX_CATEGORIES) {
            throw new IllegalArgumentException("Tối đa " + MAX_CATEGORIES + " danh mục. Hãy xoá bớt danh mục không dùng trước khi thêm.");
        }
        if (all.stream().anyMatch(c -> c.getName().equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("Danh mục \"" + name + "\" đã có.");
        }
        int next = all.stream().mapToInt(ProductCategory::getSortOrder).max().orElse(-1) + 1;
        repository.save(new ProductCategory(name, next, true));
        cache.reload();
    }

    /** Đổi tên và cập nhật luôn mọi sản phẩm đang dùng danh mục này. Trả về số sản phẩm đã cập nhật. */
    @Transactional
    public int rename(long id, String rawName) {
        ProductCategory c = find(id);
        String name = cleanName(rawName);
        if (name.equals(c.getName())) return 0;
        for (ProductCategory other : repository.findAllByOrderBySortOrderAscIdAsc()) {
            if (other.getId() != null && other.getId() != id && other.getName().equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("Đã có danh mục \"" + other.getName() + "\".");
            }
        }
        String old = c.getName();
        c.setName(name);
        repository.save(c);
        int updated = products.renameCategory(old, name);
        cache.reload();
        return updated;
    }

    @Transactional
    public void setVisible(long id, boolean visible) {
        ProductCategory c = find(id);
        c.setVisible(visible);
        repository.save(c);
        cache.reload();
    }

    /** delta = -1: lên, +1: xuống */
    @Transactional
    public void move(long id, int delta) {
        List<ProductCategory> all = new ArrayList<>(repository.findAllByOrderBySortOrderAscIdAsc());
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId() != null && all.get(i).getId() == id) index = i;
        }
        if (index < 0) throw new IllegalArgumentException("Không tìm thấy danh mục.");
        MenuOrdering.move(all, index, delta);
        repository.saveAll(all);
        cache.reload();
    }

    /** Chỉ xoá được danh mục không còn sản phẩm nào (nếu không, chỉ nên ẩn khỏi menu). */
    @Transactional
    public void delete(long id) {
        ProductCategory c = find(id);
        long used = products.countByCategoryIgnoreCase(c.getName());
        if (used > 0) {
            throw new IllegalArgumentException("Còn " + used + " sản phẩm thuộc danh mục \"" + c.getName()
                    + "\". Hãy chuyển các sản phẩm sang danh mục khác trước khi xoá (hoặc chỉ ẩn danh mục khỏi menu).");
        }
        if (repository.count() <= 1) {
            throw new IllegalArgumentException("Cần giữ lại ít nhất một danh mục.");
        }
        repository.delete(c);
        cache.reload();
    }

    private ProductCategory find(long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục."));
    }

    // ------------------------------------------------------------------ kiểm tra

    /** Chuẩn hoá (NFC, bỏ ký tự điều khiển, gộp khoảng trắng) và kiểm tra tên danh mục. */
    static String cleanName(String raw) {
        String s = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC);
        s = s.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Tên danh mục không được để trống.");
        }
        if (s.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Tên danh mục tối đa " + MAX_NAME_LENGTH + " ký tự.");
        }
        if (!NAME_OK.matcher(s).matches()) {
            throw new IllegalArgumentException("Tên danh mục chỉ gồm chữ, số, khoảng trắng và các ký tự & / . ' + ( ) -");
        }
        return s;
    }
}
