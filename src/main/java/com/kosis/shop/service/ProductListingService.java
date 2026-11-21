package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.ProductSpecs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Danh sách sản phẩm có lọc + sắp xếp + phân trang, tất cả chạy trong DB
 * (trước đây: findAll() rồi lọc / sắp xếp trong bộ nhớ ở mọi lần mở trang).
 */
@Service
@Transactional(readOnly = true)
public class ProductListingService {

    /** Các kiểu sắp xếp hợp lệ; giá trị lạ -> "featured" */
    public static final String SORT_FEATURED = "featured";

    private final ProductRepository productRepository;

    @PersistenceContext
    private EntityManager em;

    @Value("${kosis.listing.page-size:20}")
    private int pageSize = 20;

    /** Cài đặt do ADMIN chỉnh trên /dashboard/cai-dat (ghi đè giá trị trong properties). Không có (test) thì dùng pageSize. */
    @Autowired(required = false)
    private SettingsService settings;

    public ProductListingService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public int getPageSize() {
        int size = settings != null ? settings.getInt(SettingsService.LISTING_PAGE_SIZE) : pageSize;
        return size <= 0 ? 12 : size;
    }

    /** Chuẩn hoá tham số sort (null / giá trị lạ -> featured) */
    public static String normalizeSort(String sort) {
        if (sort == null) return SORT_FEATURED;
        return switch (sort) {
            case "price-asc", "price-desc", "newest" -> sort;
            default -> SORT_FEATURED;
        };
    }

    /** Sort của DB; luôn thêm id để thứ tự ổn định giữa các trang */
    public static Sort sortOf(String sort) {
        return switch (normalizeSort(sort)) {
            case "price-asc" -> Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"));
            case "price-desc" -> Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"));
            case "newest" -> Sort.by(Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.asc("id"));
        };
    }

    /**
     * Lấy 1 trang sản phẩm. {@code page} bắt đầu từ 1; số trang sai (<1 hoặc vượt quá) được kéo về trang hợp lệ.
     */
    public Page<Product> find(Specification<Product> base, String category, String sort, int page) {
        Specification<Product> spec = base.and(ProductSpecs.listed());   // không hiện sản phẩm chờ nhập hàng
        if (category != null && !category.isBlank()) {
            spec = spec.and(ProductSpecs.hasCategory(category));
        }
        int size = getPageSize();
        Sort order = sortOf(sort);
        Page<Product> result = productRepository.findAll(spec, PageRequest.of(Math.max(page, 1) - 1, size, order));
        if (result.getTotalPages() > 0 && Math.max(page, 1) > result.getTotalPages()) {
            result = productRepository.findAll(spec, PageRequest.of(result.getTotalPages() - 1, size, order));
        }
        return result;
    }

    /** Các danh mục có trong tập sản phẩm này (để hiện chip lọc), đúng thứ tự {@code order}. */
    public List<String> categoriesOf(Specification<Product> base, List<String> order) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> q = cb.createQuery(String.class);
        Root<Product> root = q.from(Product.class);
        q.select(root.<String>get("category")).distinct(true);
        Predicate p = base.and(ProductSpecs.listed()).toPredicate(root, q, cb);
        if (p != null) q.where(p);
        List<String> found = em.createQuery(q).getResultList();

        List<String> present = new ArrayList<>();
        for (String c : order) {
            boolean has = found.stream().anyMatch(f -> f != null && f.equalsIgnoreCase(c));
            if (has) present.add(c);
        }
        return present;
    }

    /**
     * Dãy số trang để hiện: luôn có trang đầu, trang cuối và 2 trang quanh trang hiện tại;
     * số 0 nghĩa là dấu "…". Vd trang 6/20 -> 1, 0, 4, 5, 6, 7, 8, 0, 20.
     */
    public static List<Integer> pageWindow(int current, int total) {
        List<Integer> out = new ArrayList<>();
        if (total <= 1) return out;
        int from = Math.max(2, current - 2);
        int to = Math.min(total - 1, current + 2);
        out.add(1);
        if (from > 2) out.add(0);
        for (int i = from; i <= to; i++) out.add(i);
        if (to < total - 1) out.add(0);
        out.add(total);
        return out;
    }
}
