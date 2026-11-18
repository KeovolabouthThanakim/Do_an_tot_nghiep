package com.kosis.shop.controller;

import com.kosis.shop.entity.ContactMessage;
import com.kosis.shop.repository.ContactMessageRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tin nhắn liên hệ của khách: /dashboard/lien-he (ADMIN và NHANVIEN đều xem và xử lý được;
 * đã được SecurityConfig chặn chung ở /dashboard/**).
 */
@Controller
@RequestMapping("/dashboard/lien-he")
public class ContactAdminController {

    private final ContactMessageRepository contactRepository;

    public ContactAdminController(ContactMessageRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @GetMapping
    public String list(Authentication auth, Model model) {
        List<ContactMessage> all = contactRepository.findAllByOrderByCreatedAtDesc();
        long open = all.stream().filter(m -> !m.isHandled()).count();
        addStaffInfo(auth, model);
        model.addAttribute("messages", all);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("openCount", open);
        model.addAttribute("handledCount", all.size() - open);
        return "dashboard/lien-he";
    }

    /** Đánh dấu đã xử lý / chưa xử lý (bấm lại để hoàn tác) */
    @PostMapping("/{id}/xu-ly")
    public String toggleHandled(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean handled,
                                Authentication auth, RedirectAttributes ra) {
        contactRepository.findById(id).ifPresentOrElse(m -> {
            m.setHandled(handled);
            m.setHandledBy(handled ? auth.getName() : null);
            m.setHandledAt(handled ? LocalDateTime.now() : null);
            contactRepository.save(m);
            ra.addFlashAttribute("contactAdminMessage",
                    handled ? "Đã đánh dấu tin nhắn của " + m.getName() + " là đã xử lý." : "Đã chuyển tin nhắn về chưa xử lý.");
        }, () -> ra.addFlashAttribute("contactAdminError", "Không tìm thấy tin nhắn."));
        return "redirect:/dashboard/lien-he";
    }

    private void addStaffInfo(Authentication auth, Model model) {
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_NHANVIEN")
                .replace("ROLE_", "");
        model.addAttribute("username", auth.getName());
        model.addAttribute("role", role);
    }
}
