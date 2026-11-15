package com.kosis.shop.repository;

import com.kosis.shop.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findTop100ByOrderByCreatedAtDescIdDesc();

    List<StockMovement> findTop200ByProductIdOrderByCreatedAtDescIdDesc(Long productId);
}
