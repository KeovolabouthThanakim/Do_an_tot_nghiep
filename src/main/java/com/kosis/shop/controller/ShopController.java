package com.kosis.shop.controller;

import com.kosis.shop.entity.ContactMessage;
import com.kosis.shop.entity.NewsletterSubscriber;
import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.User;
import com.kosis.shop.repository.ContactMessageRepository;
import com.kosis.shop.repository.NewsletterSubscriberRepository;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.ProductSpecs;
import com.kosis.shop.service.ProductListingService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import com.kosis.shop.repository.UserRepository;
import com.kosis.shop.service.EmailService;
import com.kosis.shop.service.CategoryService;
import com.kosis.shop.service.OrderService;
import com.kosis.shop.service.SettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * KOSIS storefront pages: product listings (all / men / women / kids / sale / gifts / search),
 * wishlist, account, about / contact / info pages and the newsletter endpoint.
 */
@Controller
public class ShopController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private final ProductRepository productRepository;
    private final ProductListingService listingService;
    private final UserRepository userRepository;
    private final NewsletterSubscriberRepository newsletterRepository;
    private final OrderService orderService;
    private final EmailService emailService;
    private final ContactMessageRepository contactRepository;

    /** Thông tin liên hệ (trang /contact) và email nhận thông báo tin nhắn mới: chỉnh ở /dashboard/cai-dat (mặc định lấy từ application.properties) */
    private final SettingsService settings;
    private final CategoryService categoryService;

    public ShopController(ProductRepository productRepository, UserRepository userRepository,
                          NewsletterSubscriberRepository newsletterRepository, OrderService orderService,
                          EmailService emailService, ContactMessageRepository contactRepository,
                          ProductListingService listingService, SettingsService settings,
                          CategoryService categoryService) {
        this.settings = settings;
        this.categoryService = categoryService;
        this.listingService = listingService;
        this.emailService = emailService;
        this.contactRepository = contactRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.newsletterRepository = newsletterRepository;
        this.orderService = orderService;
    }

    // ==================== Product listings ====================

    @GetMapping({"/collections", "/san-pham"})
    public String collections(@RequestParam(required = false) String category,
                              @RequestParam(required = false) String gender,
                              @RequestParam(required = false) String sort,
                              @RequestParam(defaultValue = "1") int page, Model model) {
        Specification<Product> base = ProductSpecs.all();
        String g = gender == null ? "" : gender.trim().toUpperCase(Locale.ROOT);
        if ("KIDS".equals(g)) {
            base = ProductSpecs.genderOnly("KIDS");
        } else if (List.of("MEN", "WOMEN").contains(g)) {
            base = ProductSpecs.genderOrUnisex(g);
        }
        model.addAttribute("showGenderTabs", true);
        model.addAttribute("activeGender", g);
        return listing(model, base, "/collections", "Tất cả sản phẩm", "KOSIS SEASONS",
                "Toàn bộ sản phẩm của mùa này, gói gọn ở một nơi.",
                "/images/collections/model-beige-coat-street", category, sort, page, null, "collections");
    }

    @GetMapping("/men")
    public String men(@RequestParam(required = false) String category, @RequestParam(required = false) String sort,
                      @RequestParam(defaultValue = "1") int page, Model model) {
        return listing(model, ProductSpecs.genderOrUnisex("MEN"), "/men", "Nam", "KOSIS DÀNH CHO NAM",
                "Phom dáng thoải mái, lớp áo tiện dụng và những món cơ bản hằng ngày.",
                "/images/collections/model-mens-editorial", category, sort, page, null, "men");
    }

    @GetMapping("/women")
    public String women(@RequestParam(required = false) String category, @RequestParam(required = false) String sort,
                        @RequestParam(defaultValue = "1") int page, Model model) {
        return listing(model, ProductSpecs.genderOrUnisex("WOMEN"), "/women", "Nữ", "KOSIS DÀNH CHO NỮ",
                "Form dáng mềm mại, gam màu nhã nhặn và thiết kế bền lâu.",
                "/images/collections/model-womens-editorial", category, sort, page, null, "women");
    }

    @GetMapping("/kids")
    public String kids(@RequestParam(required = false) String category, @RequestParam(required = false) String sort,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        return listing(model, ProductSpecs.genderOnly("KIDS"), "/kids", "Trẻ em", "KOSIS DÀNH CHO TRẺ EM",
                "Những món cơ bản dễ mặc, bền bỉ cho tủ đồ của bé.",
                "/images/collections/kids-fashion-look", category, sort, page, null, "kids");
    }

    @GetMapping("/sale")
    public String sale(@RequestParam(required = false) String category, @RequestParam(required = false) String sort,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        return listing(model, ProductSpecs.onSale(), "/sale", "Giảm đến 50%", "ƯU ĐÃI THEO MÙA",
                "Những món chọn lọc với mức giá tốt nhất trong mùa.",
                "/images/collections/model-cap-streetwear", category, sort, page, null, "sale");
    }

    @GetMapping("/gifts")
    public String gifts(@RequestParam(required = false) String category, @RequestParam(required = false) String sort,
                        @RequestParam(defaultValue = "1") int page, Model model) {
        return listing(model, ProductSpecs.categoryIn(List.of("Phụ kiện", "Túi")), "/gifts", "Quà tặng", "GỢI Ý QUÀ TẶNG",
                "Túi xách, đồng hồ và những món nhỏ đáng để mở quà.",
                "/images/editorial/kosis-fabric-brand-detail", category, sort, page, null, "gifts");
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false, defaultValue = "") String q,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String sort,
                         @RequestParam(defaultValue = "1") int page, Model model) {
        String needle = q.trim();
        Specification<Product> base = needle.isEmpty() ? ProductSpecs.none() : ProductSpecs.keyword(needle);
        model.addAttribute("searchPage", true);
        model.addAttribute("popularSearches", HomeController.buildPopularSearches());
        String title = needle.isEmpty() ? "Tìm kiếm" : "Kết quả cho \u201C" + needle + "\u201D";
        return listing(model, base, "/search", title, "TÌM KIẾM",
                needle.isEmpty() ? "Nhập tên sản phẩm, danh mục hoặc màu sắc để bắt đầu." : null,
                null, category, sort, page, needle, "search");
    }

    /** Lọc danh mục + sắp xếp + phân trang trong DB rồi điền model cho products/index.html. */
    private String listing(Model model, Specification<Product> base, String basePath, String title, String eyebrow, String lede,
                           String heroImageNoExt, String category, String sort, int page, String query, String activePage) {
        String activeCategory = category == null ? "" : category.trim();
        String s = ProductListingService.normalizeSort(sort);
        Page<Product> result = listingService.find(base, activeCategory, s, page);
        int current = result.getNumber() + 1;
        int total = result.getTotalPages();
        long from = result.getTotalElements() == 0 ? 0 : (long) result.getNumber() * result.getSize() + 1;
        long to = from == 0 ? 0 : from + result.getNumberOfElements() - 1;

        model.addAttribute("pageTitle", title);
        model.addAttribute("pageEyebrow", eyebrow);
        model.addAttribute("pageLede", lede);
        model.addAttribute("heroImage", heroImageNoExt);
        model.addAttribute("basePath", basePath);
        model.addAttribute("products", result.getContent());
        model.addAttribute("productCount", result.getTotalElements());
        model.addAttribute("categories", listingService.categoriesOf(base, categoryService.names()));
        model.addAttribute("activeCategory", activeCategory);
        model.addAttribute("sort", s);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("activePage", activePage);
        // phân trang
        model.addAttribute("currentPage", current);
        model.addAttribute("totalPages", total);
        model.addAttribute("pageNumbers", ProductListingService.pageWindow(current, total));
        model.addAttribute("rangeFrom", from);
        model.addAttribute("rangeTo", to);
        return "products/index";
    }

    // ==================== Wishlist (stored in the browser; page filters the catalogue by saved ids) ====================

    @GetMapping("/wishlist")
    public String wishlist(Model model) {
        model.addAttribute("products", productRepository.findAllListed());
        model.addAttribute("activePage", "wishlist");
        return "pages/wishlist";
    }

    // ==================== Account ====================

    @GetMapping("/account")
    public String account(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/login?required=true";
        List<Order> orders = orderService.findByUsernameNewestFirst(principal.getName());
        model.addAttribute("user", user);
        model.addAttribute("orderCount", orders.size());
        model.addAttribute("recentOrders", orders.stream().limit(3).toList());
        model.addAttribute("activePage", "account");
        return "pages/account";
    }

    @PostMapping("/account")
    public String updateAccount(@RequestParam String fullName, @RequestParam(defaultValue = "") String phone,
                                @RequestParam(defaultValue = "") String address, @RequestParam(defaultValue = "") String email,
                                Principal principal, RedirectAttributes ra) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/login?required=true";
        if (fullName.isBlank() || fullName.trim().length() > 100) {
            ra.addFlashAttribute("accountError", "Vui lòng nhập họ và tên (tối đa 100 ký tự).");
            return "redirect:/account";
        }
        if (!email.isBlank() && !EMAIL.matcher(email.trim()).matches()) {
            ra.addFlashAttribute("accountError", "Email chưa hợp lệ.");
            return "redirect:/account";
        }
        user.setFullName(fullName.trim());
        user.setPhone(phone.trim());
        user.setAddress(address.trim());
        if (!email.isBlank()) user.setEmail(email.trim());
        userRepository.save(user);
        ra.addFlashAttribute("accountMessage", "Đã lưu thông tin của bạn.");
        return "redirect:/account";
    }

    // ==================== Newsletter ====================

    @PostMapping("/newsletter")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> subscribe(@RequestParam(defaultValue = "") String email) {
        String e = email.trim().toLowerCase(Locale.ROOT);
        if (e.length() > 190 || !EMAIL.matcher(e).matches()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Vui lòng nhập địa chỉ email hợp lệ."));
        }
        if (newsletterRepository.existsByEmailIgnoreCase(e)) {
            return ResponseEntity.ok(Map.of("ok", true, "message", "Email này đã đăng ký rồi. Cảm ơn bạn!"));
        }
        newsletterRepository.save(new NewsletterSubscriber(e));
        emailService.send(e, "Chào mừng bạn đến với KOSIS",
                "Cảm ơn bạn đã đăng ký nhận tin từ KOSIS!\n\nChúng tôi sẽ gửi đến bạn những bộ sưu tập mới và ưu đãi sớm nhất.\n\nKOSIS");
        return ResponseEntity.ok(Map.of("ok", true, "message", "Chào mừng bạn đến với cộng đồng KOSIS. Hãy kiểm tra hộp thư nhé."));
    }

    // ==================== About / Contact / info pages ====================

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("activePage", "about");
        return "pages/about";
    }

    @GetMapping("/contact")
    public String contact(Principal principal, Model model) {
        model.addAttribute("activePage", "contact");
        model.addAttribute("contactEmail", settings.get(SettingsService.CONTACT_EMAIL));
        model.addAttribute("contactPhone", settings.get(SettingsService.CONTACT_PHONE));
        model.addAttribute("contactHours", settings.get(SettingsService.CONTACT_HOURS));
        model.addAttribute("contactAddress", settings.get(SettingsService.CONTACT_ADDRESS));
        // Khách đã đăng nhập: điền sẵn họ tên + email (nếu chưa có dữ liệu cũ do redirect lỗi mang về)
        if (!model.containsAttribute("form")) {
            ContactMessage form = new ContactMessage();
            if (principal != null) {
                userRepository.findByUsername(principal.getName()).ifPresent(u -> {
                    form.setName(u.getFullName());
                    form.setEmail(u.getEmail());
                });
            }
            model.addAttribute("form", form);
        }
        return "pages/contact";
    }

    /** Lưu tin nhắn liên hệ vào database (nhân viên xem ở /dashboard/lien-he) */
    @PostMapping("/contact")
    public String submitContact(@RequestParam String name, @RequestParam String email,
                                @RequestParam(required = false) String subject,
                                @RequestParam String message,
                                @RequestParam(required = false) String website, // ô bẫy bot: người thật không thấy nên để trống
                                Principal principal, RedirectAttributes ra) {
        // Bot điền ô ẩn -> giả vờ thành công, không lưu
        if (website != null && !website.isBlank()) {
            ra.addFlashAttribute("contactMessage", "Cảm ơn bạn! Chúng tôi đã nhận được tin nhắn.");
            return "redirect:/contact";
        }
        String n = name == null ? "" : name.trim();
        String e = email == null ? "" : email.trim();
        String sub = subject == null ? "" : subject.trim();
        String msg = message == null ? "" : message.trim();

        String error = null;
        if (n.isEmpty() || n.length() > ContactMessage.MAX_NAME) {
            error = "Vui lòng nhập họ tên (tối đa " + ContactMessage.MAX_NAME + " ký tự).";
        } else if (e.length() > ContactMessage.MAX_EMAIL || !EMAIL.matcher(e).matches()) {
            error = "Vui lòng nhập địa chỉ email hợp lệ.";
        } else if (sub.length() > ContactMessage.MAX_SUBJECT) {
            error = "Tiêu đề quá dài (tối đa " + ContactMessage.MAX_SUBJECT + " ký tự).";
        } else if (msg.isEmpty() || msg.length() > ContactMessage.MAX_MESSAGE) {
            error = "Vui lòng nhập nội dung (tối đa " + ContactMessage.MAX_MESSAGE + " ký tự).";
        }

        ContactMessage cm = new ContactMessage();
        cm.setName(n);
        cm.setEmail(e);
        cm.setSubject(sub.isEmpty() ? null : sub);
        cm.setMessage(msg);
        if (error != null) {
            // Giữ lại nội dung khách đã gõ để không phải nhập lại
            ra.addFlashAttribute("form", cm);
            ra.addFlashAttribute("contactError", error);
            return "redirect:/contact";
        }

        if (principal != null) cm.setUsername(principal.getName());
        contactRepository.save(cm);

        String notifyEmail = settings.get(SettingsService.CONTACT_NOTIFY_EMAIL);
        if (notifyEmail != null && !notifyEmail.isBlank()) {
            emailService.send(notifyEmail, "[KOSIS] Tin nhắn liên hệ mới: " + (sub.isEmpty() ? "(không có tiêu đề)" : sub),
                    "Từ: " + n + " <" + e + ">\n\n" + msg + "\n\n— Xem và xử lý tại /dashboard/lien-he");
        }
        ra.addFlashAttribute("contactMessage", "Cảm ơn bạn! Chúng tôi đã nhận được tin nhắn và sẽ phản hồi trong vòng một ngày làm việc.");
        return "redirect:/contact";
    }

    public static class InfoSection {
        private final String heading;
        private final String body;
        public InfoSection(String heading, String body) { this.heading = heading; this.body = body; }
        public String getHeading() { return heading; }
        public String getBody() { return body; }
    }

    public static class InfoPage {
        private final String eyebrow;
        private final String title;
        private final String lede;
        private final List<InfoSection> sections;
        public InfoPage(String eyebrow, String title, String lede, List<InfoSection> sections) {
            this.eyebrow = eyebrow; this.title = title; this.lede = lede; this.sections = sections;
        }
        public String getEyebrow() { return eyebrow; }
        public String getTitle() { return title; }
        public String getLede() { return lede; }
        public List<InfoSection> getSections() { return sections; }
    }

    private static final Map<String, InfoPage> INFO = new LinkedHashMap<>();
    static {
        INFO.put("shipping", new InfoPage("HỖ TRỢ KHÁCH HÀNG", "Thông tin vận chuyển",
                "Đơn hàng KOSIS đến tay bạn như thế nào và khi nào.", List.of(
                new InfoSection("Thời gian giao hàng", "Đơn hàng được chuẩn bị trong 1\u20132 ngày làm việc. Giao hàng tiêu chuẩn mất 2\u20134 ngày làm việc trên toàn quốc."),
                new InfoSection("Phí vận chuyển", "Phí vận chuyển và mức đơn hàng được miễn phí vận chuyển hiển thị ở giỏ hàng và trang thanh toán trước khi bạn đặt hàng."),
                new InfoSection("Theo dõi đơn hàng", "Khi bưu kiện rời kho, chúng tôi sẽ liên hệ theo số điện thoại bạn cung cấp lúc thanh toán."))));
        INFO.put("returns", new InfoPage("HỖ TRỢ KHÁCH HÀNG", "Đổi trả & hoàn tiền",
                "Đổi ý? Chúng tôi giúp bạn việc đó thật đơn giản.", List.of(
                new InfoSection("Đổi trả trong @@RETURN_DAYS@@ ngày", "Sản phẩm chưa sử dụng, còn nguyên tem mác có thể trả trong vòng @@RETURN_DAYS@@ ngày kể từ khi nhận hàng. Bạn gửi yêu cầu ngay tại mục Đơn hàng của tôi (đơn đã giao thành công), cửa hàng sẽ duyệt và hướng dẫn gửi hàng về."),
                new InfoSection("Sản phẩm lỗi", "Nếu sản phẩm bị hư hỏng hoặc lỗi, hãy liên hệ kèm hình ảnh, chúng tôi sẽ đổi sản phẩm mới miễn phí."),
                new InfoSection("Hoàn tiền", "Khoản hoàn tiền được chuyển về phương thức thanh toán ban đầu trong 5\u20137 ngày làm việc."))));
        INFO.put("faq", new InfoPage("HỖ TRỢ KHÁCH HÀNG", "Câu hỏi thường gặp",
                "Giải đáp nhanh những câu hỏi chúng tôi nhận được nhiều nhất.", List.of(
                new InfoSection("Tôi thanh toán bằng cách nào?", "Bạn có thể chuyển khoản toàn bộ (quét mã QR ở bước thanh toán và tải ảnh bill lên) hoặc chọn thanh toán khi nhận hàng (COD). Với COD, bạn cần chuyển khoản đặt cọc một phần giá trị đơn trước (số tiền hiển thị ở bước thanh toán), phần còn lại trả cho nhân viên giao hàng khi nhận hàng. Nếu bạn không nhận hàng mà không có lý do chính đáng, tiền cọc sẽ không được hoàn lại."),
                new InfoSection("Khi nào đơn chuyển khoản được xác nhận?", "Nhân viên sẽ kiểm tra ảnh bill và xác nhận đơn. Bạn có thể theo dõi trạng thái tại mục Đơn hàng của tôi."),
                new InfoSection("Làm sao chọn kích cỡ?", "Trang sản phẩm có liệt kê các kích cỡ còn hàng. Nếu phân vân giữa hai size, bạn nên chọn size lớn hơn với áo khoác."),
                new InfoSection("Tôi có cần tài khoản không?", "Bạn có thể xem sản phẩm và thêm vào giỏ hàng mà không cần đăng nhập. Tài khoản cần thiết ở bước thanh toán để bạn theo dõi đơn hàng."))));
        INFO.put("privacy", new InfoPage("VỀ KOSIS", "Chính sách bảo mật",
                "Chúng tôi thu thập những thông tin nào, dùng vào việc gì và bạn kiểm soát chúng ra sao.", List.of(
                new InfoSection("Thông tin chúng tôi thu thập", "Tài khoản: tên đăng nhập, mật khẩu (được mã hóa một chiều, nhân viên không xem được), họ tên, email, số điện thoại, địa chỉ. Đơn hàng: người nhận, số điện thoại, địa chỉ giao hàng, ghi chú, sản phẩm đã mua, hình thức thanh toán và ảnh bill chuyển khoản bạn tải lên. Ngoài ra: tin nhắn bạn gửi ở trang Liên hệ, email đăng ký nhận bản tin, nội dung đánh giá sản phẩm, yêu cầu trả hàng và danh sách yêu thích khi bạn đã đăng nhập."),
                new InfoSection("Mục đích sử dụng", "Xử lý, giao và hỗ trợ đơn hàng; xác nhận thanh toán; xuất hóa đơn; xử lý đổi trả, hoàn tiền; trả lời liên hệ; gửi email về đơn hàng và đặt lại mật khẩu. Email bản tin chỉ dùng để gửi thông tin bộ sưu tập mới khi bạn đã đăng ký. Chúng tôi không bán thông tin cá nhân của bạn."),
                new InfoSection("Chia sẻ với bên thứ ba", "Đơn vị vận chuyển nhận tên, số điện thoại và địa chỉ giao hàng để giao đơn. Với đơn chuyển khoản, mã QR được tạo qua dịch vụ VietQR (img.vietqr.io) nên số tài khoản nhận tiền, số tiền và nội dung chuyển khoản của đơn sẽ được gửi tới dịch vụ này. Email được gửi qua nhà cung cấp dịch vụ thư mà cửa hàng cấu hình. Trang web tải phông chữ từ Google Fonts và biểu tượng từ jsDelivr nên các dịch vụ này có thể nhận địa chỉ IP của bạn. Chúng tôi cũng có thể cung cấp thông tin khi cơ quan nhà nước có thẩm quyền yêu cầu theo quy định pháp luật."),
                new InfoSection("Cookie và dữ liệu lưu trên trình duyệt", "Website dùng cookie phiên (đăng nhập, giỏ hàng) và cookie bảo mật chống giả mạo biểu mẫu; các cookie này cần thiết để website hoạt động. Một số thiết lập hiển thị có thể được lưu trong bộ nhớ của trình duyệt. Địa chỉ IP được dùng tạm thời để giới hạn số lần đăng nhập/đăng ký bất thường nhằm chống dò mật khẩu và không được lưu lâu dài."),
                new InfoSection("Lưu trữ và bảo mật", "Mật khẩu được mã hóa, ảnh bill được lưu ở khu vực không công khai và chỉ chủ đơn hoặc nhân viên có quyền mới xem được. Dữ liệu đơn hàng được giữ trong thời gian cần thiết để xử lý đơn, bảo hành, đổi trả và đáp ứng nghĩa vụ kế toán, thuế theo quy định. Không có hệ thống nào an toàn tuyệt đối; nếu phát hiện sự cố liên quan đến dữ liệu của bạn, chúng tôi sẽ thông báo theo quy định."),
                new InfoSection("Quyền của bạn", "Bạn có thể xem và cập nhật họ tên, email, số điện thoại, địa chỉ tại Tài khoản của tôi; và liên hệ (kể cả để hủy nhận bản tin) để yêu cầu truy cập, chỉnh sửa, hạn chế xử lý hoặc xóa dữ liệu, rút lại sự đồng ý theo quy định pháp luật Việt Nam về bảo vệ dữ liệu cá nhân (Nghị định 13/2023/NĐ-CP). Một số dữ liệu của đơn hàng đã phát sinh có thể phải được giữ lại theo luật. Gửi yêu cầu qua trang Liên hệ."),
                new InfoSection("Lưu ý", "Chính sách này mô tả cách hệ thống đang hoạt động, nhưng chưa được luật sư rà soát. Cửa hàng cần kiểm tra và bổ sung thông tin pháp nhân, địa chỉ, đầu mối xử lý dữ liệu trước khi chính thức vận hành."))));
        INFO.put("terms", new InfoPage("VỀ KOSIS", "Điều khoản & điều kiện",
                "Những quy định khi mua sắm tại KOSIS.", List.of(
                new InfoSection("Chấp nhận điều khoản", "Khi tạo tài khoản hoặc đặt hàng tại KOSIS, bạn đồng ý với các điều khoản này, Chính sách bảo mật và chính sách Đổi trả & hoàn tiền. Bạn cần cung cấp thông tin chính xác và chịu trách nhiệm về các hoạt động diễn ra trong tài khoản của mình, kể cả việc giữ bí mật mật khẩu."),
                new InfoSection("Đặt hàng và xác nhận", "Việc gửi đơn là đề nghị mua hàng. Đơn chỉ được xem là đã chấp nhận khi nhân viên xác nhận: với chuyển khoản là sau khi kiểm tra bill và tiền đã về tài khoản; với thanh toán khi nhận hàng (COD) là sau khi cửa hàng kiểm tra tiền cọc đã về tài khoản và xác nhận đơn. Chúng tôi có thể từ chối hoặc hủy đơn (ví dụ hết hàng, thông tin giao hàng không hợp lệ, không xác nhận được thanh toán, nghi ngờ gian lận) và sẽ thông báo lý do; nếu bạn đã chuyển khoản, cửa hàng sẽ liên hệ để hoàn tiền. Bạn có thể tự hủy đơn khi đơn còn đang chờ xác nhận."),
                new InfoSection("Giá bán và thanh toán", "Giá hiển thị bằng đồng Việt Nam (₫). Phí vận chuyển và mã giảm giá được tính và hiển thị ở giỏ hàng, trang thanh toán trước khi bạn đặt hàng. Mã giảm giá có điều kiện, thời hạn và số lượt dùng riêng, không quy đổi thành tiền mặt. Nếu có sai sót rõ ràng về giá, chúng tôi có thể điều chỉnh hoặc hủy đơn trước khi xác nhận và thông báo cho bạn. Với chuyển khoản, bạn cần nhập đúng số tiền và nội dung được hướng dẫn."),
                new InfoSection("Giao hàng và nhận hàng", "Thời gian giao hàng chỉ là ước tính. Bạn cần cung cấp đúng số điện thoại, địa chỉ và nghe máy khi đơn vị vận chuyển liên hệ. Khi hàng đã giao tới, bạn xác nhận \"Tôi đã nhận được hàng\" ở mục Đơn hàng của tôi; nếu sau số ngày quy định bạn không phản hồi, hệ thống tự chuyển đơn sang đã giao thành công. Việc nhiều lần từ chối nhận hàng sẽ được ghi nhận và cửa hàng có thể cân nhắc khi xử lý các đơn sau."),
                new InfoSection("Đổi trả và hoàn tiền", "Việc đổi trả tuân theo chính sách Đổi trả & hoàn tiền: gửi yêu cầu trong thời hạn quy định kể từ khi nhận hàng, sản phẩm chưa sử dụng và còn nguyên tem mác, trừ trường hợp sản phẩm lỗi do cửa hàng."),
                new InfoSection("Hành vi bị cấm", "Bạn không được dùng website để gian lận thanh toán (như tải bill giả), dò mật khẩu hoặc truy cập trái phép tài khoản khác, tạo tài khoản hàng loạt, gây quá tải hệ thống hoặc đăng đánh giá giả mạo, xúc phạm. Chúng tôi có quyền khóa tài khoản và từ chối đơn khi phát hiện vi phạm."),
                new InfoSection("Nội dung và quyền sở hữu trí tuệ", "Hình ảnh, nội dung và thương hiệu trên website thuộc quyền của KOSIS hoặc bên cấp phép, không được sao chép hay dùng cho mục đích thương mại khi chưa được đồng ý bằng văn bản. Đánh giá bạn gửi có thể được hiển thị công khai trên trang sản phẩm."),
                new InfoSection("Giới hạn trách nhiệm và thay đổi điều khoản", "Trong phạm vi pháp luật cho phép, KOSIS không chịu trách nhiệm cho thiệt hại gián tiếp phát sinh từ việc gián đoạn website ngoài khả năng kiểm soát của chúng tôi; điều này không làm mất các quyền của người tiêu dùng theo luật. Chúng tôi có thể cập nhật điều khoản; đơn đã xác nhận trước đó áp dụng điều khoản tại thời điểm đặt hàng. Tranh chấp được ưu tiên giải quyết qua thương lượng, nếu không được thì giải quyết tại tòa án có thẩm quyền của Việt Nam."),
                new InfoSection("Lưu ý", "Điều khoản này mô tả cách cửa hàng đang vận hành nhưng chưa được luật sư rà soát. Cửa hàng cần kiểm tra và bổ sung thông tin pháp nhân, mã số thuế, địa chỉ trước khi chính thức vận hành."))));
        INFO.put("our-story", new InfoPage("VỀ KOSIS", "Câu chuyện thương hiệu",
                "Một thương hiệu nhỏ với tầm nhìn dài hạn về những gì chúng ta mặc.", List.of(
                new InfoSection("Vì sao là KOSIS", "KOSIS bắt đầu từ một ý tưởng giản dị: quần áo nên tinh tế chứ không ồn ào. Chúng tôi thiết kế với bảng màu trung tính và chất liệu tự nhiên để mỗi món đồ đều dễ phối với tủ đồ của bạn."),
                new InfoSection("Cách chúng tôi làm việc", "Chúng tôi chọn chất liệu cẩn thận, giữ bộ sưu tập nhỏ gọn và hợp tác với những người thợ cùng chia sẻ sự kiên nhẫn đó."),
                new InfoSection("Chúng tôi hướng tới", "Nhiều thiết kế hơn cho nhiều người hơn, nhưng luôn nghĩ đến việc sở hữu ít hơn mà tốt hơn."))));
    }

    @GetMapping("/{slug:shipping|returns|faq|privacy|terms|our-story}")
    public String info(@PathVariable String slug, Model model) {
        model.addAttribute("page", INFO.get(slug));
        model.addAttribute("activePage", "info");
        return "pages/info";
    }
}
