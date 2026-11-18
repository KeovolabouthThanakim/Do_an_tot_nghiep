package com.kosis.shop.repository;

import com.kosis.shop.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Locale;

/**
 * Điều kiện lọc sản phẩm chạy thẳng trong DB (thay cho findAll() rồi lọc trong bộ nhớ),
 * ghép được với nhau bằng Specification.and(...) và dùng cùng phân trang.
 */
public final class ProductSpecs {

    private ProductSpecs() {
    }

    /** Không lọc gì (lấy tất cả) */
    public static Specification<Product> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Chỉ sản phẩm đang bán: bỏ các sản phẩm "chờ nhập hàng" (chưa có phiếu nhập nào).
     * Cột NULL (hàng cũ trước khi có tính năng này) được coi là đang bán.
     */
    public static Specification<Product> listed() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.<Boolean>get("pendingImport")),
                cb.isFalse(root.<Boolean>get("pendingImport")));
    }

    /** Đúng giới tính (vd KIDS) */
    public static Specification<Product> genderOnly(String gender) {
        String g = gender.toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.upper(root.<String>get("gender")), g);
    }

    /** Giới tính đó hoặc UNISEX (trang Nam / Nữ) */
    public static Specification<Product> genderOrUnisex(String gender) {
        String g = gender.toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.or(
                cb.equal(cb.upper(root.<String>get("gender")), g),
                cb.equal(cb.upper(root.<String>get("gender")), "UNISEX"));
    }

    /** Đang giảm giá: giá cũ cao hơn giá bán (khớp Product.isOnSale) */
    public static Specification<Product> onSale() {
        return (root, query, cb) -> cb.and(
                cb.greaterThan(root.<Long>get("oldPrice"), root.<Long>get("price")),
                cb.greaterThan(root.<Long>get("price"), 0L));
    }

    /** Danh mục nằm trong danh sách (không phân biệt hoa thường) */
    public static Specification<Product> categoryIn(List<String> categories) {
        List<String> lower = categories.stream().map(c -> c.toLowerCase(Locale.ROOT)).toList();
        return (root, query, cb) -> cb.lower(root.<String>get("category")).in(lower);
    }

    /** Đúng 1 danh mục (không phân biệt hoa thường) */
    public static Specification<Product> hasCategory(String category) {
        String c = category.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.<String>get("category")), c);
    }

    /** Tìm theo từ khoá trong tên, danh mục, mã, mô tả, giới tính */
    public static Specification<Product> keyword(String needle) {
        String pattern = "%" + escapeLike(needle.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.<String>get("name")), pattern, '\\'),
                cb.like(cb.lower(root.<String>get("category")), pattern, '\\'),
                cb.like(cb.lower(root.<String>get("code")), pattern, '\\'),
                cb.like(cb.lower(root.<String>get("description")), pattern, '\\'),
                cb.like(cb.lower(root.<String>get("gender")), pattern, '\\'));
    }

    /** Không khớp gì cả (dùng khi ô tìm kiếm để trống) */
    public static Specification<Product> none() {
        return (root, query, cb) -> cb.disjunction();
    }

    /** Thoát ký tự đặc biệt của LIKE để "50%" hay "a_b" được tìm đúng nghĩa đen */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
