package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Tin nhắn khách gửi từ form /contact. Nhân viên xem và đánh dấu "đã xử lý" ở /dashboard/lien-he. */
@Entity
@Table(name = "contact_messages")
public class ContactMessage implements java.io.Serializable {

    public static final int MAX_NAME = 120;
    public static final int MAX_EMAIL = 190;
    public static final int MAX_SUBJECT = 200;
    public static final int MAX_MESSAGE = 3000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_NAME)
    private String name;

    @Column(nullable = false, length = MAX_EMAIL)
    private String email;

    @Column(length = MAX_SUBJECT)
    private String subject;

    @Column(nullable = false, length = MAX_MESSAGE)
    private String message;

    /** Username nếu khách đã đăng nhập lúc gửi (null = khách vãng lai) */
    @Column(length = 100)
    private String username;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean handled = false;

    @Column(name = "handled_by", length = 100)
    private String handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    public ContactMessage() {
    }

    public String getCreatedAtText() {
        return createdAt == null ? "" : createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public String getHandledAtText() {
        return handledAt == null ? "" : handledAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isHandled() { return handled; }
    public void setHandled(boolean handled) { this.handled = handled; }
    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
}
