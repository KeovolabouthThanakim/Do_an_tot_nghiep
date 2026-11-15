package com.kosis.shop.controller;

import com.kosis.shop.entity.User;
import com.kosis.shop.repository.UserRepository;
import com.kosis.shop.service.EmailService;
import com.kosis.shop.service.PasswordResetService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * Quên mật khẩu (/forgot-password, /reset-password - công khai) và đổi mật khẩu khi đã đăng nhập (/account/password).
 */
@Controller
public class PasswordController {

    private static final int MIN_LEN = 6;
    /** BCrypt chỉ dùng 72 byte đầu của mật khẩu -> không nhận dài hơn để khỏi bị cắt âm thầm */
    private static final int MAX_LEN = 72;

    private final PasswordResetService resetService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public PasswordController(PasswordResetService resetService, UserRepository userRepository,
                              PasswordEncoder passwordEncoder, EmailService emailService) {
        this.resetService = resetService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // ---------- Quên mật khẩu ----------

    @GetMapping("/forgot-password")
    public String forgotPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotSubmit(@RequestParam(defaultValue = "") String identifier, Model model) {
        resetService.requestReset(identifier);
        // Luôn trả lời giống nhau dù có tài khoản hay không
        model.addAttribute("successMessage",
                "Nếu thông tin khớp với một tài khoản, chúng tôi đã gửi email hướng dẫn đặt lại mật khẩu. "
                        + "Link có hiệu lực 30 phút. Hãy kiểm tra cả hộp thư rác.");
        return "auth/forgot-password";
    }

    // ---------- Đặt lại mật khẩu bằng link trong email ----------

    @GetMapping("/reset-password")
    public String resetPage(@RequestParam(defaultValue = "") String token, Model model) {
        if (!resetService.isValid(token)) {
            model.addAttribute("invalid", true);
            model.addAttribute("errorMessage", "Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu link mới.");
        } else {
            model.addAttribute("token", token);
        }
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetSubmit(@RequestParam(defaultValue = "") String token,
                              @RequestParam(defaultValue = "") String password,
                              @RequestParam(defaultValue = "") String confirmPassword,
                              Model model) {
        String error = validateNew(password, confirmPassword);
        if (error != null) {
            model.addAttribute("token", token);
            model.addAttribute("errorMessage", error);
            return "auth/reset-password";
        }
        if (!resetService.resetPassword(token, password)) {
            model.addAttribute("invalid", true);
            model.addAttribute("errorMessage", "Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu link mới.");
            return "auth/reset-password";
        }
        return "redirect:/login?passwordReset=true";
    }

    // ---------- Đổi mật khẩu (đã đăng nhập) ----------

    @PostMapping("/account/password")
    public String changePassword(@RequestParam(defaultValue = "") String currentPassword,
                                 @RequestParam(defaultValue = "") String newPassword,
                                 @RequestParam(defaultValue = "") String confirmPassword,
                                 Principal principal, RedirectAttributes ra) {
        User u = userRepository.findByUsername(principal.getName()).orElse(null);
        if (u == null) return "redirect:/login";

        if (!passwordEncoder.matches(currentPassword, u.getPassword())) {
            ra.addFlashAttribute("passwordError", "Mật khẩu hiện tại không đúng.");
            return "redirect:/account";
        }
        String error = validateNew(newPassword, confirmPassword);
        if (error != null) {
            ra.addFlashAttribute("passwordError", error);
            return "redirect:/account";
        }
        if (passwordEncoder.matches(newPassword, u.getPassword())) {
            ra.addFlashAttribute("passwordError", "Mật khẩu mới phải khác mật khẩu hiện tại.");
            return "redirect:/account";
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(u);
        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            emailService.send(u.getEmail(), "KOSIS - Mật khẩu đã được thay đổi",
                    "Mật khẩu của tài khoản \"" + u.getUsername() + "\" vừa được đổi.\n"
                            + "Nếu không phải bạn thực hiện, hãy liên hệ cửa hàng ngay.\n\nKOSIS");
        }
        ra.addFlashAttribute("passwordMessage", "Đã đổi mật khẩu.");
        return "redirect:/account";
    }

    /** null = hợp lệ */
    private static String validateNew(String password, String confirm) {
        if (password.length() < MIN_LEN) return "Mật khẩu phải có ít nhất " + MIN_LEN + " ký tự.";
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_LEN) return "Mật khẩu quá dài (tối đa " + MAX_LEN + " ký tự).";
        if (!password.equals(confirm)) return "Mật khẩu nhập lại không khớp.";
        return null;
    }
}
