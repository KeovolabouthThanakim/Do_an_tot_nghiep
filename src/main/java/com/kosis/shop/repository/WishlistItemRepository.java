package com.kosis.shop.repository;

import com.kosis.shop.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUsernameOrderByCreatedAtDesc(String username);

    long countByUsername(String username);

    boolean existsByUsernameAndProductId(String username, Long productId);

    @Transactional
    long deleteByUsernameAndProductId(String username, Long productId);

    /** Xoá tài khoản: bỏ luôn danh sách yêu thích (tránh người đăng ký trùng tên sau này thấy dữ liệu cũ) */
    @Transactional
    long deleteByUsername(String username);

    /** Đổi tên đăng nhập: danh sách yêu thích đi theo tên mới */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WishlistItem w set w.username = :newName where w.username = :oldName")
    int renameUser(@Param("oldName") String oldName, @Param("newName") String newName);
}
