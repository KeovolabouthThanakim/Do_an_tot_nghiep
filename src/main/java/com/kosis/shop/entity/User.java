package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Entity ánh xạ với bảng "users" trong SQL database.
 * Mật khẩu (password) LUÔN được lưu dưới dạng đã mã hoá (BCrypt hash),
 * không bao giờ lưu chuỗi gốc người dùng nhập vào.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Chuỗi hash BCrypt (khoảng 60 ký tự), KHÔNG phải mật khẩu gốc */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    /** Vai trò: USER (khách hàng - vào website), ADMIN hoặc NHANVIEN (vào /dashboard) */
    @Column(nullable = false, length = 20)
    private String role = "USER";

    /** Tài khoản có đang được kích hoạt hay không (khoá tài khoản khi false) */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Ảnh đại diện do admin tải lên, dạng URL "/uploads/avatars/xxx.jpg" (null = dùng ảnh theo email / chữ cái đầu) */
    @Column(name = "avatar_image", length = 255)
    private String avatarImage;

    /** Ngày giờ tạo tài khoản (tài khoản cũ trước khi thêm cột này sẽ để trống) */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public User() {
    }

    public User(String username, String password, String fullName, String email) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
    }

    // ===== Getter / Setter =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** "29/09/2026" hoặc "—" nếu tài khoản cũ chưa có ngày đăng ký */
    public String getCreatedAtText() {
        return createdAt == null ? "—" : createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /**
     * Ảnh đại diện: ảnh admin tải lên, nếu không có thì ảnh theo email (Gravatar): băm SHA-256 của email viết thường.
     * Email chưa đăng ký Gravatar -> trả 404, giao diện tự quay về chữ cái đầu của tên.
     * Trả null nếu tài khoản chưa có email.
     */
    public String getAvatarUrl() {
        if (avatarImage != null && !avatarImage.isBlank()) {
            return avatarImage; // ảnh tải lên được ưu tiên
        }
        if (email == null || email.isBlank()) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return "https://gravatar.com/avatar/" + sb + "?s=160&d=404";
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }

    public String getAvatarImage() {
        return avatarImage;
    }

    public void setAvatarImage(String avatarImage) {
        this.avatarImage = avatarImage;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
