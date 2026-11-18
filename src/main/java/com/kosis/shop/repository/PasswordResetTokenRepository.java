package com.kosis.shop.repository;

import com.kosis.shop.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Mã mới nhất của 1 tài khoản (để giới hạn tần suất yêu cầu) */
    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /** Gọi trong @Transactional */
    void deleteByUserId(Long userId);

    /** Dọn mã đã hết hạn. Gọi trong @Transactional */
    void deleteByExpiresAtBefore(LocalDateTime time);
}
