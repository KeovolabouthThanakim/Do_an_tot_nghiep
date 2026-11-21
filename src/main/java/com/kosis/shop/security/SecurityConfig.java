package com.kosis.shop.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Content-Security-Policy: chỉ cho phép tải tài nguyên từ chính site + các CDN mà template đang dùng
     * (jsDelivr: Bootstrap Icons / Chart.js / qrcode-generator; Google Fonts; img.vietqr.io cho mã QR).
     * 'unsafe-inline' cho script/style vẫn cần vì template còn nhiều <script> / onclick / style="" nội tuyến.
     * Muốn siết chặt hơn: chuyển script nội tuyến sang file .js (hoặc dùng nonce) rồi bỏ 'unsafe-inline'.
     */
    private static final String CSP =
            "default-src 'self'; "
          + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
          + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; "
          + "font-src 'self' data: https://cdn.jsdelivr.net https://fonts.gstatic.com; "
          + "img-src 'self' data: blob: https:; "          // https: vì ảnh sản phẩm có thể là URL ngoài, QR từ img.vietqr.io
          + "connect-src 'self'; "
          + "object-src 'none'; "
          + "base-uri 'self'; "
          + "form-action 'self'; "
          + "frame-ancestors 'none'";

    private final AuthenticationSuccessHandler roleBasedAuthenticationSuccessHandler;
    private final AuthRateLimiter authRateLimiter;

    public SecurityConfig(RoleBasedAuthenticationSuccessHandler roleBasedAuthenticationSuccessHandler,
                          AuthRateLimiter authRateLimiter) {
        this.roleBasedAuthenticationSuccessHandler = roleBasedAuthenticationSuccessHandler;
        this.authRateLimiter = authRateLimiter;
    }

    /**
     * BCrypt: thuật toán mã hoá 1 chiều (không thể giải mã ngược lại),
     * dùng để mã hoá mật khẩu trước khi lưu vào SQL và để so sánh khi đăng nhập.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            // true = bắt mọi request phải qua HTTPS (bật ở profile prod, khi chạy sau reverse proxy có TLS)
            @Value("${kosis.security.require-https:false}") boolean requireHttps,
            // true = CSP chỉ báo cáo vi phạm trong console trình duyệt, KHÔNG chặn (dùng để thử trước khi bật thật)
            @Value("${kosis.security.csp.report-only:false}") boolean cspReportOnly) throws Exception {
        // Spring Security 6 mặc định nạp CSRF token "lười" (chỉ khi form đầu tiên cần).
        // Trang có nhiều <form th:action> (như /san-pham) sẽ khiến session bị tạo giữa lúc
        // đang render -> response đã commit -> lỗi "Cannot create a session after the
        // response has been committed". Đặt tên attribute = null để nạp token NGAY trong
        // CsrfFilter (trước khi view bắt đầu ghi ra), session/cookie luôn được tạo sớm.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            // Giới hạn số lần thử ở POST /login và POST /register (chống dò mật khẩu / tạo tài khoản hàng loạt)
            .addFilterBefore(new AuthRateLimitFilter(authRateLimiter), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Gửi đánh giá sản phẩm: bắt buộc đăng nhập (xem đánh giá thì ai cũng xem được - luật /san-pham/** bên dưới)
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/san-pham/*/danh-gia").authenticated()
                // Webhook ngân hàng (SePay/Casso): không có session, tự xác thực bằng API key trong controller
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/payment/webhook").permitAll()
                // Cho phép truy cập công khai: trang chủ, sản phẩm, tài nguyên tĩnh, trang login/register
                // !!! Route MỚI mà khách chưa đăng nhập cũng vào được thì PHẢI thêm vào danh sách này,
                // !!! nếu không sẽ bị yêu cầu đăng nhập (xem anyRequest().authenticated() ở cuối).
                .requestMatchers(
                        "/", "/san-pham", "/san-pham/**", "/css/**", "/js/**", "/images/**", "/uploads/**",
                        "/gio-hang", "/gio-hang/**", "/robots.txt", "/sitemap.xml", "/favicon.ico",
                        "/login", "/register", "/register/**", "/error",
                        "/men", "/women", "/kids", "/collections", "/sale", "/gifts", "/search", "/wishlist",
                        "/about", "/danh-gia", "/our-story", "/contact", "/shipping", "/returns", "/faq", "/privacy", "/terms",
                        "/newsletter", "/forgot-password", "/reset-password"
                ).permitAll()
                // ----- Quyền riêng của ADMIN (đặt TRƯỚC luật chung của /dashboard/**; chặn ở server, không chỉ ẩn nút) -----
                // Trang quản lý nhân viên/admin: chỉ ADMIN
                .requestMatchers("/dashboard/nhan-vien", "/dashboard/nhan-vien/**").hasRole("ADMIN")
                // Quản lý mã giảm giá: chỉ ADMIN
                .requestMatchers("/dashboard/voucher", "/dashboard/voucher/**").hasRole("ADMIN")
                // Cài đặt hệ thống (phí ship, đổi trả, thông tin cửa hàng, hoá đơn...): chỉ ADMIN
                .requestMatchers("/dashboard/cai-dat", "/dashboard/cai-dat/**").hasRole("ADMIN")
                // Khoá / mở khoá tài khoản khách hàng: chỉ ADMIN (NHANVIEN chỉ được xem)
                .requestMatchers("/dashboard/khach-hang/*/khoa", "/dashboard/khach-hang/*/mo-khoa").hasRole("ADMIN")
                // Xoá sản phẩm: chỉ ADMIN
                .requestMatchers("/dashboard/kho-hang/xoa/**").hasRole("ADMIN")
                // /dashboard chỉ dành cho nhân viên (ADMIN, NHANVIEN) - khách hàng (USER) không vào được
                .requestMatchers("/dashboard", "/dashboard/**").hasAnyRole("ADMIN", "NHANVIEN")
                // Thanh toán: bắt buộc đăng nhập (giỏ hàng /gio-hang thì ai cũng dùng được)
                .requestMatchers("/thanh-toan", "/thanh-toan/**").authenticated()
                // My account (KOSIS): login required
                .requestMatchers("/account", "/account/**").authenticated()
                // Danh sách yêu thích gắn với tài khoản: bắt buộc đăng nhập
                .requestMatchers("/wishlist/api", "/wishlist/api/**").authenticated()
                // Đơn hàng của tôi: bắt buộc đăng nhập (mỗi người chỉ thấy đơn của chính mình)
                .requestMatchers("/don-hang-cua-toi", "/don-hang-cua-toi/**").authenticated()
                // Mọi đường dẫn còn lại: BẮT BUỘC đăng nhập (fail-closed). Route mới quên khai báo ở trên
                // sẽ không bị lộ ra công khai; muốn mở công khai thì thêm vào danh sách permitAll phía trên.
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")                 // trang login tự thiết kế (GET)
                .loginProcessingUrl("/login")         // Spring Security tự xử lý khi submit form (POST)
                .usernameParameter("username")
                .passwordParameter("password")
                // Chuyển hướng theo role: USER -> trang chủ, ADMIN/NHANVIEN -> dashboard
                .successHandler(roleBasedAuthenticationSuccessHandler)
                .failureUrl("/login?error=true")      // sai tài khoản/mật khẩu
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            // Vào /dashboard mà chưa đăng nhập/không đủ quyền -> quay lại trang login
            .exceptionHandling(ex -> ex
                    // chưa đăng nhập mà vào trang cần đăng nhập -> login kèm lời nhắc (?required)
                    .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login?required=true"))
                    .accessDeniedPage("/login?error=denied"))
            // CSRF đang BẬT (các form th:action tự thêm token). Nạp token sớm bằng csrfHandler.
            .csrf(csrf -> csrf
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/api/payment/webhook"))
            // ----- Security headers -----
            .headers(headers -> {
                headers.contentTypeOptions(Customizer.withDefaults());            // X-Content-Type-Options: nosniff
                headers.frameOptions(frame -> frame.deny());                      // chống clickjacking (không trang nào nhúng iframe)
                // HSTS chỉ được gửi khi request đến qua HTTPS nên vô hại ở localhost (http)
                headers.httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000).includeSubDomains(false));
                headers.referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=()"));
                headers.contentSecurityPolicy(csp -> {
                    // Production (bắt buộc HTTPS): tự nâng cấp mọi tài nguyên http:// lên https://
                    csp.policyDirectives(requireHttps ? CSP + "; upgrade-insecure-requests" : CSP);
                    if (cspReportOnly) {
                        csp.reportOnly();
                    }
                });
            });

        // Production: chuyển http -> https. Chỉ bật khi chạy sau reverse proxy có TLS và đã đặt
        // server.forward-headers-strategy=native (xem application-prod.properties), nếu không sẽ bị redirect liên tục.
        if (requireHttps) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        return http.build();
    }
}