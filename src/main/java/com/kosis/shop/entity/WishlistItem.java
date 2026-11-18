package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Một sản phẩm yêu thích của một tài khoản (mỗi tài khoản + sản phẩm chỉ có 1 dòng). */
@Entity
@Table(name = "wishlist_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_user_product", columnNames = {"username", "product_id"}))
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public WishlistItem() {
    }

    public WishlistItem(String username, Long productId) {
        this.username = username;
        this.productId = productId;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
