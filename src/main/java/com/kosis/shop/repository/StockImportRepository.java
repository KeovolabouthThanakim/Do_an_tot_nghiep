package com.kosis.shop.repository;

import com.kosis.shop.entity.StockImport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockImportRepository extends JpaRepository<StockImport, Long> {

    /** Toàn bộ phiếu (mới nhất trước), nạp luôn các dòng hàng để trang danh sách không bị N+1 truy vấn */
    @EntityGraph(attributePaths = "items")
    List<StockImport> findAllByOrderByImportDateDescIdDesc();

    /** Một phiếu kèm các dòng hàng (dùng cho phiếu in / xuất PDF) */
    @EntityGraph(attributePaths = "items")
    Optional<StockImport> findWithItemsById(Long id);

    /** Chỉ 2 cột (sản phẩm, nguồn), mới nhất trước - đủ để biết "nguồn nhập gần nhất" mà không nạp cả phiếu */
    interface ProductSupplier {
        Long getProductId();
        String getSupplier();
    }

    @Query("select i.productId as productId, s.supplier as supplier from StockImportItem i join i.stockImport s "
            + "where i.productId is not null order by s.importDate desc, s.id desc")
    List<ProductSupplier> findProductSuppliersNewestFirst();
}
