package com.kosis.shop.controller;

import com.kosis.shop.entity.User;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Quản lý nhân viên & admin: /dashboard/nhan-vien - CHỈ ADMIN
 * (chặn ở SecurityConfig: requestMatchers("/dashboard/nhan-vien/**").hasRole("ADMIN")).
 *
 * Quy tắc:
 *  - Không có nút xoá: dùng khoá tài khoản (enabled = false).
 *  - Không được khoá / hạ quyền chính mình.
 *  - Không được khoá / hạ quyền ADMIN cuối cùng còn hoạt động.
 *  - Mật khẩu mã hoá BCrypt (cùng PasswordEncoder với đăng ký), tối thiểu 6 ký tự như khi đăng ký.
 */
@Controller
@RequestMapping("/dashboard/nhan-vien")
public class StaffAdminController {

    static final List<String> STAFF_ROLES = List.of("ADMIN", "NHANVIEN");
    private static final int MIN_PASSWORD = 6;
    private static final Path AVATAR_DIR = Path.of("uploads", "avatars");
    private static final String AVATAR_URL = "/uploads/avatars/";
    private static final Set<String> AVATAR_EXT = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");
    private static final long AVATAR_MAX = 2L * 1024 * 1024; // 2MB

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.kosis.shop.repository.WishlistItemRepository wishlistRepository;

