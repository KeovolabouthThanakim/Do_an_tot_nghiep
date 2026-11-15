package com.kosis.shop.controller;

import com.kosis.shop.entity.User;
import com.kosis.shop.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Hiển thị trang đăng nhập.
     * Spring Security sẽ tự xử lý POST /login (so khớp mật khẩu đã mã hoá trong SQL),
     * nên ở đây chỉ cần render giao diện + đọc các tham số ?error / ?logout trên URL.
     */
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                             @RequestParam(value = "logout", required = false) String logout,
                             @RequestParam(value = "registered", required = false) String registered,
                             @RequestParam(value = "required", required = false) String required,
                             @RequestParam(value = "renamed", required = false) String renamed,
                             @RequestParam(value = "passwordReset", required = false) String passwordReset,
                             Model model) {
        if ("ratelimit".equals(error)) {
            model.addAttribute("errorMessage", "Bạn thử đăng nhập quá nhiều lần. Vui lòng đợi vài phút rồi thử lại.");
        } else if ("denied".equals(error)) {
            // đã đăng nhập nhưng không đủ quyền vào trang vừa mở (khác với sai mật khẩu)
            model.addAttribute("errorMessage", "Tài khoản của bạn không có quyền truy cập trang này.");
        } else if (error != null) {
            model.addAttribute("errorMessage", "Sai tên đăng nhập hoặc mật khẩu!");
        }
        if (required != null) {
            model.addAttribute("infoMessage", "Vui lòng đăng nhập để tiếp tục.");
        }
        if (renamed != null) {
            model.addAttribute("infoMessage", "Tên đăng nhập đã được đổi. Vui lòng đăng nhập lại bằng tên mới.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "Bạn đã đăng xuất thành công.");
        }
        if (passwordReset != null) {
            model.addAttribute("successMessage", "Đã đặt lại mật khẩu. Vui lòng đăng nhập bằng mật khẩu mới.");
        }
        if (registered != null) {
            model.addAttribute("successMessage", "Đăng ký thành công! Vui lòng đăng nhập.");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(@RequestParam(value = "error", required = false) String error, Model model) {
        if ("ratelimit".equals(error)) {
            model.addAttribute("errorMessage", "Bạn đăng ký quá nhiều lần. Vui lòng thử lại sau.");
        }
        model.addAttribute("user", new User());
        return "auth/register";
    }

    /**
     * Đăng ký: kiểm tra thông tin, mã hoá mật khẩu (BCrypt) rồi tạo tài khoản ngay (không cần mã xác thực).
     * Tên đăng nhập và email phải là duy nhất; SỐ ĐIỆN THOẠI được phép trùng nhau.
     */
    @PostMapping("/register")
    public String register(@ModelAttribute User user, Model model) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            model.addAttribute("errorMessage", "Vui lòng nhập tên đăng nhập.");
            return "auth/register";
        }
        user.setUsername(user.getUsername().trim());
        if (user.getPassword() == null || user.getPassword().length() < 6) {
            model.addAttribute("errorMessage", "Mật khẩu phải có ít nhất 6 ký tự.");
            return "auth/register";
        }
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        if (email.isEmpty()) {
            model.addAttribute("errorMessage", "Vui lòng nhập email.");
            return "auth/register";
        }
        String phone = normalizeVnPhone(user.getPhone());
        if (phone == null) {
            model.addAttribute("errorMessage", "Số điện thoại không hợp lệ. Nhập 10 số, VD: 0901234567.");
            return "auth/register";
        }
        if (userRepository.existsByUsernameIgnoreCase(user.getUsername())) {
            model.addAttribute("errorMessage", "Tên đăng nhập đã tồn tại, vui lòng chọn tên khác.");
            return "auth/register";
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            model.addAttribute("errorMessage", "Email này đã được dùng cho tài khoản khác.");
            return "auth/register";
        }

        User u = new User();
        u.setUsername(user.getUsername());
        u.setPassword(passwordEncoder.encode(user.getPassword()));
        u.setFullName(user.getFullName());
        u.setEmail(email);
        u.setPhone(phone);
        u.setAddress(user.getAddress());
        u.setRole("USER");
        u.setEnabled(true);
        try {
            userRepository.save(u);
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("errorMessage", "Tên đăng nhập hoặc email đã tồn tại, vui lòng thử lại.");
            return "auth/register";
        }
        return "redirect:/login?registered=true";
    }

    /** "0901234567" | "+84901234567" | "84901234567" (có thể có dấu cách/chấm/gạch) -> "0901234567"; sai định dạng -> null */
    private static String normalizeVnPhone(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s.\\-()]", "");
        if (s.startsWith("+84")) s = "0" + s.substring(3);
        else if (s.startsWith("84") && s.length() == 11) s = "0" + s.substring(2);
        return s.matches("0\\d{9}") ? s : null;
    }
}
