package com.kosis.shop.service;

import com.kosis.shop.entity.ShopSetting;
import com.kosis.shop.repository.ShopSettingRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Cài đặt hệ thống chỉnh được trên /dashboard/cai-dat (chỉ ADMIN).
 *
 * Thứ tự ưu tiên khi đọc một giá trị:
 *   1. Giá trị ADMIN đã lưu trong bảng shop_settings (ghi đè)
 *   2. Giá trị trong application.properties / local.properties / biến môi trường
 *   3. Giá trị dự phòng viết cứng ở bảng định nghĩa bên dưới
 *
 * Chỉ lưu vào DB những mục KHÁC mặc định; đặt lại đúng bằng mặc định thì xoá dòng ghi đè.
 * Giá trị được giữ trong bộ nhớ nên gọi getter thoải mái ở mỗi request. Bộ nhớ được nạp lại từ DB khi khởi động,
 * ngay sau mỗi lần lưu trên máy này, và tự nạp lại sau mỗi kosis.settings.refresh-seconds giây (mặc định 30; 0 = tắt)
 * để khi chạy NHIỀU máy chủ, máy khác sửa cài đặt thì máy này cũng tự cập nhật mà không cần khởi động lại.
 *
 * KHÔNG đưa vào đây: tài khoản ngân hàng, khoá webhook, SMTP, mật khẩu... (nhạy cảm, giữ ở biến môi trường).
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    // ---- Khoá cài đặt ----
    public static final String CONTACT_EMAIL = "contact.email";
    public static final String CONTACT_PHONE = "contact.phone";
    public static final String CONTACT_HOURS = "contact.hours";
    public static final String CONTACT_ADDRESS = "contact.address";
    public static final String CONTACT_NOTIFY_EMAIL = "contact.notify-email";
    public static final String INVOICE_SELLER_NAME = "invoice.seller-name";
    public static final String INVOICE_TAX_CODE = "invoice.seller-tax-code";
    public static final String INVOICE_ADDRESS = "invoice.seller-address";
    public static final String SHIPPING_FEE = "shipping.fee";
    public static final String SHIPPING_FREE_THRESHOLD = "shipping.free-threshold";
    public static final String SHIPPING_AUTO_COMPLETE_DAYS = "shipping.auto-complete-days";
    public static final String COD_DEPOSIT_PERCENT = "cod.deposit-percent";
    public static final String RETURNS_WINDOW_DAYS = "returns.window-days";
    public static final String LISTING_PAGE_SIZE = "listing.page-size";

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 .()-]{5,29}$");
    private static final Pattern TAX_CODE = Pattern.compile("^\\d{10}(-?\\d{3})?$");
    private static final Pattern DIGITS = Pattern.compile("^\\d{1,12}$");

    /** Kiểu dữ liệu của một mục - quyết định cách kiểm tra và loại ô nhập trên trang */
    public enum Type { TEXT, EMAIL, PHONE, TAX_CODE, MONEY, INT }

    /** Định nghĩa một mục cài đặt (lớp thường có getter để Thymeleaf đọc được) */
    public static final class Def {
        private final String key;
        private final String label;
        private final String hint;
        private final String property;
        private final String fallback;
        private final Type type;
        private final boolean required;
        private final long min;
        private final long max;
        private final boolean wide;
        private final String unit;

        private Def(String key, String label, String hint, String property, String fallback, Type type,
                    boolean required, long min, long max, boolean wide, String unit) {
            this.key = key;
            this.label = label;
            this.hint = hint;
            this.property = property;
            this.fallback = fallback;
            this.type = type;
            this.required = required;
            this.min = min;
            this.max = max;
            this.wide = wide;
            this.unit = unit;
        }

        private static Def str(Type type, String key, String label, String hint, String property, String fallback,
                               boolean required, int maxLength, boolean wide) {
            return new Def(key, label, hint, property, fallback, type, required, 0, maxLength, wide, "");
        }

        private static Def num(Type type, String key, String label, String hint, String property, String fallback,
                               long min, long max, String unit) {
            return new Def(key, label, hint, property, fallback, type, true, min, max, false, unit);
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public String getHint() { return hint; }
        public Type getType() { return type; }
        public boolean isRequired() { return required; }
        public boolean isWide() { return wide; }
        public String getUnit() { return unit; }
        public boolean isNumber() { return type == Type.MONEY || type == Type.INT; }
        /** Với kiểu chữ: số ký tự tối đa */
        public int getMaxLength() { return (int) max; }
        /** Với kiểu số: giới hạn dưới / trên */
        public long getMin() { return min; }
        public long getMax() { return max; }

        /** "từ 0 đến 10.000.000 ₫" cho kiểu số (rỗng với kiểu chữ) */
        public String getRangeText() {
            if (!isNumber()) return "";
            return "từ " + groupDigits(min) + " đến " + groupDigits(max) + (unit.isEmpty() ? "" : " " + unit);
        }

        /** Giá trị cho thuộc tính type của thẻ input */
        public String getInputType() {
            switch (type) {
                case EMAIL: return "email";
                case PHONE: return "tel";
                case MONEY:
                case INT: return "number";
                default: return "text";
            }
        }
    }

    /** Một nhóm mục hiển thị thành một thẻ trên trang cài đặt */
    public static final class Group {
        private final String id;
        private final String title;
        private final String subtitle;
        private final String icon;
        private final List<Def> defs;

        private Group(String id, String title, String subtitle, String icon, List<Def> defs) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.defs = List.copyOf(defs);
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public String getIcon() { return icon; }
        public List<Def> getDefs() { return defs; }
    }

    /** Kết quả lưu: có lỗi thì KHÔNG lưu gì cả (all-or-nothing) */
    public static final class SaveResult {
        private final Map<String, String> fieldErrors;
        private final int changed;

        private SaveResult(Map<String, String> fieldErrors, int changed) {
            this.fieldErrors = fieldErrors;
            this.changed = changed;
        }

        public boolean isOk() { return fieldErrors.isEmpty(); }
        public Map<String, String> getFieldErrors() { return fieldErrors; }
        public int getChanged() { return changed; }
    }

    private static final List<Group> GROUPS = List.of(
            new Group("contact", "Thông tin cửa hàng",
                    "Hiện ở trang Liên hệ của cửa hàng.", "bi-shop", List.of(
                    Def.str(Type.EMAIL, CONTACT_EMAIL, "Email liên hệ", "",
                            "kosis.contact.email", "hello@kosis.example", true, 100, false),
                    Def.str(Type.PHONE, CONTACT_PHONE, "Số điện thoại", "",
                            "kosis.contact.phone", "1900 1234", true, 30, false),
                    Def.str(Type.TEXT, CONTACT_HOURS, "Giờ làm việc", "",
                            "kosis.contact.hours", "Thứ 2 – Thứ 7, 9:00–18:00", true, 100, true),
                    Def.str(Type.TEXT, CONTACT_ADDRESS, "Địa chỉ", "",
                            "kosis.contact.address", "123 đường Atelier, Quận 1, TP. Hồ Chí Minh", true, 250, true),
                    Def.str(Type.EMAIL, CONTACT_NOTIFY_EMAIL, "Email nhận thông báo tin nhắn mới",
                            "Có tin nhắn liên hệ mới sẽ gửi email báo đến đây. Để trống = chỉ lưu trong Dashboard (cần cấu hình SMTP để gửi được).",
                            "kosis.contact.notify-email", "", false, 100, true))),

            new Group("invoice", "Hoá đơn bán hàng",
                    "Thông tin bên bán in trên hoá đơn và gửi kèm email xác nhận đơn.", "bi-receipt", List.of(
                    Def.str(Type.TEXT, INVOICE_SELLER_NAME, "Tên bên bán", "",
                            "kosis.invoice.seller-name", "KOSIS", true, 120, true),
                    Def.str(Type.TAX_CODE, INVOICE_TAX_CODE, "Mã số thuế",
                            "10 hoặc 13 chữ số. Để trống = không in mã số thuế.",
                            "kosis.invoice.seller-tax-code", "", false, 14, false),
                    Def.str(Type.TEXT, INVOICE_ADDRESS, "Địa chỉ bên bán",
                            "Để trống = dùng địa chỉ cửa hàng ở trên.",
                            "kosis.invoice.seller-address", "", false, 250, true))),

            new Group("shipping", "Vận chuyển",
                    "Áp dụng ngay cho giỏ hàng, thanh toán và thanh thông báo của cửa hàng.", "bi-truck", List.of(
                    Def.num(Type.MONEY, SHIPPING_FEE, "Phí vận chuyển",
                            "Phí cố định mỗi đơn. 0 = miễn phí mọi đơn.",
                            "shop.shipping.fee", "30000", 0, 10_000_000L, "₫"),
                    Def.num(Type.MONEY, SHIPPING_FREE_THRESHOLD, "Miễn phí vận chuyển từ",
                            "Đơn có tiền hàng (sau giảm giá) từ mức này trở lên được miễn phí ship. 0 = không bao giờ miễn phí.",
                            "shop.shipping.free-threshold", "500000", 0, 1_000_000_000L, "₫"),
                    Def.num(Type.INT, SHIPPING_AUTO_COMPLETE_DAYS, "Tự hoàn tất đơn sau",
                            "Hãng vận chuyển báo đã giao mà khách không phản hồi quá số ngày này thì đơn tự chuyển “Đã giao thành công”. Nên đặt 3–7 ngày.",
                            "kosis.shipping.auto-complete-days", "5", 1, 30, "ngày"))),

            new Group("cod", "Đặt cọc đơn COD",
                    "Khách chọn thanh toán khi nhận hàng (COD) phải chuyển khoản tiền cọc trước; phần còn lại trả cho shipper khi nhận hàng.",
                    "bi-cash-coin", List.of(
                    Def.num(Type.INT, COD_DEPOSIT_PERCENT, "Tiền cọc đơn COD",
                            "% giá trị đơn khách phải chuyển khoản trước (làm tròn lên 1.000₫). 0 = tắt, COD không cần cọc. Khuyên dùng 30%; 50% nếu hàng giá trị cao hoặc tỷ lệ bom hàng cao.",
                            "shop.cod.deposit-percent", "30", 0, 100, "%"))),

            new Group("policy", "Chính sách & hiển thị",
                    "Thời hạn đổi trả và cách hiển thị danh sách sản phẩm.", "bi-arrow-repeat", List.of(
                    Def.num(Type.INT, RETURNS_WINDOW_DAYS, "Thời hạn đổi trả",
                            "Khách được gửi yêu cầu trả hàng trong số ngày này kể từ lúc nhận hàng.",
                            "kosis.returns.window-days", "7", 1, 60, "ngày"),
                    Def.num(Type.INT, LISTING_PAGE_SIZE, "Số sản phẩm mỗi trang",
                            "Ở trang danh sách và tìm kiếm của cửa hàng.",
                            "kosis.listing.page-size", "20", 4, 48, "sản phẩm"))));

    private static final Map<String, Def> DEFS;

    static {
        Map<String, Def> m = new LinkedHashMap<>();
        for (Group g : GROUPS) {
            for (Def d : g.getDefs()) {
                m.put(d.getKey(), d);
            }
        }
        DEFS = Collections.unmodifiableMap(m);
    }

    private static final long DEFAULT_REFRESH_SECONDS = 30;

    private final ShopSettingRepository repository;
    private final Environment env;
    private final LongSupplier clock;
    /** Sau bao nhiêu mili-giây thì nạp lại từ DB (<= 0: không tự nạp lại) */
    private final long refreshMillis;

    /** Bản chụp các mục đã ghi đè (thay nguyên cả map mỗi lần nạp lại nên đọc không cần khoá) */
    private volatile Map<String, ShopSetting> overrides = Map.of();
    /** Lúc nạp gần nhất (kể cả lần nạp lỗi, để DB sập không bị gọi dồn dập) */
    private volatile long loadedAt;
    /** Chỉ cho MỘT luồng nạp lại tại một thời điểm; các luồng khác cứ dùng bản đang có */
    private final AtomicBoolean refreshing = new AtomicBoolean();

    @Autowired
    public SettingsService(ShopSettingRepository repository, Environment env) {
        this(repository, env, System::currentTimeMillis);
    }

    /** Dùng trong test: tự điều khiển đồng hồ */
    SettingsService(ShopSettingRepository repository, Environment env, LongSupplier clock) {
        this.repository = repository;
        this.env = env;
        this.clock = clock;
        this.refreshMillis = readRefreshSeconds(env) * 1000L;
    }

    private static long readRefreshSeconds(Environment env) {
        String v = env.getProperty("kosis.settings.refresh-seconds");
        if (v == null || v.isBlank()) return DEFAULT_REFRESH_SECONDS;
        try {
            return Math.max(0, Long.parseLong(v.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_REFRESH_SECONDS;
        }
    }

    @PostConstruct
    void load() {
        reload();
    }

    /** Nạp lại các mục ghi đè từ DB. Lỗi DB thì giữ nguyên bản cũ (và dùng mặc định nếu chưa có) */
    public void reload() {
        loadedAt = clock.getAsLong();
        try {
            Map<String, ShopSetting> m = new HashMap<>();
            for (ShopSetting s : repository.findAll()) {
                if (s.getKey() != null && DEFS.containsKey(s.getKey()) && s.getValue() != null) {
                    m.put(s.getKey(), s);
                }
            }
            overrides = Map.copyOf(m);
        } catch (RuntimeException e) {
            log.warn("Không đọc được bảng shop_settings, dùng giá trị mặc định trong application.properties: {}", e.toString());
        }
    }

    /** Bản ghi đè đang dùng; quá hạn thì nạp lại từ DB (chỉ một luồng nạp, luồng khác dùng bản cũ trong lúc đó) */
    private Map<String, ShopSetting> current() {
        if (refreshMillis > 0 && clock.getAsLong() - loadedAt >= refreshMillis && refreshing.compareAndSet(false, true)) {
            try {
                reload();
            } finally {
                refreshing.set(false);
            }
        }
        return overrides;
    }

    // ------------------------------------------------------------------ đọc

    public static List<Group> groups() {
        return GROUPS;
    }

    /** Mọi khoá cài đặt hợp lệ (để lọc tham số gửi lên từ form) */
    public static Set<String> keys() {
        return DEFS.keySet();
    }

    private static Def def(String key) {
        Def d = DEFS.get(key);
        if (d == null) {
            throw new IllegalArgumentException("Khoá cài đặt không tồn tại: " + key);
        }
        return d;
    }

    /** Giá trị mặc định (từ application.properties / biến môi trường, không có thì dùng giá trị dự phòng) */
    public String defaultOf(String key) {
        return defaultOf(def(key));
    }

    private String defaultOf(Def d) {
        String v = rawDefault(d);
        // "Địa chỉ bên bán" mặc định mượn địa chỉ cửa hàng: coi như để trống để luôn đi theo địa chỉ cửa hàng đang dùng
        if (INVOICE_ADDRESS.equals(d.getKey()) && v.equals(rawDefault(def(CONTACT_ADDRESS)))) {
            return "";
        }
        return v;
    }

    private String rawDefault(Def d) {
        String v = env.getProperty(d.property);
        return v == null ? d.fallback : v.trim();
    }

    /** Giá trị đang áp dụng (ghi đè nếu có, không thì mặc định) */
    public String get(String key) {
        Def d = def(key);
        ShopSetting s = current().get(key);
        return s != null ? s.getValue() : defaultOf(d);
    }

    public long getLong(String key) {
        Def d = def(key);
        Long v = parseLong(get(key));
        if (v == null) v = parseLong(defaultOf(d));
        if (v == null) v = parseLong(d.fallback);
        return v == null ? 0 : v;
    }

    public int getInt(String key) {
        long v = getLong(key);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
    }

    /** Địa chỉ in trên hoá đơn: để trống thì dùng địa chỉ cửa hàng */
    public String sellerAddress() {
        String a = get(INVOICE_ADDRESS);
        return a == null || a.isBlank() ? get(CONTACT_ADDRESS) : a;
    }

    public boolean isCustomized(String key) {
        return current().containsKey(key);
    }

    public Set<String> customizedKeys() {
        return new HashSet<>(current().keySet());
    }

    /** Giá trị đang áp dụng của mọi mục (key -> giá trị) */
    public Map<String, String> currentValues() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Def d : DEFS.values()) m.put(d.getKey(), get(d.getKey()));
        return m;
    }

    /** Giá trị mặc định đã định dạng để hiện cho người dùng (key -> "30.000 ₫", "7 ngày", "(để trống)") */
    public Map<String, String> defaultLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Def d : DEFS.values()) m.put(d.getKey(), format(d, defaultOf(d)));
        return m;
    }

    /** Lần sửa gần nhất (để hiện "Cập nhật lần cuối ...") */
    public Optional<ShopSetting> lastChange() {
        return current().values().stream().max(Comparator.comparing(ShopSetting::getUpdatedAt));
    }

    private static String format(Def d, String raw) {
        if (raw == null || raw.isBlank()) return "(để trống)";
        if (!d.isNumber()) return raw;
        Long n = parseLong(raw);
        if (n == null) return raw;
        return groupDigits(n) + (d.getUnit().isEmpty() ? "" : " " + d.getUnit());
    }

    private static String groupDigits(long n) {
        return String.format(Locale.forLanguageTag("vi-VN"), "%,d", n);
    }

    private static Long parseLong(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ ghi

    /**
     * Kiểm tra và lưu các mục gửi lên. Mục nào không có trong {@code submitted} thì giữ nguyên.
     * Có BẤT KỲ lỗi nào thì không lưu gì (kết quả chứa lỗi theo từng mục).
     *
     * @param username tên đăng nhập người sửa (ghi lại để biết ai đổi)
     */
    public SaveResult save(Map<String, String> submitted, String username) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> clean = new LinkedHashMap<>();

        for (Def d : DEFS.values()) {
            if (!submitted.containsKey(d.getKey())) continue;
            String v = normalize(d, submitted.get(d.getKey()));
            String err = validate(d, v);
            if (err != null) {
                errors.put(d.getKey(), err);
            } else {
                clean.put(d.getKey(), v);
            }
        }
        if (!errors.isEmpty()) {
            return new SaveResult(errors, 0);
        }

        List<ShopSetting> toSave = new ArrayList<>();
        List<String> toDelete = new ArrayList<>();
        for (Map.Entry<String, String> e : clean.entrySet()) {
            Def d = def(e.getKey());
            String newVal = e.getValue();
            String current = get(d.getKey());
            if (newVal.equals(current)) continue;                   // không đổi gì
            if (newVal.equals(defaultOf(d))) {
                toDelete.add(d.getKey());                           // về đúng mặc định: bỏ ghi đè
            } else {
                toSave.add(new ShopSetting(d.getKey(), newVal, username));
            }
            log.info("Cài đặt \"{}\" đổi bởi {}: [{}] -> [{}]", d.getKey(), username, current, newVal);
        }
        if (!toSave.isEmpty()) repository.saveAll(toSave);
        if (!toDelete.isEmpty()) repository.deleteAllById(toDelete);
        reload();
        return new SaveResult(Map.of(), toSave.size() + toDelete.size());
    }

    /** Xoá mọi ghi đè: toàn bộ cài đặt quay về giá trị mặc định */
    public int resetAll(String username) {
        int n = current().size();
        repository.deleteAll();
        reload();
        log.info("Mọi cài đặt đã được đặt lại mặc định bởi {} ({} mục tuỳ chỉnh bị xoá)", username, n);
        return n;
    }

    // ------------------------------------------------------------------ kiểm tra

    /** Chuẩn hoá chuỗi gửi lên: bỏ ký tự điều khiển; với số: bỏ khoảng trắng và dấu phân cách hàng nghìn, bỏ số 0 đầu */
    static String normalize(Def d, String raw) {
        String s = raw == null ? "" : raw.replaceAll("\\p{Cntrl}", " ").trim();
        if (d.isNumber()) {
            s = s.replaceAll("[\\s.,_]", "");
            if (DIGITS.matcher(s).matches()) {
                s = Long.toString(Long.parseLong(s));
            }
        }
        return s;
    }

    /** Trả về thông báo lỗi tiếng Việt, hoặc null nếu hợp lệ. {@code v} đã qua normalize() */
    static String validate(Def d, String v) {
        if (v.isEmpty()) {
            return d.isRequired() ? d.getLabel() + " không được để trống." : null;
        }
        if (d.isNumber()) {
            if (!DIGITS.matcher(v).matches()) {
                return d.getLabel() + " phải là số nguyên không âm.";
            }
            long n = Long.parseLong(v);
            if (n < d.getMin() || n > d.getMax()) {
                return d.getLabel() + " phải từ " + groupDigits(d.getMin()) + " đến " + groupDigits(d.getMax())
                        + (d.getUnit().isEmpty() ? "" : " " + d.getUnit()) + ".";
            }
            return null;
        }
        if (v.length() > d.getMaxLength()) {
            return d.getLabel() + " quá dài (tối đa " + d.getMaxLength() + " ký tự).";
        }
        switch (d.getType()) {
            case EMAIL:
                return EMAIL.matcher(v).matches() ? null : d.getLabel() + " không phải địa chỉ email hợp lệ.";
            case PHONE:
                return PHONE.matcher(v).matches() ? null : d.getLabel() + " không hợp lệ (chỉ gồm số, khoảng trắng và + . ( ) -).";
            case TAX_CODE:
                return TAX_CODE.matcher(v).matches() ? null : d.getLabel() + " phải gồm 10 hoặc 13 chữ số.";
            default:
                return null;
        }
    }
}
