package com.kosis.shop.config;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.User;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the KOSIS fashion sample catalogue (Vietnamese) (codes KS...) the first time the app runs.
 *
 * - Runs only when no product with a "KS" code exists, so products you add or edit in
 *   /dashboard/kho-hang are never overwritten.
 * - The old GrandSport demo products (codes GS001..GS015 / GS101..GS112, football jerseys with
 *   placeholder images) are removed at the same moment. Set
 *   kosis.seed.replace-legacy-demo=false in application.properties to keep them.
 * - Images live in src/main/resources/static/images/products (see README in that folder).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final String IMG = "/images/products/";
    private static final String MODEL = "/images/collections/";

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${kosis.seed.replace-legacy-demo:true}")
    private boolean replaceLegacyDemo;

    /** Tài khoản admin đầu tiên - chỉ được tạo khi trong hệ thống CHƯA có ADMIN nào */
    @Value("${kosis.admin.username:admin}")
    private String adminUsername;

    /** Mật khẩu admin đọc từ config / biến môi trường KOSIS_ADMIN_PASSWORD; để trống thì app tự sinh và in ra console 1 lần */
    @Value("${kosis.admin.password:}")
    private String adminPassword;

    public DataSeeder(ProductRepository productRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Tạo admin mặc định nếu chưa có ADMIN nào (kể cả đang bị khoá thì vẫn tính là "đã có",
     * để không tự tạo thêm tài khoản ngoài ý muốn). Mật khẩu KHÔNG nằm trong code.
     */
    private void seedDefaultAdmin() {
        if (userRepository.countByRole("ADMIN") > 0) {
            return;
        }
        String username = adminUsername == null || adminUsername.isBlank() ? "admin" : adminUsername.trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            System.out.println("[KOSIS] Chưa có ADMIN nhưng tên đăng nhập \"" + username
                    + "\" đã tồn tại - không tạo tự động. Hãy đổi kosis.admin.username hoặc đổi role tài khoản đó trong database.");
            return;
        }
        boolean generated = adminPassword == null || adminPassword.isBlank();
        String rawPassword = generated ? randomPassword() : adminPassword;
        if (rawPassword.length() < 6) {
            System.out.println("[KOSIS] kosis.admin.password phải có ít nhất 6 ký tự - chưa tạo admin mặc định.");
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setFullName("Quản trị viên");
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        userRepository.save(admin);
        System.out.println("[KOSIS] Đã tạo tài khoản ADMIN mặc định: " + username
                + (generated ? " / mật khẩu tạm (chỉ hiện 1 lần, hãy đổi ngay): " + rawPassword : " (mật khẩu lấy từ kosis.admin.password)"));
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Sản phẩm có màu nhưng tồn kho còn dùng CHUNG cho mọi màu (dữ liệu trước khi có tồn kho theo màu) được chuyển sang
     * kho riêng từng màu: tồn kho của mỗi size chia đều cho các màu, TỔNG KHÔNG ĐỔI. Admin chỉnh lại số từng màu bằng
     * phiếu nhập (tăng) / trang Tồn kho (giảm). Chạy lại nhiều lần vẫn an toàn (sản phẩm đã chia thì bỏ qua).
     */
    private void migrateColorStock() {
        List<Product> legacy = productRepository.findAll().stream().filter(Product::isLegacyColorStock).toList();
        if (legacy.isEmpty()) {
            return;
        }
        for (Product p : legacy) {
            p.convertToPerColorStock();
        }
        productRepository.saveAll(legacy);
        System.out.println("[KOSIS] Đã chuyển " + legacy.size() + " sản phẩm có màu sang tồn kho riêng từng màu (chia đều, tổng tồn kho không đổi).");
    }

    @Override
    public void run(String... args) {
        seedDefaultAdmin();
        migrateColorStock();
        if (productRepository.existsByCodeStartingWith("KS")) {
            // An earlier English-language seed is replaced once by the Vietnamese catalogue
            List<Product> english = productRepository.findAll().stream()
                    .filter(p -> p.getCode() != null && p.getCode().startsWith("KS") && "Olive Camp Shirt".equals(p.getName()))
                    .toList();
            if (english.isEmpty()) {
                return; // catalogue already seeded (and possibly edited by the admin)
            }
            productRepository.deleteAll(productRepository.findAll().stream()
                    .filter(p -> p.getCode() != null && p.getCode().matches("KS\\d{3}")).toList());
        }
        if (replaceLegacyDemo) {
            List<Product> legacy = productRepository.findAll().stream()
                    .filter(p -> p.getCode() != null && p.getCode().matches("GS\\d{3}"))
                    .toList();
            if (!legacy.isEmpty()) {
                productRepository.deleteAll(legacy);
                System.out.println("[KOSIS] Removed " + legacy.size() + " legacy GrandSport demo products.");
            }
        }

        final String CLOTH = "S,M,L,XL";
        final String SHOES = "40,41,42,43,44";
        final String NONE = "-";

        // ---------- MEN ----------
        seed("KS101", "Áo sơ mi Camp xanh rêu", "MEN", "Áo", 1190000, 0, true, 4.7, CLOTH, 10, "olive-shirt", null,
                "Xanh rêu:#6b7a46,Cát:#d9cfb8", "Áo sơ mi camp form rộng, tay ngắn từ cotton twill wash, mềm và thoải mái như đã mặc lâu.");
        seed("KS102", "Áo sơ mi Oxford xanh", "MEN", "Áo", 1290000, 0, false, 4.6, CLOTH, 10, "blue-shirt", null,
                "Xanh trời:#a9c0e0,Trắng:#f5f5f3", "Áo sơ mi cài nút kinh điển từ cotton oxford chắc dáng. Có thể sơ vin hoặc mặc khoác ngoài áo thun.");
        seed("KS103", "Áo khoác bomber da lộn", "MEN", "Áo khoác", 2590000, 4290000, false, 4.9, CLOTH, 6, "brown-jacket", null,
                "Nâu thuốc lá:#7b4f2c", "Áo bomber đường nét gọn gàng bằng da lộn mềm, khóa bấm phía trước và bo cổ gân.");
        seed("KS104", "Quần ống rộng wash", "MEN", "Quần", 1690000, 0, true, 4.5, CLOTH, 9, "black-trousers", null,
                "Than chì:#2b2b2b", "Quần ống rộng từ denim đen wash, lưng cao và phom dáng rộng thoải mái.");
        seed("KS105", "Giày sneaker Gum Court", "MEN", "Giày", 2390000, 2990000, false, 4.8, SHOES, 8, "sport-sneakers", null,
                "Trắng:#f4f4f2,Đen:#111111", "Giày da lấy cảm hứng từ sân terrace với sọc tương phản và đế cao su màu tự nhiên.");
        seed("KS106", "Giày Retro Runner xám", "MEN", "Giày", 2790000, 0, true, 4.6, SHOES, 8, "white-retro-sneakers", null,
                "Xám:#c9c9c4,Kem:#ece6d6", "Giày chạy phối lưới và da lộn, đế êm, phom dáng hoài cổ thập niên chín mươi.");
        seed("KS107", "Đồng hồ mặt xanh ngọc", "MEN", "Phụ kiện", 5900000, 0, false, 4.9, NONE, 14, "green-watch", null,
                "Thép:#a8adb2", "Đồng hồ dây thép không gỉ với mặt số xanh ngọc tia nắng và kính phủ sapphire.");
        seed("KS108", "Balo đi làm Urban", "MEN", "Túi", 2190000, 3190000, false, 4.5, NONE, 18, "black-backpack", null,
                "Đen:#111111", "Balo form cứng chống nước, ngăn laptop có đệm và túi ẩn phía sau lưng.");
        seed("KS109", "Giày Terrace nâu", "MEN", "Giày", 1645000, 3290000, false, 4.4, SHOES, 3, "brown-sneakers", null,
                "Nâu bò:#b88a5a", "Giày da lộn mềm màu nâu ấm với sọc trắng mảnh. Những đôi cuối cùng của mùa.");

        // ---------- UNISEX ----------
        seed("KS110", "Giày Court xanh lá", "UNISEX", "Giày", 2590000, 0, true, 4.7, "39,40,41,42,43", 8, "green-white-sneakers", null,
                "Xanh lá:#2f6b4a", "Giày court thấp cổ bằng da trắng phối mảng xanh lá rừng.");
        seed("KS201", "Áo hoodie kem", "UNISEX", "Áo", 1390000, 0, true, 4.8, CLOTH, 12, "cream-hoodie", null,
                "Kem:#efe8da,Đen:#111111", "Áo hoodie nỉ dày lót bông mềm, form rộng và túi kangaroo sâu.");
        seed("KS202", "Kính mát Wayfarer", "UNISEX", "Phụ kiện", 1490000, 0, false, 4.6, NONE, 25, "black-sunglasses", null,
                "Đen:#111111", "Gọng acetate dày dặn, tròng xám đậm chống tia UV400.");
        seed("KS203", "Túi tote canvas KOSIS", "UNISEX", "Túi", 890000, 0, true, 4.7, NONE, 40, "kosis-canvas-tote", null,
                "Tự nhiên:#efe6d3", "Túi tote hằng ngày bằng canvas dày màu tự nhiên, có túi trong và logo KOSIS.");
        seed("KS204", "Nón KOSIS xanh rừng", "UNISEX", "Phụ kiện", 590000, 0, true, 4.6, NONE, 40, "green-cap", null,
                "Xanh rừng:#2f4a38", "Nón cotton sáu mảnh thêu chữ KOSIS, có khóa điều chỉnh phía sau.");
        seed("KS205", "Nón KOSIS màu cát", "UNISEX", "Phụ kiện", 590000, 0, false, 4.6, NONE, 40, "beige-cap", null,
                "Cát:#d7c6a5", "Nón cotton wash màu cát, vành cong và thêu tông sát màu.");
        seed("KS206", "Tai nghe Studio", "UNISEX", "Phụ kiện", 3290000, 4390000, false, 4.8, NONE, 15, "black-headphones", null,
                "Đen:#111111", "Tai nghe chụp tai không dây, chống ồn chủ động và dùng được ba mươi giờ.");
        seed("KS207", "Giày canvas cổ cao", "UNISEX", "Giày", 1490000, 0, false, 4.5, "38,39,40,41,42,43,44", 8, "black-high-top-sneakers", null,
                "Đen:#111111", "Mẫu giày cổ cao kinh điển bằng canvas đen với đế cao su lưu hóa.");
        seed("KS208", "Giày Trail Runner trắng", "UNISEX", "Giày", 2490000, 2990000, false, 4.6, "39,40,41,42,43", 8, "white-running-shoes", null,
                "Trắng:#f4f4f2", "Giày chạy nhẹ cho mỗi ngày, thân giày thoáng khí và đế cao su.");

        // ---------- WOMEN ----------
        seed("KS301", "Áo trench coat cổ điển", "WOMEN", "Áo khoác", 3290000, 5490000, false, 4.9, "XS,S,M,L", 6, "beige-coat",
                MODEL + "model-beige-coat-street.webp", "Nâu lạc đà:#cdb38c",
                "Áo trench dài đến gối bằng cotton gabardine chống thấm, có nắp chắn gió và dây đai tháo rời.");
        seed("KS302", "Túi đeo vai form cứng", "WOMEN", "Túi", 2990000, 0, true, 4.8, NONE, 14, "black-shoulder-bag", null,
                "Đen:#111111", "Túi đeo vai có form mềm cứng bằng da vân hạt, đóng bằng nam châm.");
        seed("KS303", "Túi hobo da", "WOMEN", "Túi", 3190000, 0, false, 4.7, NONE, 12, "black-hobo-bag", null,
                "Đen:#111111", "Túi hobo da đen mềm, đủ rộng cho cả ngày dài.");
        seed("KS304", "Túi xách tay màu kem", "WOMEN", "Túi", 2790000, 3490000, false, 4.7, NONE, 12, "beige-handbag", null,
                "Kem:#e7ddcc", "Túi xách tay tối giản bằng da mềm màu kem, kèm dây đeo tháo rời.");
        seed("KS305", "Kính mát mắt mèo", "WOMEN", "Phụ kiện", 1390000, 0, false, 4.5, NONE, 25, "black-sunglasses-alt", null,
                "Đen:#111111", "Gọng acetate mảnh với đuôi mắt mèo nhẹ nhàng, tròng xám đậm.");
        seed("KS306", "Giày sneaker trắng basic", "WOMEN", "Giày", 2190000, 0, true, 4.6, "36,37,38,39,40", 8, "white-sneakers", null,
                "Trắng:#f4f4f2,Kem:#ece6d6", "Giày da phối da lộn sạch sẽ, dễ phối với mọi trang phục.");

        // ---------- KIDS ----------
        final String KIDSHOES = "28,30,32,34";
        seed("KS401", "Áo hoodie mini màu kem", "KIDS", "Áo", 790000, 0, true, 4.8, "4Y,6Y,8Y,10Y", 10, "cream-hoodie", MODEL + "kids-fashion-look.webp",
                "Kem:#efe8da", "Áo hoodie nỉ mềm dành cho bé, túi rộng và form dễ mặc.");
        seed("KS402", "Túi tote canvas trẻ em", "KIDS", "Túi", 490000, 0, false, 4.6, NONE, 30, "kosis-canvas-tote", null,
                "Tự nhiên:#efe6d3", "Túi tote canvas nhỏ gọn cho sách vở, đồ ăn vặt và mọi thứ của bé.");
        seed("KS403", "Nón trẻ em xanh rừng", "KIDS", "Phụ kiện", 390000, 0, true, 4.6, NONE, 30, "green-cap", null,
                "Xanh rừng:#2f4a38", "Nón cotton mềm màu xanh rừng, có khóa điều chỉnh.");
        seed("KS404", "Nón trẻ em màu cát", "KIDS", "Phụ kiện", 390000, 0, false, 4.6, NONE, 30, "beige-cap", null,
                "Cát:#d7c6a5", "Nón cotton wash màu cát, kích thước dành cho bé.");
        seed("KS405", "Giày Court trẻ em", "KIDS", "Giày", 1190000, 0, false, 4.7, KIDSHOES, 8, "white-sneakers", null,
                "Trắng:#f4f4f2", "Giày da dễ xỏ, đế linh hoạt cho cả ngày vui chơi.");
        seed("KS406", "Giày Trail Runner trẻ em", "KIDS", "Giày", 1290000, 1590000, false, 4.5, KIDSHOES, 8, "white-running-shoes", null,
                "Trắng:#f4f4f2", "Giày chạy nhẹ, thoáng khí, sẵn sàng cho sân chơi.");
        seed("KS407", "Giày canvas cổ cao trẻ em", "KIDS", "Giày", 990000, 0, false, 4.5, KIDSHOES, 8, "black-high-top-sneakers", null,
                "Đen:#111111", "Giày canvas cổ cao kinh điển với phom dành cho bé.");
        seed("KS408", "Giày Retro trẻ em", "KIDS", "Giày", 1390000, 0, true, 4.6, KIDSHOES, 8, "white-retro-sneakers", null,
                "Xám:#c9c9c4", "Giày chạy retro đế êm với dải size nhỏ.");
    }

    private void seed(String code, String name, String gender, String category, long price, long oldPrice, boolean isNew,
                      double rating, String sizes, int stockPerSizeOrTotal, String imageName, String image2,
                      String colors, String description) {
        seed(code, name, gender, category, price, oldPrice, isNew, rating, sizes, stockPerSizeOrTotal, imageName, image2, null, colors, description);
    }

    private void seed(String code, String name, String gender, String category, long price, long oldPrice, boolean isNew,
                      double rating, String sizes, int stockPerSizeOrTotal, String imageName, String image2, String image3,
                      String colors, String description) {
        Product p = new Product();
        p.setCode(code);
        p.setName(name);
        p.setGender(gender);
        p.setCategory(category);
        p.setPrice(price);
        boolean sale = oldPrice > price;
        p.setOldPrice(sale ? oldPrice : 0);
        p.setDiscountPercent(sale ? (int) Math.round((oldPrice - price) * 100.0 / oldPrice) : 0);
        p.setNew(isNew);
        p.setRating(rating);
        p.setSection(sale ? "PROMOTION" : "FEATURED");
        p.setColors(colors);
        p.setDescription(description);
        p.setImage(IMG + imageName + ".webp");
        p.setImage2(image2);
        p.setImage3(image3);
        p.setSizes(sizes);
        if ("-".equals(sizes)) {
            p.setStock(stockPerSizeOrTotal);
            p.convertToPerColorStock();   // có màu -> chia kho riêng từng màu (không có màu thì không làm gì)
        } else {
            Map<String, Integer> perSize = new LinkedHashMap<>();
            for (String s : p.getSizeList()) perSize.put(s, stockPerSizeOrTotal);
            p.setSizeStockMap(perSize);   // also sets stock = total of all sizes
        }
        productRepository.save(p);
    }
}
