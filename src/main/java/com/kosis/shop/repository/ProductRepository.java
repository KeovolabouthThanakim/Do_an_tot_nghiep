package com.kosis.shop.repository;

import com.kosis.shop.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findBySection(String section);

    boolean existsByCode(String code);

    /** Sản phẩm tồn tại VÀ đang bán (không ở trạng thái "chờ nhập hàng"). */
    @Query("select count(p) > 0 from Product p where p.id = :id and (p.pendingImport is null or p.pendingImport = false)")
    boolean existsListedById(@Param("id") Long id);

    /** Chỉ lấy sản phẩm đang bán (dùng cho cửa hàng: trang chủ, liên quan, wishlist...). */
    default List<Product> findAllListed() {
        return findAll(ProductSpecs.listed());
    }

    /** Lấy theo id nhưng chỉ khi đang bán. */
    default Optional<Product> findListedById(Long id) {
        return findById(id).filter(Product::isListed);
    }

    boolean existsByCodeStartingWith(String prefix);

    /** Số sản phẩm đang thuộc một danh mục (không phân biệt hoa thường, tính cả sản phẩm chờ nhập hàng). */
    long countByCategoryIgnoreCase(String category);

    /** Số sản phẩm theo từng tên danh mục: mỗi dòng là [tên danh mục, số sản phẩm]. */
    @Query("select p.category, count(p) from Product p where p.category is not null group by p.category")
    List<Object[]> countGroupedByCategory();

    /** Các tên danh mục đang được sản phẩm dùng (để không làm mất danh mục cũ khi khởi tạo bảng danh mục). */
    @Query("select distinct p.category from Product p where p.category is not null")
    List<String> findDistinctCategories();

    /** Đổi tên danh mục trên mọi sản phẩm đang dùng nó (không phân biệt hoa thường). Trả về số sản phẩm đã đổi. */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.category = :to where lower(p.category) = lower(:from)")
    int renameCategory(@Param("from") String from, @Param("to") String to);

    /**
     * Lấy sản phẩm và KHOÁ dòng dữ liệu (SELECT ... FOR UPDATE) cho tới hết transaction.
     * Dùng khi trừ / hoàn tồn kho để 2 khách đặt cùng lúc món cuối cùng không làm kho bị âm hoặc bán lố.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