    public StaffAdminController(UserRepository userRepository, OrderRepository orderRepository, PasswordEncoder passwordEncoder,
                                com.kosis.shop.repository.WishlistItemRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- Danh sách ----------
    @GetMapping
    public String list(Authentication auth, Model model) {
        List<User> staff = userRepository.findByRoleInOrderByRoleAscUsernameAsc(STAFF_ROLES);
        long admins = staff.stream().filter(User::isAdmin).count();
        long locked = staff.stream().filter(u -> !u.isEnabled()).count();

        addStaffInfo(auth, model);
        model.addAttribute("staff", staff);
        model.addAttribute("totalCount", staff.size());
        model.addAttribute("adminCount", admins);
        model.addAttribute("staffCount", staff.size() - admins);
        model.addAttribute("lockedCount", locked);
        return "dashboard/nhan-vien";
    }

    // ---------- Tạo mới ----------
    @GetMapping("/them")
    public String addForm(Authentication auth, Model model) {
        addStaffInfo(auth, model);
        model.addAttribute("isEdit", false);
        model.addAttribute("form", new User());
        return "dashboard/nhan-vien-form";
    }

    @PostMapping("/them")
    public String addSubmit(@RequestParam String username,
                            @RequestParam(defaultValue = "") String password,
                            @RequestParam(defaultValue = "") String fullName,
                            @RequestParam(defaultValue = "") String email,
                            @RequestParam(defaultValue = "") String phone,
                            @RequestParam(defaultValue = "NHANVIEN") String role,
                            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                            Authentication auth, Model model, RedirectAttributes ra) {
        username = username.trim();
        User form = new User();
        form.setUsername(username);
        form.setFullName(fullName.trim());
        form.setEmail(email.trim());
        form.setPhone(phone.trim());
        form.setRole(role);

        String error = null;
        if (username.isEmpty()) {
            error = "Vui lòng nhập tên đăng nhập.";
        } else if (username.length() > 50) {
            error = "Tên đăng nhập tối đa 50 ký tự.";
        } else if (userRepository.existsByUsernameIgnoreCase(username)) {
            error = "Tên đăng nhập đã tồn tại, vui lòng chọn tên khác.";
        } else if (!STAFF_ROLES.contains(role)) {
            error = "Vai trò không hợp lệ.";
        } else if (!form.getEmail().isEmpty() && userRepository.existsByEmailIgnoreCase(form.getEmail())) {
            error = "Email này đã được dùng cho tài khoản khác.";
        } else if (password.length() < MIN_PASSWORD) {
            error = "Mật khẩu phải có ít nhất " + MIN_PASSWORD + " ký tự.";
        } else {
            error = avatarError(avatar);
        }
        if (error != null) {
            addStaffInfo(auth, model);
            model.addAttribute("isEdit", false);
            model.addAttribute("form", form);
            model.addAttribute("errorMessage", error);
            return "dashboard/nhan-vien-form";
        }

        form.setPassword(passwordEncoder.encode(password));
        form.setEnabled(true);
        try {
            if (avatar != null && !avatar.isEmpty()) {
                form.setAvatarImage(saveAvatar(avatar));
            }
        } catch (IOException e) {
            addStaffInfo(auth, model);
            model.addAttribute("isEdit", false);
            model.addAttribute("form", form);
            model.addAttribute("errorMessage", "Không lưu được ảnh đại diện, vui lòng thử lại.");
            return "dashboard/nhan-vien-form";
        }
        try {
            userRepository.save(form);
        } catch (DataIntegrityViolationException e) {
            addStaffInfo(auth, model);
            model.addAttribute("isEdit", false);
            model.addAttribute("form", form);
            model.addAttribute("errorMessage", "Tên đăng nhập đã tồn tại, vui lòng chọn tên khác.");
            return "dashboard/nhan-vien-form";
        }
        ra.addFlashAttribute("staffMessage", "Đã tạo tài khoản \"" + username + "\" (" + role + ").");
        return "redirect:/dashboard/nhan-vien";
    }

    // ---------- Sửa thông tin / đổi role ----------
    @GetMapping("/sua/{id}")
    public String editForm(@PathVariable Long id, Authentication auth, Model model, RedirectAttributes ra) {
        User u = findStaff(id);
        if (u == null) {
            ra.addFlashAttribute("staffError", "Không tìm thấy tài khoản nhân viên.");
            return "redirect:/dashboard/nhan-vien";
        }
        addStaffInfo(auth, model);
        model.addAttribute("isEdit", true);
        model.addAttribute("form", u);
        model.addAttribute("isSelf", u.getUsername().equals(auth.getName()));
        return "dashboard/nhan-vien-form";
    }

    @PostMapping("/sua/{id}")
    @Transactional
    public String editSubmit(@PathVariable Long id,
                             @RequestParam(required = false) String username,
                             @RequestParam(defaultValue = "") String fullName,
                             @RequestParam(defaultValue = "") String email,
                             @RequestParam(defaultValue = "") String phone,
                             @RequestParam String role,
                             @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                             @RequestParam(value = "removeAvatar", required = false) String removeAvatar,
                             Authentication auth, HttpServletRequest request, RedirectAttributes ra) {
        User u = findStaff(id);
        if (u == null) {
            ra.addFlashAttribute("staffError", "Không tìm thấy tài khoản nhân viên.");
            return "redirect:/dashboard/nhan-vien";
        }
        // ----- Đổi tên đăng nhập (nếu có thay đổi) -----
        String oldName = u.getUsername();
        String newName = username == null ? oldName : username.trim();
        boolean renamed = !newName.equals(oldName);
        if (renamed) {
            String err = null;
            if (newName.isEmpty()) {
                err = "Tên đăng nhập không được để trống.";
            } else if (newName.length() > 50) {
                err = "Tên đăng nhập tối đa 50 ký tự.";
            } else if (newName.chars().anyMatch(Character::isWhitespace)) {
                err = "Tên đăng nhập không được chứa khoảng trắng.";
            } else if (userRepository.existsByUsernameIgnoreCaseAndIdNot(newName, u.getId())) {
                err = "Tên đăng nhập đã tồn tại, vui lòng chọn tên khác.";
            }
            if (err != null) {
                ra.addFlashAttribute("staffError", err);
                return "redirect:/dashboard/nhan-vien/sua/" + id;
            }
        }
        if (!STAFF_ROLES.contains(role)) {
            ra.addFlashAttribute("staffError", "Vai trò không hợp lệ.");
            return "redirect:/dashboard/nhan-vien/sua/" + id;
        }
        String avatarErr = avatarError(avatar);
        if (avatarErr != null) {
            ra.addFlashAttribute("staffError", avatarErr);
            return "redirect:/dashboard/nhan-vien/sua/" + id;
        }
        if (!email.trim().isEmpty() && userRepository.existsByEmailIgnoreCaseAndIdNot(email.trim(), u.getId())) {
            ra.addFlashAttribute("staffError", "Email này đã được dùng cho tài khoản khác.");
            return "redirect:/dashboard/nhan-vien/sua/" + id;
        }
        if (!role.equals(u.getRole())) {
            // Đổi role: không tự hạ quyền mình, không bỏ ADMIN cuối cùng
            if (u.getUsername().equals(auth.getName())) {
                ra.addFlashAttribute("staffError", "Bạn không thể đổi vai trò của chính mình.");
                return "redirect:/dashboard/nhan-vien/sua/" + id;
            }
            if (u.isAdmin() && u.isEnabled() && isLastActiveAdmin()) {
                ra.addFlashAttribute("staffError", "Không thể hạ quyền ADMIN cuối cùng đang hoạt động. Hãy tạo / mở khoá một ADMIN khác trước.");
                return "redirect:/dashboard/nhan-vien/sua/" + id;
            }
            u.setRole(role);
        }
        u.setFullName(fullName.trim());
        u.setEmail(email.trim());
        u.setPhone(phone.trim());
        if (renamed) {
            u.setUsername(newName);
        }
        // ----- Ảnh đại diện: tải ảnh mới (thay ảnh cũ) hoặc xoá ảnh đã tải -----
        try {
            if (avatar != null && !avatar.isEmpty()) {
                String old = u.getAvatarImage();
                u.setAvatarImage(saveAvatar(avatar));
                deleteAvatarFile(old);
            } else if (removeAvatar != null) {
                deleteAvatarFile(u.getAvatarImage());
                u.setAvatarImage(null);
            }
        } catch (IOException e) {
            ra.addFlashAttribute("staffError", "Không lưu được ảnh đại diện, vui lòng thử lại.");
            return "redirect:/dashboard/nhan-vien/sua/" + id;
        }
        userRepository.save(u);
        if (renamed) {
            // đơn đã đặt / đã xử lý đi theo tên mới để lịch sử không bị mồ côi
            orderRepository.renameCustomer(oldName, newName);
            orderRepository.renameReviewer(oldName, newName);
            wishlistRepository.renameUser(oldName, newName);
            if (oldName.equals(auth.getName())) {
                // tự đổi tên của mình: phiên đăng nhập đang giữ tên cũ -> đăng xuất để vào lại bằng tên mới
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                return "redirect:/login?renamed=true";
            }
        }
        ra.addFlashAttribute("staffMessage", "Đã lưu thay đổi cho \"" + u.getUsername() + "\"."
                + (renamed ? " Tên đăng nhập đã đổi từ \"" + oldName + "\"." : ""));
        return "redirect:/dashboard/nhan-vien";
    }

    // ---------- Reset mật khẩu ----------
    @PostMapping("/{id}/dat-lai-mat-khau")
    public String resetPassword(@PathVariable Long id, @RequestParam(defaultValue = "") String newPassword,
                                RedirectAttributes ra) {
        User u = findStaff(id);
        if (u == null) {
            ra.addFlashAttribute("staffError", "Không tìm thấy tài khoản nhân viên.");
            return "redirect:/dashboard/nhan-vien";
        }
        if (newPassword.length() < MIN_PASSWORD) {
            ra.addFlashAttribute("staffError", "Mật khẩu mới phải có ít nhất " + MIN_PASSWORD + " ký tự.");
            return "redirect:/dashboard/nhan-vien/sua/" + id;
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(u);
        ra.addFlashAttribute("staffMessage", "Đã đặt lại mật khẩu cho \"" + u.getUsername() + "\".");
        return "redirect:/dashboard/nhan-vien";
    }

    // ---------- Khoá / mở khoá ----------
    @PostMapping("/{id}/khoa")
    public String lock(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        User u = findStaff(id);
        if (u == null) {
            ra.addFlashAttribute("staffError", "Không tìm thấy tài khoản nhân viên.");
            return "redirect:/dashboard/nhan-vien";
        }
        if (u.getUsername().equals(auth.getName())) {
            ra.addFlashAttribute("staffError", "Bạn không thể khoá chính tài khoản của mình.");
            return "redirect:/dashboard/nhan-vien";
        }
        if (u.isAdmin() && u.isEnabled() && isLastActiveAdmin()) {
            ra.addFlashAttribute("staffError", "Không thể khoá ADMIN cuối cùng đang hoạt động.");
            return "redirect:/dashboard/nhan-vien";
        }
        u.setEnabled(false);
        userRepository.save(u);
        ra.addFlashAttribute("staffMessage", "Đã khoá tài khoản \"" + u.getUsername() + "\".");
        return "redirect:/dashboard/nhan-vien";
    }

    @PostMapping("/{id}/mo-khoa")
    public String unlock(@PathVariable Long id, RedirectAttributes ra) {
        User u = findStaff(id);
        if (u == null) {
            ra.addFlashAttribute("staffError", "Không tìm thấy tài khoản nhân viên.");
            return "redirect:/dashboard/nhan-vien";
        }
        u.setEnabled(true);
        userRepository.save(u);
        ra.addFlashAttribute("staffMessage", "Đã mở khoá tài khoản \"" + u.getUsername() + "\".");
        return "redirect:/dashboard/nhan-vien";
    }

    // ---------- Xoá tài khoản nhân viên (không xoá chính mình / ADMIN cuối cùng) ----------
    @PostMapping("/{id}/xoa")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        User u = findStaff(id);
        if (u == null) {
            ra.addFlashAttribute("staffError", "Không tìm thấy tài khoản nhân viên.");
            return "redirect:/dashboard/nhan-vien";
        }
        if (u.getUsername().equals(auth.getName())) {
            ra.addFlashAttribute("staffError", "Bạn không thể xoá chính tài khoản của mình.");
            return "redirect:/dashboard/nhan-vien";
        }
        if (u.isAdmin() && u.isEnabled() && isLastActiveAdmin()) {
            ra.addFlashAttribute("staffError", "Không thể xoá ADMIN cuối cùng đang hoạt động.");
            return "redirect:/dashboard/nhan-vien";
        }
        deleteAvatarFile(u.getAvatarImage());
        userRepository.delete(u);
        wishlistRepository.deleteByUsername(u.getUsername());
        ra.addFlashAttribute("staffMessage", "Đã xoá tài khoản \"" + u.getUsername() + "\".");
        return "redirect:/dashboard/nhan-vien";
    }

    // ==================== helper ====================

    private static String extensionOf(MultipartFile f) {
        String n = f.getOriginalFilename();
        int dot = n == null ? -1 : n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot).toLowerCase();
    }

