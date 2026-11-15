package com.kosis.shop.controller;

import com.kosis.shop.entity.Voucher;
import com.kosis.shop.repository.VoucherRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Quản lý mã giảm giá - chỉ ADMIN (chặn ở SecurityConfig: /dashboard/voucher/**).
 */
@Controller
@RequestMapping("/dashboard/voucher")
public class VoucherAdminController {

    private final VoucherRepository voucherRepository;

    public VoucherAdminController(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    @GetMapping
    public String list(Authentication auth, Model model) {
        java.util.List<Voucher> vouchers = voucherRepository.findAllByOrderByCreatedAtDesc();
        long usable = vouchers.stream().filter(Voucher::isUsable).count();
        long off = vouchers.stream().filter(v -> !v.isActive()).count();
        long ended = vouchers.stream().filter(v -> v.isActive() && (v.isExpired() || v.isExhausted())).count();
        long totalUsed = vouchers.stream().mapToLong(Voucher::getUsedCount).sum();
        model.addAttribute("vouchers", vouchers);
        model.addAttribute("totalCount", vouchers.size());
        model.addAttribute("usableCount", usable);
        model.addAttribute("offCount", off);
        model.addAttribute("endedCount", ended);
        model.addAttribute("totalUsed", totalUsed);
        model.addAttribute("username", auth.getName());
        model.addAttribute("role", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_ADMIN").replace("ROLE_", ""));
        return "dashboard/voucher";
    }

    @PostMapping("/tao")
    public String create(@RequestParam String code,
                         @RequestParam(defaultValue = "") String description,
                         @RequestParam String discountType,
                         @RequestParam long discountValue,
                         @RequestParam(defaultValue = "0") long minOrder,
                         @RequestParam(defaultValue = "0") long maxDiscount,
                         @RequestParam(defaultValue = "0") int usageLimit,
                         @RequestParam(defaultValue = "") String expiresOn,
                         RedirectAttributes ra) {
        String c = Voucher.normalize(code);
        String error = null;
        if (!c.matches("[A-Z0-9_-]{3,30}")) {
            error = "Mã chỉ gồm chữ không dấu, số, gạch ngang/gạch dưới (3-30 ký tự).";
        } else if (voucherRepository.existsByCode(c)) {
            error = "Mã \"" + c + "\" đã tồn tại.";
        } else if (!Voucher.PERCENT.equals(discountType) && !Voucher.FIXED.equals(discountType)) {
            error = "Loại giảm giá không hợp lệ.";
        } else if (Voucher.PERCENT.equals(discountType) && (discountValue < 1 || discountValue > 100)) {
            error = "Giảm theo % phải từ 1 đến 100.";
        } else if (Voucher.FIXED.equals(discountType) && discountValue < 1000) {
            error = "Số tiền giảm tối thiểu 1.000 ₫.";
        } else if (minOrder < 0 || maxDiscount < 0 || usageLimit < 0) {
            error = "Các giá trị không được âm.";
        } else if (description.length() > 150) {
            error = "Mô tả quá dài (tối đa 150 ký tự).";
        }
        LocalDateTime expires = null;
        if (error == null && !expiresOn.isBlank()) {
            try {
                expires = LocalDate.parse(expiresOn.trim()).atTime(LocalTime.of(23, 59, 59));
            } catch (DateTimeParseException e) {
                error = "Ngày hết hạn không hợp lệ.";
            }
        }
        if (error != null) {
            ra.addFlashAttribute("voucherError", error);
            return "redirect:/dashboard/voucher";
        }
        Voucher v = new Voucher();
        v.setCode(c);
        v.setDescription(description.isBlank() ? null : description.trim());
        v.setDiscountType(discountType);
        v.setDiscountValue(discountValue);
        v.setMinOrder(minOrder);
        v.setMaxDiscount(Voucher.PERCENT.equals(discountType) ? maxDiscount : 0);
        v.setUsageLimit(usageLimit);
        v.setExpiresAt(expires);
        voucherRepository.save(v);
        ra.addFlashAttribute("voucherMessage", "Đã tạo mã " + c + ".");
        return "redirect:/dashboard/voucher";
    }

    @PostMapping("/{id}/bat-tat")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        voucherRepository.findById(id).ifPresent(v -> {
            v.setActive(!v.isActive());
            voucherRepository.save(v);
            ra.addFlashAttribute("voucherMessage", (v.isActive() ? "Đã bật" : "Đã tắt") + " mã " + v.getCode() + ".");
        });
        return "redirect:/dashboard/voucher";
    }

    /** Đơn cũ đã lưu mã dạng chữ nên xoá voucher không ảnh hưởng lịch sử đơn */
    @PostMapping("/{id}/xoa")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        voucherRepository.findById(id).ifPresent(v -> {
            voucherRepository.delete(v);
            ra.addFlashAttribute("voucherMessage", "Đã xoá mã " + v.getCode() + ".");
        });
        return "redirect:/dashboard/voucher";
    }
}
