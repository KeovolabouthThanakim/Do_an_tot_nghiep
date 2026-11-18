package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Mã đặt lại mật khẩu dùng một lần. Chỉ lưu BĂM SHA-256 của mã (mã gốc chỉ nằm trong link gửi qua email),
 * nên dù lộ database cũng không dùng được để đặt lại mật khẩu.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PasswordResetToken() {
    }

    public PasswordResetToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isExpired() { return expiresAt == null || expiresAt.isBefore(LocalDateTime.now()); }
}
