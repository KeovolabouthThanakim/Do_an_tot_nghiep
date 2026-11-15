package com.kosis.shop.repository;

import com.kosis.shop.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndHiddenFalseOrderByCreatedAtDesc(Long productId);

    Optional<Review> findByProductIdAndUsername(Long productId, String username);

    long countByProductIdAndHiddenFalse(Long productId);

    @Query("select avg(r.rating) from Review r where r.productId = :pid and r.hidden = false")
    Double averageRating(@Param("pid") Long productId);

    List<Review> findAllByOrderByCreatedAtDesc();
}
