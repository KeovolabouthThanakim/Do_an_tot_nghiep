package com.kosis.shop.service;

import com.kosis.shop.config.I18n;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lưu ảnh bill chuyển khoản của khách.
 * Lưu ở thư mục "private/slips" (KHÔNG nằm trong /uploads nên không ai mở được bằng đường link công khai) -
 * chỉ chủ đơn và nhân viên/admin xem được thông qua CheckoutController (/thanh-toan/slip/{id}).
 * Kiểm tra bằng nội dung file thật (JPG/PNG/WEBP), không tin vào đuôi file khách gửi lên.
 */
@Service
public class SlipStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Path DIR = Path.of("private", "slips");
    private static final Pattern SAFE_NAME = Pattern.compile("^[0-9a-f\\-]{36}\\.(jpg|png|webp)$");

    /** Lưu file, trả về tên file đã lưu. @throws IllegalArgumentException nếu ảnh không hợp lệ */
    public String save(MultipartFile file) throws IOException {
        String ext = detectExtension(file);
        Files.createDirectories(DIR);
        String name = UUID.randomUUID() + ext;
        file.transferTo(DIR.resolve(name).toAbsolutePath());
        return name;
    }

    private String detectExtension(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(I18n.t("slip.required", "Vui lòng tải lên ảnh bill chuyển khoản."));
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException(I18n.t("slip.too.big", "Ảnh bill quá lớn (tối đa 5MB)."));
        }
        byte[] h = new byte[12];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(h, 0, h.length);
        }
        if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (n >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return ".png";
        }
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return ".webp";
        }
        throw new IllegalArgumentException(I18n.t("slip.bad.type", "Ảnh bill chỉ chấp nhận file JPG, PNG hoặc WEBP."));
    }

    /** Đường dẫn file thật trên đĩa; null nếu tên không hợp lệ hoặc file không tồn tại */
    public Path resolve(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) return null;
        Path p = DIR.resolve(name).toAbsolutePath();
        return Files.isRegularFile(p) ? p : null;
    }

    public void deleteQuietly(String name) {
        Path p = resolve(name);
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // không xoá được thì thôi
        }
    }

    public static String contentTypeOf(String name) {
        if (name == null) return "application/octet-stream";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
