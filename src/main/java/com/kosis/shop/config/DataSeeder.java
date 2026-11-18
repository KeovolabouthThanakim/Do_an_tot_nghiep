package com.kosis.shop.config;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.User;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

/**
 * Khởi tạo dữ liệu tối thiểu khi app chạy:
 *  - tạo tài khoản ADMIN đầu tiên nếu hệ thống chưa có ADMIN nào;
 *  - chuyển tồn kho dùng chung sang tồn kho riêng từng màu (dữ liệu cũ).
 * KHÔNG tạo sản phẩm mẫu - sản phẩm do admin thêm ở /dashboard/kho-hang.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Tài khoản admin đầu tiên - chỉ được tạo khi trong hệ thống CHƯA có ADMIN nào */
    @Value("${kosis.admin.username:admin}")
    private String adminUsername;

    /** Mật khẩu admin đọc từ config / biến môi trường KOSIS_ADMIN_PASSWORD; để trống thì app tự sinh và in ra console 1 lần */
    @Value("${kosis.admin.password:}")
    private String adminPassword;

    public DataSeeder(ProductRepository productRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Tạo admin mặc định nếu chưa có ADMIN nào (kể cả đang bị khoá thì vẫn tính là "đã có",
     * để không tự tạo thêm tài khoản ngoài ý muốn). Mật khẩu KHÔNG nằm trong code.
     */
    private void seedDefaultAdmin() {
        if (userRepository.countByRole("ADMIN") > 0) {
            return;
        }
        String username = adminUsername == null || adminUsername.isBlank() ? "admin" : adminUsername.trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            System.out.println("[KOSIS] Chưa có ADMIN nhưng tên đăng nhập \"" + username
                    + "\" đã tồn tại - không tạo tự động. Hãy đổi kosis.admin.username hoặc đổi role tài khoản đó trong database.");
            return;
        }
        boolean generated = adminPassword == null || adminPassword.isBlank();
        String rawPassword = generated ? randomPassword() : adminPassword;
        if (rawPassword.length() < 6) {
            System.out.println("[KOSIS] kosis.admin.password phải có ít nhất 6 ký tự - chưa tạo admin mặc định.");
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setFullName("Quản trị viên");
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        userRepository.save(admin);
        System.out.println("[KOSIS] Đã tạo tài khoản ADMIN mặc định: " + username
                + (generated ? " / mật khẩu tạm (chỉ hiện 1 lần, hãy đổi ngay): " + rawPassword : " (mật khẩu lấy từ kosis.admin.password)"));
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Sản phẩm có màu nhưng tồn kho còn dùng CHUNG cho mọi màu (dữ liệu trước khi có tồn kho theo màu) được chuyển sang
     * kho riêng từng màu: tồn kho của mỗi size chia đều cho các màu, TỔNG KHÔNG ĐỔI. Admin chỉnh lại số từng màu bằng
     * phiếu nhập (tăng) / trang Tồn kho (giảm). Chạy lại nhiều lần vẫn an toàn (sản phẩm đã chia thì bỏ qua).
     */
    private void migrateColorStock() {
        List<Product> legacy = productRepository.findAll().stream().filter(Product::isLegacyColorStock).toList();
        if (legacy.isEmpty()) {
            return;
        }
        for (Product p : legacy) {
            p.convertToPerColorStock();
        }
        productRepository.saveAll(legacy);
        System.out.println("[KOSIS] Đã chuyển " + legacy.size() + " sản phẩm có màu sang tồn kho riêng từng màu (chia đều, tổng tồn kho không đổi).");
    }

    @Override
    public void run(String... args) {
        seedDefaultAdmin();
        migrateColorStock();
        // Không còn tự tạo sản phẩm mẫu: sản phẩm chỉ được thêm qua trang quản trị /dashboard/kho-hang
    }
}
