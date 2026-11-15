package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.StockImport;
import com.kosis.shop.entity.StockImportItem;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.StockImportRepository;
import com.kosis.shop.entity.StockMovement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Tạo phiếu nhập hàng: kiểm tra dữ liệu, CỘNG tồn kho (đúng size, đúng màu) và cập nhật giá nhập của sản phẩm.
 * Đây là CÁCH DUY NHẤT để tăng tồn kho; sản phẩm mới ở trạng thái "chờ nhập hàng" sẽ tự lên kệ khi có phiếu đầu tiên.
 *
 * Giá nhập của sản phẩm = GIÁ TRUNG BÌNH theo số lượng giữa hàng đang tồn và lô mới nhập
 * (VD: còn 10 cái giá nhập 100.000, nhập thêm 10 cái giá 120.000 -> giá nhập = 110.000).
 * Nhờ vậy "Giá trị tồn (theo giá nhập)" ở trang Kho hàng luôn sát thực tế. Hết hàng thì lấy giá của lô mới.
 * Giá từng lô vẫn được giữ nguyên trong phiếu để tra cứu.
 */
@Service
public class StockImportService {

    public static final int MAX_QTY = 1_000_000;
    public static final long MAX_COST = 1_000_000_000L;
    public static final int MAX_LINES = 100;

    /** Một dòng nhập do người dùng khai báo trên form (color để trống nếu sản phẩm không có màu). */
    public record Line(Long productId, String size, String color, int quantity, long unitCost) {
        public Line(Long productId, String size, int quantity, long unitCost) {
            this(productId, size, null, quantity, unitCost);
        }
    }

    /** Nguồn hàng ghi trên phiếu tự tạo khi khai báo tồn kho ban đầu lúc tạo sản phẩm (Thêm sản phẩm / CSV). */
    public static final String OPENING_SUPPLIER = "Tồn đầu kỳ";
    public static final String OPENING_NOTE = "Tồn kho ban đầu khi tạo sản phẩm";

    /** Tồn đầu kỳ của một ô (màu, size): số lượng + giá vốn / cái. size / color để trống nếu sản phẩm không có. */
    public record OpeningLine(String size, String color, int quantity, long unitCost) {
    }

    /** Sản phẩm mới kèm tồn đầu kỳ. {@code opening} rỗng = chỉ tạo sản phẩm, vẫn "chờ nhập hàng". */
    public record NewProduct(Product product, List<OpeningLine> opening) {
    }

    private final StockImportRepository importRepository;
    private final ProductRepository productRepository;

    /** Sổ kho (không bắt buộc: bỏ trống thì chỉ không ghi lịch sử). */
    @Autowired(required = false)
    private StockMovementService movements;

    public StockImportService(StockImportRepository importRepository, ProductRepository productRepository) {
        this.importRepository = importRepository;
        this.productRepository = productRepository;
    }

