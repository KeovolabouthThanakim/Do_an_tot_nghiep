package com.kosis.shop.controller;

import com.kosis.shop.service.CategoryService;
import com.kosis.shop.service.MenuLinkService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Quản lý thanh menu của cửa hàng - chỉ ADMIN (chặn ở SecurityConfig: /dashboard/cai-dat/**).
 *  - Danh mục sản phẩm: thêm / đổi tên (cập nhật luôn sản phẩm) / sắp xếp / ẩn khỏi menu / xoá (khi không còn sản phẩm)
 *  - Liên kết thêm: tên hiển thị + đường dẫn, sắp xếp / ẩn / xoá
 * Mọi thay đổi hiện ngay trên cửa hàng.
 */
@Controller
@RequestMapping("/dashboard/cai-dat/menu")
public class MenuAdminController {

    private static final String CATEGORY_PAGE = "redirect:/dashboard/cai-dat/menu#danh-muc";
    private static final String LINK_PAGE = "redirect:/dashboard/cai-dat/menu#lien-ket";

    private final CategoryService categories;
    private final MenuLinkService links;

    public MenuAdminController(CategoryService categories, MenuLinkService links) {
        this.categories = categories;
        this.links = links;
    }

    @GetMapping
    public String page(Authentication auth, Model model) {
        model.addAttribute("catItems", categories.items());
        model.addAttribute("linkItems", links.items());
        model.addAttribute("maxCategories", CategoryService.MAX_CATEGORIES);
        model.addAttribute("maxLinks", MenuLinkService.MAX_LINKS);
        model.addAttribute("username", auth.getName());
        model.addAttribute("role", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_ADMIN").replace("ROLE_", ""));
        return "dashboard/menu";
    }

    // ------------------------------------------------------------------ danh mục sản phẩm

    @PostMapping("/danh-muc/them")
    public String addCategory(@RequestParam(defaultValue = "") String name, RedirectAttributes ra) {
        try {
            categories.add(name);
            ra.addFlashAttribute("menuMessage", "Đã thêm danh mục \"" + name.trim() + "\". Menu cửa hàng cập nhật ngay.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
            ra.addFlashAttribute("draftName", name);
        }
        return CATEGORY_PAGE;
    }

    @PostMapping("/danh-muc/{id}/doi-ten")
    public String renameCategory(@PathVariable long id, @RequestParam(defaultValue = "") String name, RedirectAttributes ra) {
        try {
            int updated = categories.rename(id, name);
            ra.addFlashAttribute("menuMessage", updated == 0
                    ? "Tên danh mục không thay đổi."
                    : "Đã đổi tên danh mục và cập nhật " + updated + " sản phẩm.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
        }
        return CATEGORY_PAGE;
    }

    @PostMapping("/danh-muc/{id}/len")
    public String moveCategoryUp(@PathVariable long id, RedirectAttributes ra) {
        return moveCategory(id, -1, ra);
    }

    @PostMapping("/danh-muc/{id}/xuong")
    public String moveCategoryDown(@PathVariable long id, RedirectAttributes ra) {
        return moveCategory(id, 1, ra);
    }

    private String moveCategory(long id, int delta, RedirectAttributes ra) {
        try {
            categories.move(id, delta);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
        }
        return CATEGORY_PAGE;
    }

    @PostMapping("/danh-muc/{id}/an-hien")
    public String toggleCategory(@PathVariable long id, @RequestParam boolean show, RedirectAttributes ra) {
        try {
            categories.setVisible(id, show);
            ra.addFlashAttribute("menuMessage", show ? "Đã hiện danh mục trên menu." : "Đã ẩn danh mục khỏi menu (sản phẩm vẫn giữ nguyên).");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
        }
        return CATEGORY_PAGE;
    }

    @PostMapping("/danh-muc/{id}/xoa")
    public String deleteCategory(@PathVariable long id, RedirectAttributes ra) {
        try {
            categories.delete(id);
            ra.addFlashAttribute("menuMessage", "Đã xoá danh mục.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
        }
        return CATEGORY_PAGE;
    }

    // ------------------------------------------------------------------ liên kết thêm

    @PostMapping("/lien-ket/them")
    public String addLink(@RequestParam(defaultValue = "") String label, @RequestParam(defaultValue = "") String url,
                          RedirectAttributes ra) {
        try {
            links.add(label, url);
            ra.addFlashAttribute("menuMessage", "Đã thêm liên kết vào menu.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
            ra.addFlashAttribute("draftLabel", label);
            ra.addFlashAttribute("draftUrl", url);
        }
        return LINK_PAGE;
    }

    @PostMapping("/lien-ket/{id}/sua")
    public String updateLink(@PathVariable long id, @RequestParam(defaultValue = "") String label,
                             @RequestParam(defaultValue = "") String url, RedirectAttributes ra) {
        try {
            links.update(id, label, url);
            ra.addFlashAttribute("menuMessage", "Đã lưu liên kết.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
        }
        return LINK_PAGE;
    }

    @PostMapping("/lien-ket/{id}/len")
    public String moveLinkUp(@PathVariable long id, RedirectAttributes ra) {
        return moveLink(id, -1, ra);
    }

    @PostMapping("/lien-ket/{id}/xuong")
    public String moveLinkDown(@PathVariable long id, RedirectAttributes ra) {
        return moveLink(id, 1, ra);
    }

    private String moveLink(long id, int delta, RedirectAttributes ra) {
        try {
            links.move(id, delta);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
        }
        return LINK_PAGE;
    }

    @PostMapping("/lien-ket/{id}/an-hien")
    public String toggleLink(@PathVariable long id, @RequestParam boolean show, RedirectAttributes ra) {
        try {
            links.setVisible(id, show);
            ra.addFlashAttribute("menuMessage", show ? "Đã hiện liên kết trên menu." : "Đã ẩn liên kết khỏi menu.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
        }
        return LINK_PAGE;
    }

    @PostMapping("/lien-ket/{id}/xoa")
    public String deleteLink(@PathVariable long id, RedirectAttributes ra) {
        try {
            links.delete(id);
            ra.addFlashAttribute("menuMessage", "Đã xoá liên kết.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("menuError", e.getMessage());
        }
        return LINK_PAGE;
    }
}
