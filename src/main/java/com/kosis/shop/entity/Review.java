package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Đánh giá của khách cho một sản phẩm (mỗi khách chỉ có 1 đánh giá / sản phẩm, được sửa lại). */
@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(name = "uk_review_user_product", columnNames = {"username", "product_id"}))
public class Review {

    public static final int MAX_COMMENT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 50)
    private String username;

    /** 1-5 sao */
    @Column(nullable = false)
    private int rating;

    @Column(length = MAX_COMMENT)
    private String comment;

    /** Nhân viên đã ẩn (nội dung không phù hợp) - không hiện và không tính vào điểm trung bình */
    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Review() {
    }

    /** Tên hiển thị đã che bớt, VD "kh***" (không lộ tên đăng nhập đầy đủ) */
    @Transient
    public String getMaskedName() {
        if (username == null || username.isBlank()) return "Khách";
        return username.substring(0, Math.min(2, username.length())) + "***";
    }

    @Transient
    public String getCreatedAtText() {
        return createdAt == null ? "" : createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
