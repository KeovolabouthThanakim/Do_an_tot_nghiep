package com.kosis.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cho phép ảnh sản phẩm được admin upload (lưu ở thư mục "uploads/products"
 * ngoài classpath) truy cập được qua URL /uploads/products/xxx.jpg
 *
 * LƯU Ý (đã sửa lỗi ảnh bị vỡ): Path.toUri() chỉ thêm dấu "/" ở cuối khi thư mục
 * ĐÃ TỒN TẠI. Nếu chưa có thư mục "uploads" lúc khởi động app thì đường dẫn thiếu
 * "/" và Spring không tìm thấy file nào (ảnh hiện icon vỡ). Vì vậy ở đây tạo thư mục
 * trước và luôn đảm bảo đường dẫn kết thúc bằng "/".
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Ngôn ngữ giao diện (vi/en) lưu trong cookie - xem KosisLocaleResolver. Tên bean PHẢI là "localeResolver". */
    @Bean
    public LocaleResolver localeResolver() {
        return new KosisLocaleResolver();
    }

    /** Đổi ngôn ngữ bằng tham số ?lang=en hoặc ?lang=vi trên bất kỳ trang nào. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor change = new LocaleChangeInterceptor();
        change.setParamName("lang");
        change.setIgnoreInvalidLocale(true);
        registry.addInterceptor(change);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Path.of("uploads").toAbsolutePath();
        try {
            Files.createDirectories(uploadDir.resolve("products"));
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được thư mục upload: " + uploadDir, e);
        }

        String location = uploadDir.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}