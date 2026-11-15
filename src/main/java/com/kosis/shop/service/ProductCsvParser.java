package com.kosis.shop.service;

import com.kosis.shop.entity.Product;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Đọc file CSV để tạo HÀNG LOẠT sản phẩm mới (kèm tồn đầu kỳ). Chỉ phân tích + kiểm tra, KHÔNG ghi gì vào DB:
 * có lỗi ở bất kỳ dòng nào thì trả về danh sách lỗi (kèm số dòng) và không sản phẩm nào được tạo.
 *
 * <p>Mỗi dòng = một biến thể (màu, size) của sản phẩm; các dòng cùng {@code code} gộp thành một sản phẩm.
 * Thông tin chung của sản phẩm (name, category, gender, list_price, discount_percent) chỉ cần điền ở một dòng
 * (các dòng khác để trống, hoặc điền giống hệt). Cột (không phân biệt hoa / thường, thứ tự tuỳ ý):
 * <pre>
 *   code, name, category, gender, list_price      bắt buộc
 *   discount_percent                              0-90, mặc định 0
 *   color, color_hex                              màu và mã màu (#rrggbb); thiếu mã màu = #cccccc
 *   size                                          để trống ở mọi dòng = sản phẩm không có size
 *   qty, cost                                     tồn đầu kỳ và giá vốn / cái; qty &gt; 0 thì cost bắt buộc
 * </pre>
 * Ký tự ngăn cách: dấu phẩy, chấm phẩy (Excel tiếng Việt) hoặc tab - tự nhận từ dòng tiêu đề.
 */
public final class ProductCsvParser {

    public static final int MAX_ROWS = 5000;
    public static final int MAX_PRODUCTS = 500;
    public static final int MAX_COLORS = 12;
    public static final long MAX_BYTES = 2L * 1024 * 1024;
    public static final String DEFAULT_HEX = "#cccccc";

    /** File mẫu cho nút "Tải file mẫu" (có BOM ở nơi gửi để Excel đọc đúng tiếng Việt). */
    public static final String SAMPLE_CSV = String.join("\r\n",
            "code,name,category,gender,list_price,discount_percent,color,color_hex,size,qty,cost",
            "KS101,Áo sơ mi Camp xanh rêu,Áo,MEN,450000,0,Olive,#6b7a46,M,10,200000",
            "KS101,,,,,,Olive,#6b7a46,L,5,200000",
            "KS101,,,,,,Đen,#111111,M,8,210000",
            "KS101,,,,,,Đen,#111111,L,0,",
            "KS102,Túi đeo chéo da,Túi,UNISEX,690000,10,,,,12,300000",
            "KS103,Mũ lưỡi trai be,Phụ kiện,UNISEX,220000,0,,,,0,",
            "");

    private static final List<String> REQUIRED = List.of("code", "name", "category", "gender", "list_price");

    /** Kết quả: sản phẩm hợp lệ (chưa lưu) hoặc danh sách lỗi. Có lỗi thì {@code products} rỗng. */
    public record Result(List<StockImportService.NewProduct> products, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty() && !products.isEmpty();
        }
    }

    private ProductCsvParser() {
    }

    /** Giải mã byte của file thành chữ: bắt buộc UTF-8 (bỏ BOM nếu có). */
    public static String decode(byte[] bytes) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return s.startsWith("\uFEFF") ? s.substring(1) : s;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("File không phải mã hoá UTF-8. Hãy lưu lại dạng \"CSV UTF-8\" (Excel: Save As > CSV UTF-8) rồi thử lại.");
        }
    }

    public static Result parse(String text, Predicate<String> codeExists) {
        return parse(text, codeExists, Product.CATEGORIES);
    }

    /** [categories]: các danh mục được phép (CategoryService.names()); cột category của file phải khớp một trong số đó. */
    public static Result parse(String text, Predicate<String> codeExists, java.util.Collection<String> categories) {
        List<String> errors = new ArrayList<>();
        List<Rec> recs = tokenize(text == null ? "" : text);
        if (recs.isEmpty()) {
            return fail("File trống: cần dòng tiêu đề và ít nhất một dòng dữ liệu.");
        }

        // --- dòng tiêu đề ---
        Rec head = recs.get(0);
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < head.cells.size(); i++) {
            col.putIfAbsent(head.cells.get(i).trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_"), i);
        }
        List<String> missing = new ArrayList<>();
        for (String r : REQUIRED) if (!col.containsKey(r)) missing.add(r);
        if (!missing.isEmpty()) {
            return fail("Thiếu cột bắt buộc trong dòng tiêu đề: " + String.join(", ", missing)
                    + ". Hãy dùng đúng tên cột như file mẫu.");
        }

        // --- các dòng dữ liệu, gom theo mã sản phẩm ---
        if (recs.size() - 1 > MAX_ROWS) {
            return fail("File quá nhiều dòng (tối đa " + MAX_ROWS + " dòng dữ liệu). Hãy chia nhỏ file.");
        }
        Map<String, List<Row>> byCode = new LinkedHashMap<>();
        for (int i = 1; i < recs.size(); i++) {
            Rec rec = recs.get(i);
            Row row = new Row(rec.line, col, rec.cells);
            if (row.isBlank()) continue;
            String code = row.get("code");
            if (code.isEmpty()) {
                errors.add("Dòng " + row.line + ": thiếu mã sản phẩm (cột code).");
                continue;
            }
            byCode.computeIfAbsent(code.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(row);
        }
        if (byCode.isEmpty() && errors.isEmpty()) {
            return fail("File không có dòng dữ liệu nào bên dưới dòng tiêu đề.");
        }
        if (byCode.size() > MAX_PRODUCTS) {
            return fail("File có " + byCode.size() + " sản phẩm, tối đa " + MAX_PRODUCTS + " sản phẩm mỗi lần. Hãy chia nhỏ file.");
        }

        List<StockImportService.NewProduct> products = new ArrayList<>();
        for (List<Row> rows : byCode.values()) {
            build(rows, codeExists, categories, products, errors);
        }
        return errors.isEmpty() ? new Result(products, errors) : new Result(List.of(), errors);
    }

    // ------------------------------------------------------------------ một sản phẩm

    private static void build(List<Row> rows, Predicate<String> codeExists, java.util.Collection<String> categories,
                              List<StockImportService.NewProduct> out, List<String> errors) {
        final int before = errors.size();
        Row first = rows.get(0);
        String code = first.get("code");
        String tag = "mã " + code;

        if (code.length() > 30) errors.add("Dòng " + first.line + ": " + tag + " quá dài (tối đa 30 ký tự).");
        if (codeExists != null && codeExists.test(code)) {
            errors.add("Dòng " + first.line + ": " + tag + " đã tồn tại trong kho. File này chỉ để thêm sản phẩm MỚI.");
        }

        // thông tin chung: lấy giá trị đầu tiên có điền, các dòng khác (nếu điền) phải giống hệt
        String name = common(rows, "name", false, tag, errors);
        String category = common(rows, "category", false, tag, errors);
        String gender = common(rows, "gender", false, tag, errors);
        String priceRaw = common(rows, "list_price", true, tag, errors);
        String discountRaw = common(rows, "discount_percent", true, tag, errors);
        if (name.isEmpty()) errors.add("Dòng " + first.line + ": " + tag + " thiếu tên sản phẩm (cột name).");
        if (name.length() > 150) errors.add("Dòng " + first.line + ": tên của " + tag + " quá dài (tối đa 150 ký tự).");

        long listPrice = number(priceRaw, "list_price", first.line, errors);
        if (priceRaw.isEmpty()) errors.add("Dòng " + first.line + ": " + tag + " thiếu giá bán niêm yết (cột list_price).");
        else if (listPrice < 1) errors.add("Dòng " + first.line + ": giá bán niêm yết của " + tag + " phải lớn hơn 0.");
        long discount = number(discountRaw, "discount_percent", first.line, errors);
        if (discount > 90) errors.add("Dòng " + first.line + ": discount_percent của " + tag + " phải từ 0 đến 90.");

        // --- biến thể: từng dòng = (màu, size, số lượng, giá vốn) ---
        boolean anyColor = false, anySize = false;
        for (Row r : rows) {
            if (!r.get("color").isEmpty()) anyColor = true;
            if (!cleanSize(r.get("size")).isEmpty()) anySize = true;
        }

        Map<String, String[]> colors = new LinkedHashMap<>();      // tên thường -> {tên, hex}
        Map<String, String> explicitHex = new HashMap<>();         // tên thường -> mã màu do file khai báo
        Set<String> sizes = new LinkedHashSet<>();
        Map<String, Row> seen = new HashMap<>();
        List<StockImportService.OpeningLine> opening = new ArrayList<>();

        for (Row r : rows) {
            String color = cleanColor(r.get("color"));
            String size = cleanSize(r.get("size"));

            if (anyColor && color.isEmpty()) {
                errors.add("Dòng " + r.line + ": thiếu màu (các dòng khác của " + tag + " có màu).");
            }
            if (anySize && size.isEmpty()) {
                errors.add("Dòng " + r.line + ": thiếu size (các dòng khác của " + tag + " có size).");
            }
            if (!size.isEmpty() && size.matches(".*[,;\\s].*")) {
                errors.add("Dòng " + r.line + ": size \"" + size + "\" không được chứa dấu cách, dấu phẩy hoặc chấm phẩy.");
            }
            if (size.length() > 10) {
                errors.add("Dòng " + r.line + ": size \"" + size + "\" quá dài.");
            }

            if (!color.isEmpty()) {
                String hexRaw = r.get("color_hex");
                String hex = hexRaw.isEmpty() ? null : normHex(hexRaw);
                if (!hexRaw.isEmpty() && hex == null) {
                    errors.add("Dòng " + r.line + ": mã màu \"" + hexRaw + "\" không hợp lệ (VD: #f4f4f2 hoặc f4f4f2).");
                }
                String key = color.toLowerCase(Locale.ROOT);
                String[] existing = colors.get(key);       // {tên, hex}
                if (existing == null) {
                    colors.put(key, new String[]{color, hex == null ? DEFAULT_HEX : hex});
                    if (hex != null) explicitHex.put(key, hex);
                } else if (hex != null) {
                    String prev = explicitHex.get(key);
                    if (prev == null) {                    // dòng trước chưa có mã màu: dùng mã vừa khai báo
                        existing[1] = hex;
                        explicitHex.put(key, hex);
                    } else if (!prev.equals(hex)) {
                        errors.add("Dòng " + r.line + ": màu \"" + color + "\" có hai mã màu khác nhau (" + prev + " và " + hex + ").");
                    }
                }
            }
            if (!size.isEmpty()) sizes.add(size);

            String variant = color.toLowerCase(Locale.ROOT) + "|" + size;
            Row dup = seen.putIfAbsent(variant, r);
            if (dup != null) {
                errors.add("Dòng " + r.line + ": trùng màu / size với dòng " + dup.line + " của " + tag + ".");
            }

            long qty = number(r.get("qty"), "qty", r.line, errors);
            long cost = number(r.get("cost"), "cost", r.line, errors);
            if (qty > StockImportService.MAX_QTY) {
                errors.add("Dòng " + r.line + ": qty phải từ 0 đến " + StockImportService.MAX_QTY + ".");
            } else if (qty > 0) {
                if (cost < 1 || cost > StockImportService.MAX_COST) {
                    errors.add("Dòng " + r.line + ": có số lượng (qty) thì phải có giá vốn (cost) lớn hơn 0.");
                } else {
                    opening.add(new StockImportService.OpeningLine(size.isEmpty() ? null : size,
                            color.isEmpty() ? null : color, (int) qty, cost));
                }
            }
        }

        if (colors.size() > MAX_COLORS) {
            errors.add("Dòng " + first.line + ": " + tag + " có " + colors.size() + " màu, tối đa " + MAX_COLORS + " màu.");
        }

        // --- dựng Product rồi dùng đúng bộ kiểm tra của form Thêm sản phẩm ---
        Product p = new Product();
        p.setCode(code);
        p.setName(name);
        p.setCategory(category);
        p.setGender(gender);
        p.setDiscountPercent((int) Math.max(0, Math.min(90, discount)));
        StringBuilder cs = new StringBuilder();
        for (String[] c : colors.values()) {
            if (cs.length() > 0) cs.append(',');
            cs.append(c[0]).append(':').append(c[1]);
        }
        p.setColors(cs.length() == 0 ? null : cs.toString());
        p.setSizes(sizes.isEmpty() ? "-" : String.join(",", sizes));   // không cột size = không có size
        if (p.getSizes().length() > 100) {
            errors.add("Dòng " + first.line + ": danh sách size của " + tag + " quá dài.");
        }
        String classify = ProductRules.normalizeClassification(p, categories);
        if (classify != null) errors.add("Dòng " + first.line + ": " + tag + " - " + classify);

        if (errors.size() == before) {
            ProductRules.applyPricing(p, listPrice);
            out.add(new StockImportService.NewProduct(p, opening));
        }
    }

    /** Giá trị chung của sản phẩm: lấy ô điền đầu tiên; ô điền sau mà khác thì báo lỗi. */
    private static String common(List<Row> rows, String column, boolean numeric, String tag, List<String> errors) {
        String value = "";
        Row from = null;
        for (Row r : rows) {
            String v = r.get(column);
            if (v.isEmpty()) continue;
            if (from == null) {
                value = v;
                from = r;
            } else if (!same(value, v, numeric)) {
                errors.add("Dòng " + r.line + ": " + column + " của " + tag + " khác dòng " + from.line
                        + " (\"" + v + "\" so với \"" + value + "\"). Mỗi sản phẩm chỉ có một giá trị.");
            }
        }
        return value;
    }

    private static boolean same(String a, String b, boolean numeric) {
        if (numeric) return a.replaceAll("[^0-9]", "").equals(b.replaceAll("[^0-9]", ""));
        return a.trim().equalsIgnoreCase(b.trim());
    }

    // ------------------------------------------------------------------ giá trị từng ô

    /** Số nguyên không âm; chấp nhận dấu chấm / phẩy / cách / ₫ ngăn nghìn (VD 450.000). Ô trống = 0; sai = 0 và ghi lỗi. */
    private static long number(String raw, String column, int line, List<String> errors) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return 0;
        String d = t.replaceAll("[\\s.,₫đĐ]", "");
        if (!d.matches("\\d{1,12}")) {
            errors.add("Dòng " + line + ": " + column + " \"" + t + "\" không phải số hợp lệ (chỉ dùng số nguyên dương).");
            return 0;
        }
        return Long.parseLong(d);
    }

    private static String cleanColor(String s) {
        return s == null ? "" : s.replaceAll("[,:|]", " ").trim().replaceAll("\\s+", " ");
    }

    private static String cleanSize(String s) {
        String t = s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
        return t.equals("-") ? "" : t;
    }

    /** "f4f4f2" / "#abc" / "#F4F4F2" -> "#f4f4f2"; sai định dạng -> null */
    private static String normHex(String v) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) return null;
        if (t.charAt(0) != '#') t = "#" + t;
        if (t.matches("#[0-9a-fA-F]{3}")) {
            t = "#" + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2) + t.charAt(3) + t.charAt(3);
        }
        return t.matches("#[0-9a-fA-F]{6}") ? t.toLowerCase(Locale.ROOT) : null;
    }

    private static Result fail(String message) {
        return new Result(List.of(), List.of(message));
    }

    // ------------------------------------------------------------------ CSV thô

    private record Rec(int line, List<String> cells) {
    }

    private static final class Row {
        final int line;
        private final Map<String, Integer> col;
        private final List<String> cells;

        Row(int line, Map<String, Integer> col, List<String> cells) {
            this.line = line;
            this.col = col;
            this.cells = cells;
        }

        String get(String column) {
            Integer i = col.get(column);
            return i == null || i >= cells.size() ? "" : cells.get(i).trim();
        }

        boolean isBlank() {
            for (String c : cells) if (!c.trim().isEmpty()) return false;
            return true;
        }
    }

    /** Tách CSV theo RFC 4180 (ngoặc kép, "" là một dấu ngoặc, xuống dòng trong ô); tự nhận dấu ngăn cách; bỏ dòng trống. */
    private static List<Rec> tokenize(String text) {
        char delim = detectDelimiter(text);
        List<Rec> out = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false, started = false;
        int line = 1, recLine = 1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n') line++;
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0) {
                inQuotes = true;
                started = true;
            } else if (c == delim) {
                cur.add(field.toString());
                field.setLength(0);
                started = true;
            } else if (c == '\n') {
                if (started || field.length() > 0) {
                    cur.add(field.toString());
                    out.add(new Rec(recLine, cur));
                }
                cur = new ArrayList<>();
                field.setLength(0);
                started = false;
                line++;
                recLine = line;
            } else if (c != '\r') {
                field.append(c);
                started = true;
            }
        }
        if (started || field.length() > 0) {
            cur.add(field.toString());
            out.add(new Rec(recLine, cur));
        }
        return out;
    }

    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String head = end < 0 ? text : text.substring(0, end);
        int comma = 0, semi = 0, tab = 0;
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if (c == ',') comma++;
            else if (c == ';') semi++;
            else if (c == '\t') tab++;
        }
        if (semi > comma && semi >= tab) return ';';
        if (tab > comma && tab > semi) return '\t';
        return ',';
    }
}
