package com.kosis.shop.security;

import com.kosis.shop.entity.User;
import com.kosis.shop.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lấy thông tin user từ SQL database (qua UserRepository) để Spring Security
 * dùng cho việc xác thực đăng nhập. Mật khẩu so sánh ở dạng hash (BCrypt),
 * việc so sánh này Spring Security tự làm thông qua PasswordEncoder.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String login = username == null ? "" : username.trim();
        // Đăng nhập bằng TÊN ĐĂNG NHẬP hoặc EMAIL. Tên đăng nhập được ưu tiên trước.
        User user = userRepository.findByUsername(login).orElseGet(() -> {
            if (!login.contains("@")) {
                return null;
            }
            List<User> byEmail = userRepository.findByEmailIgnoreCase(login);
            // Nếu có nhiều tài khoản cùng email (dữ liệu cũ) thì không đoán - bắt đăng nhập bằng tên đăng nhập
            return byEmail.size() == 1 ? byEmail.get(0) : null;
        });
        if (user == null) {
            throw new UsernameNotFoundException("Không tìm thấy tài khoản: " + login);
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword()) // đã là chuỗi hash BCrypt sẵn trong DB
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
                .disabled(!user.isEnabled())
                .build();
    }
}