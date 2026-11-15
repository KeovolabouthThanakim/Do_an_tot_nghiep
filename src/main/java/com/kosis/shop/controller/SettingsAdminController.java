package com.kosis.shop.controller;

import com.kosis.shop.config.BankProperties;
import com.kosis.shop.service.SettingsService;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cài đặt hệ thống - chỉ ADMIN (chặn ở SecurityConfig: /dashboard/cai-dat/**).
 * Sửa được: thông tin cửa hàng, hoá đơn, phí vận chuyển, thời hạn đổi trả... (xem SettingsService).
 * Chỉ ĐỌC: tài khoản ngân hàng, webhook, SMTP - những mục nhạy cảm này vẫn đặt bằng biến môi trường / local.properties.
 */
@Controller
@RequestMapping("/dashboard/cai-dat")
public class SettingsAdminController {

    private final SettingsService settings;
    private final BankProperties bank;
    private final Environment env;

    public SettingsAdminController(SettingsService settings, BankProperties bank, Environment env) {
        this.settings = settings;
        this.bank = bank;
        this.env = env;
    }

    @GetMapping
    public String page(Authentication auth, Model model) {
        model.addAttribute("groups", SettingsService.groups());

        // giá trị hiện trong ô: đang áp dụng, trừ khi lần lưu trước bị lỗi thì giữ những gì người dùng vừa gõ
        Map<String, String> shown = settings.currentValues();
        Object submitted = model.asMap().get("submitted");
        if (submitted instanceof Map<?, ?> typed) {
            typed.forEach((k, v) -> {
                if (k != null && v != null) shown.put(k.toString(), v.toString());
            });
        }
        model.addAttribute("values", shown);
        Object errors = model.asMap().get("fieldErrors");
        model.addAttribute("fieldErrors", errors instanceof Map ? errors : Map.of());

        model.addAttribute("defaults", settings.defaultLabels());
        model.addAttribute("customized", settings.customizedKeys());
        model.addAttribute("customizedCount", settings.customizedKeys().size());
        model.addAttribute("lastChange", settings.lastChange().orElse(null));
        model.addAttribute("systemStatus", systemStatus());
        model.addAttribute("username", auth.getName());
        model.addAttribute("role", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_ADMIN").replace("ROLE_", ""));
        return "dashboard/cai-dat";
    }

    /** Lưu tất cả mục gửi lên. Có mục sai thì không lưu gì và giữ lại những gì người dùng đã gõ để sửa tiếp */
    @PostMapping("/luu")
    public String save(@RequestParam Map<String, String> params, Authentication auth, RedirectAttributes ra) {
        // chỉ lấy các khoá cài đặt hợp lệ (bỏ _csrf và tham số lạ)
        Map<String, String> submitted = new LinkedHashMap<>();
        for (String key : SettingsService.keys()) {
            if (params.containsKey(key)) submitted.put(key, params.get(key));
        }

        SettingsService.SaveResult result = settings.save(submitted, auth.getName());
        if (!result.isOk()) {
            ra.addFlashAttribute("submitted", submitted);
            ra.addFlashAttribute("fieldErrors", result.getFieldErrors());
            ra.addFlashAttribute("settingsError", "Có " + result.getFieldErrors().size()
                    + " mục chưa hợp lệ nên chưa lưu gì. Hãy sửa các ô báo đỏ rồi lưu lại.");
        } else if (result.getChanged() == 0) {
            ra.addFlashAttribute("settingsInfo", "Không có thay đổi nào để lưu.");
        } else {
            ra.addFlashAttribute("settingsMessage", "Đã lưu " + result.getChanged()
                    + " thay đổi. Cửa hàng áp dụng ngay, không cần khởi động lại.");
        }
        return "redirect:/dashboard/cai-dat";
    }

    /** Xoá mọi tuỳ chỉnh: tất cả quay về giá trị mặc định trong application.properties */
    @PostMapping("/mac-dinh")
    public String reset(Authentication auth, RedirectAttributes ra) {
        int n = settings.resetAll(auth.getName());
        if (n > 0) {
            ra.addFlashAttribute("settingsMessage", "Đã đặt lại " + n + " mục về giá trị mặc định.");
        } else {
            ra.addFlashAttribute("settingsInfo", "Mọi mục đang ở giá trị mặc định rồi.");
        }
        return "redirect:/dashboard/cai-dat";
    }

    // ------------------------------------------------------------------ trạng thái hệ thống (chỉ đọc)

    /** Một dòng trạng thái (lớp thường có getter để Thymeleaf đọc được) */
    public static class StatusItem {
        private final String label;
        private final boolean ok;
        private final String detail;

        StatusItem(String label, boolean ok, String detail) {
            this.label = label;
            this.ok = ok;
            this.detail = detail;
        }

        public String getLabel() { return label; }
        public boolean isOk() { return ok; }
        public String getDetail() { return detail; }
    }

    private List<StatusItem> systemStatus() {
        try {
            return buildStatus();
        } catch (RuntimeException e) {
            // trang cài đặt vẫn phải mở được dù không đọc được trạng thái
            return List.of(new StatusItem("Không đọc được trạng thái hệ thống", false, e.getClass().getSimpleName()));
        }
    }

    private List<StatusItem> buildStatus() {
        List<StatusItem> list = new ArrayList<>();

        String acc = bank.getAccountNumber() == null ? "" : bank.getAccountNumber().trim();
        boolean qrReady = !acc.isEmpty() && !bank.resolvedBin().isEmpty();
        list.add(new StatusItem("Tài khoản nhận tiền & mã QR", qrReady,
                qrReady ? bank.getBankName() + " · " + mask(acc)
                        : "Chưa cấu hình KOSIS_BANK_ACCOUNT_NUMBER / KOSIS_BANK_ACCOUNT_NAME: trang thanh toán chưa hiện được mã QR."));

        boolean webhook = notBlank(env.getProperty("shop.payment.webhook-key"));
        list.add(new StatusItem("Tự đối soát chuyển khoản (webhook)", webhook,
                webhook ? "Đang bật" : "Đang tắt: nhân viên xác nhận đơn chuyển khoản bằng tay."));

        String mailHost = env.getProperty("spring.mail.host");
        boolean mail = notBlank(mailHost);
        list.add(new StatusItem("Gửi email (SMTP)", mail,
                mail ? mailHost.trim() : "Chưa cấu hình: nội dung email chỉ in ra console, không gửi thật."));

        String baseUrl = env.getProperty("kosis.base-url", "");
        boolean realUrl = notBlank(baseUrl) && !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1");
        list.add(new StatusItem("Địa chỉ website trong email", realUrl,
                realUrl ? baseUrl.trim() : "Đang là địa chỉ máy dev (" + baseUrl.trim() + "): link trong email sẽ không mở được với khách."));

        return list;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Che số tài khoản, chỉ hiện 4 số cuối */
    private static String mask(String account) {
        return account.length() <= 4 ? account : "•••• " + account.substring(account.length() - 4);
    }
}