    /** null = hợp lệ (hoặc không chọn ảnh) */
    private static String avatarError(MultipartFile f) {
        if (f == null || f.isEmpty()) return null;
        String type = f.getContentType();
        if (type == null || !type.startsWith("image/") || !AVATAR_EXT.contains(extensionOf(f))) {
            return "Ảnh đại diện phải là file .jpg, .png, .webp hoặc .gif.";
        }
        if (f.getSize() > AVATAR_MAX) {
            return "Ảnh đại diện tối đa 2MB.";
        }
        return null;
    }

    private static String saveAvatar(MultipartFile f) throws IOException {
        Files.createDirectories(AVATAR_DIR);
        String name = UUID.randomUUID() + extensionOf(f);
        f.transferTo(AVATAR_DIR.resolve(name).toAbsolutePath());
        return AVATAR_URL + name;
    }

    /** Chỉ xoá file nằm trong uploads/avatars (không đụng file khác) */
    private static void deleteAvatarFile(String url) {
        if (url == null || !url.startsWith(AVATAR_URL)) return;
        String name = url.substring(AVATAR_URL.length());
        if (name.isEmpty() || name.contains("/") || name.contains("..")) return;
        try {
            Files.deleteIfExists(AVATAR_DIR.resolve(name));
        } catch (IOException ignored) {
            // không xoá được file cũ thì bỏ qua, không ảnh hưởng tài khoản
        }
    }

    /** Chỉ ADMIN / NHANVIEN - trang này không đụng tới tài khoản khách hàng */
    private User findStaff(Long id) {
        return userRepository.findById(id).filter(u -> STAFF_ROLES.contains(u.getRole())).orElse(null);
    }

    /** true nếu chỉ còn đúng 1 (hoặc ít hơn) ADMIN đang hoạt động */
    private boolean isLastActiveAdmin() {
        return userRepository.countByRoleAndEnabled("ADMIN", true) <= 1;
    }

    private void addStaffInfo(Authentication auth, Model model) {
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("NHANVIEN")
                .replace("ROLE_", "");
        model.addAttribute("username", auth.getName());
        model.addAttribute("role", role);
        model.addAttribute("selfUsername", auth.getName());
    }
}
