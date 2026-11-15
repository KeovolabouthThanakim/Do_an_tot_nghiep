package com.kosis.shop.repository;

import com.kosis.shop.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findAllByOrderBySortOrderAscIdAsc();
}
