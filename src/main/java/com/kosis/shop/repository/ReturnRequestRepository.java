package com.kosis.shop.repository;

import com.kosis.shop.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    Optional<ReturnRequest> findByOrderId(Long orderId);

    List<ReturnRequest> findByUsernameOrderByCreatedAtDesc(String username);

    List<ReturnRequest> findAllByOrderByCreatedAtDesc();

    /** Số yêu cầu đang chờ nhân viên xử lý (badge ở sidebar) */
    long countByStatusIn(Collection<String> statuses);
}