    /**
     * @throws IllegalArgumentException thông báo tiếng Việt, hiển thị thẳng cho người dùng
     */
    @Transactional
    public StockImport create(String supplier, String supplierContact, String reference, LocalDate importDate,
                              String note, List<Line> lines, String createdBy) {
        String sup = clean(supplier);
        if (sup.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập nguồn hàng (tên nhà cung cấp).");
        if (sup.length() > 150) throw new IllegalArgumentException("Tên nguồn hàng quá dài (tối đa 150 ký tự).");
        String contact = clean(supplierContact);
        if (contact.length() > 200) throw new IllegalArgumentException("Thông tin liên hệ quá dài (tối đa 200 ký tự).");
        String ref = clean(reference);
        if (ref.length() > 60) throw new IllegalArgumentException("Số chứng từ quá dài (tối đa 60 ký tự).");
        String nt = clean(note);
        if (nt.length() > 500) throw new IllegalArgumentException("Ghi chú quá dài (tối đa 500 ký tự).");
        if (importDate == null) throw new IllegalArgumentException("Vui lòng chọn ngày nhập hàng.");
        if (importDate.isAfter(LocalDate.now().plusDays(1))) {
            throw new IllegalArgumentException("Ngày nhập hàng không được ở tương lai.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng thêm ít nhất một sản phẩm vào phiếu nhập.");
        }
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("Mỗi phiếu tối đa " + MAX_LINES + " dòng.");
        }

        StockImport phieu = new StockImport();
        phieu.setSupplier(sup);
        phieu.setSupplierContact(contact.isEmpty() ? null : contact);
        phieu.setReference(ref.isEmpty() ? null : ref);
        phieu.setImportDate(importDate);
        phieu.setNote(nt.isEmpty() ? null : nt);
        phieu.setCreatedBy(createdBy);

        // Khoá các sản phẩm theo THỨ TỰ id tăng dần: 2 phiếu nhập cùng lúc không khoá chéo nhau gây deadlock
        lines.stream().map(Line::productId).filter(java.util.Objects::nonNull).distinct().sorted()
                .forEach(productRepository::findByIdForUpdate);

        int n = 0;
        for (Line l : lines) {
            n++;
            if (l.productId() == null) throw new IllegalArgumentException("Dòng " + n + ": chưa chọn sản phẩm.");
            if (l.quantity() < 1 || l.quantity() > MAX_QTY) {
                throw new IllegalArgumentException("Dòng " + n + ": số lượng phải từ 1 đến " + MAX_QTY + ".");
            }
            if (l.unitCost() < 1 || l.unitCost() > MAX_COST) {
                throw new IllegalArgumentException("Dòng " + n + ": vui lòng nhập giá nhập (lớn hơn 0).");
            }

            // Khoá dòng sản phẩm: nhập hàng và bán hàng cùng lúc không làm lệch tồn kho
            Product p = productRepository.findByIdForUpdate(l.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không còn tồn tại (có thể đã bị xoá)."));

            // Sản phẩm có màu: phải chọn màu, hàng nhập cộng đúng vào kho của màu đó
            // (dữ liệu cũ dùng kho chung các màu -> chuyển sang chia đều theo màu TRƯỚC, rồi mới kiểm tra size)
            String color = null;
            if (p.isHasColors()) {
                Product.ColorOption co = p.findColor(l.color());
                if (co == null) {
                    throw new IllegalArgumentException("Dòng " + n + ": vui lòng chọn màu cho \"" + p.getName() + "\".");
                }
                color = co.getName();
                p.convertToPerColorStock();
            }

            String size = null;
            if (p.isPerSizeStock()) {
                size = l.size() == null ? "" : l.size().trim().toUpperCase();
                if (!p.getSizeList().contains(size)) {
                    throw new IllegalArgumentException("Dòng " + n + ": vui lòng chọn size cho \"" + p.getName() + "\".");
                }
            }

            // Giá nhập trung bình mới (tính TRƯỚC khi cộng tồn kho)
            long oldStock = Math.max(0, p.getStock());
            long oldCost = p.getImportPrice();
            long newCost = l.unitCost();
            if (oldStock > 0 && oldCost > 0) {
                newCost = Math.round((oldStock * oldCost + (double) l.quantity() * l.unitCost()) / (oldStock + l.quantity()));
            }
            p.adjustStock(size, color, l.quantity());
            p.setImportPrice(newCost);
            p.setPendingImport(false);   // phiếu nhập đầu tiên -> sản phẩm tự lên kệ bán
            productRepository.save(p);

            StockImportItem item = new StockImportItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductCode(p.getCode());
            item.setSize(size);
            item.setColor(color);
            item.setQuantity(l.quantity());
            item.setUnitCost(l.unitCost());
            phieu.addItem(item);
        }
        StockImport saved = importRepository.save(phieu);
        if (movements != null) {
            String movementRef = "Phiếu nhập #" + saved.getId();
            for (StockImportItem it : saved.getItems()) {
                movements.record(it.getProductId(), it.getProductName(), it.getProductCode(), it.getSize(), it.getColor(),
                        StockMovement.TYPE_IMPORT, it.getQuantity(), "Nhập từ " + sup, movementRef, createdBy);
            }
        }
        return saved;
    }

    /**
     * Tạo MỘT sản phẩm mới. Có tồn đầu kỳ thì sinh luôn phiếu nhập "Tồn đầu kỳ" nên sản phẩm lên kệ ngay và vẫn có
     * lịch sử; không có thì sản phẩm ở trạng thái "chờ nhập hàng". Sản phẩm và phiếu cùng một giao dịch: lỗi thì không lưu gì.
     *
     * @return phiếu "Tồn đầu kỳ" vừa tạo, hoặc null nếu không có tồn đầu kỳ
     */
    @Transactional
    public StockImport createProduct(Product product, List<OpeningLine> opening, String createdBy) {
        List<StockImport> slips = createProducts(List.of(new NewProduct(product, opening)), createdBy);
        return slips.isEmpty() ? null : slips.get(0);
    }

    /**
     * Tạo NHIỀU sản phẩm mới cùng lúc (nhập từ CSV) trong một giao dịch. Tồn đầu kỳ của tất cả sản phẩm được gom vào
     * các phiếu "Tồn đầu kỳ" (mỗi phiếu tối đa {@link #MAX_LINES} dòng). Tồn kho vẫn chỉ tăng qua phiếu nhập.
     */
    @Transactional
    public List<StockImport> createProducts(List<NewProduct> items, String createdBy) {
        List<Line> all = new ArrayList<>();
        for (NewProduct np : items) {
            Product p = np.product();
            // Luôn bắt đầu từ 0 / chờ nhập hàng: số lượng chỉ vào kho qua phiếu bên dưới (có nguồn + giá vốn)
            p.setPendingImport(true);
            p.setStock(0);
            p.setImportPrice(0);
            p.setSizeStockMap(new LinkedHashMap<>());   // có màu / size -> mọi ô = 0; không có -> không làm gì
            Product saved = productRepository.save(p);
            for (OpeningLine o : np.opening()) {
                if (o.quantity() > 0) {
                    all.add(new Line(saved.getId(), o.size(), o.color(), o.quantity(), o.unitCost()));
                }
            }
        }
        List<StockImport> slips = new ArrayList<>();
        for (int i = 0; i < all.size(); i += MAX_LINES) {
            slips.add(create(OPENING_SUPPLIER, null, null, LocalDate.now(), OPENING_NOTE,
                    all.subList(i, Math.min(all.size(), i + MAX_LINES)), createdBy));
        }
        return slips;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}