package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.StockMovement;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sổ kho: ghi lại mỗi lần tồn kho thay đổi + điều chỉnh GIẢM thủ công (bắt buộc có lý do).
 * Tăng tồn kho chỉ qua phiếu nhập (StockImportService).
 */
@Service
public class StockMovementService {

    public static final int MAX_ADJUST_QTY = 1_000_000;

    private final StockMovementRepository repository;
    private final ProductRepository productRepository;

    public StockMovementService(StockMovementRepository repository, ProductRepository productRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
    }

    /** Ghi một dòng sổ kho (chạy chung transaction với nơi gọi). */
    @Transactional
    public void record(Long productId, String name, String code, String size, String type, int delta,
                       String reason, String reference, String by) {
        record(productId, name, code, size, null, type, delta, reason, reference, by);
    }

    /** Ghi một dòng sổ kho kèm MÀU (null nếu sản phẩm không có màu). */
    @Transactional
    public void record(Long productId, String name, String code, String size, String color, String type, int delta,
                       String reason, String reference, String by) {
        if (delta == 0) return;
        StockMovement m = new StockMovement();
        m.setProductId(productId);
        m.setProductName(name == null || name.isBlank() ? "(không rõ)" : name);
        m.setProductCode(code);
        m.setSize(size == null || size.isBlank() ? null : size.trim().toUpperCase());
        m.setColor(color == null || color.isBlank() ? null : color.trim());
        m.setType(type);
        m.setDelta(delta);
        m.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        m.setReference(reference);
        m.setCreatedBy(by);
        repository.save(m);
    }

    /**
     * Giảm tồn kho thủ công (kiểm kê thiếu, hàng hỏng/mất...). Bắt buộc có lý do; không cho giảm quá số đang có.
     * @throws IllegalArgumentException thông báo tiếng Việt, hiển thị thẳng cho người dùng
     */
    @Transactional
    public StockMovement adjustDown(Long productId, String size, int quantity, String reason, String by) {
        return adjustDown(productId, size, null, quantity, reason, by);
    }

    /** Giảm tồn kho của đúng (size, màu). Sản phẩm có màu thì bắt buộc chọn màu. */
    @Transactional
    public StockMovement adjustDown(Long productId, String size, String color, int quantity, String reason, String by) {
        String why = reason == null ? "" : reason.trim().replaceAll("\\s+", " ");
        if (why.length() < 3) throw new IllegalArgumentException("Vui lòng nhập lý do điều chỉnh (ít nhất 3 ký tự).");
        if (why.length() > 300) throw new IllegalArgumentException("Lý do quá dài (tối đa 300 ký tự).");
        if (quantity < 1 || quantity > MAX_ADJUST_QTY) {
            throw new IllegalArgumentException("Số lượng giảm phải từ 1 đến " + MAX_ADJUST_QTY + ".");
        }
        if (productId == null) throw new IllegalArgumentException("Vui lòng chọn sản phẩm.");

        Product p = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không còn tồn tại (có thể đã bị xoá)."));

        // Sản phẩm có màu: điều chỉnh đúng kho của màu đó (dữ liệu cũ chưa chia theo màu thì chuyển sang chia đều trước)
        String col = "";
        if (p.isHasColors()) {
            Product.ColorOption c = p.findColor(color);
            if (c == null) {
                throw new IllegalArgumentException("Vui lòng chọn màu cho \"" + p.getName() + "\".");
            }
            col = c.getName();
            p.convertToPerColorStock();
        }

        String sz = size == null ? "" : size.trim().toUpperCase();
        if (p.isPerSizeStock()) {
            if (!p.getSizeList().contains(sz)) {
                throw new IllegalArgumentException("Vui lòng chọn size cho \"" + p.getName() + "\".");
            }
        } else {
            sz = "";   // chưa chia kho theo size / không có size: dùng chung 1 kho
        }
        int have = p.getStockOf(sz, col);
        if (quantity > have) {
            throw new IllegalArgumentException("Chỉ còn " + have + " trong kho"
                    + (col.isEmpty() ? "" : " (màu " + col + ")")
                    + (sz.isEmpty() ? "" : " (size " + sz + ")") + ", không thể giảm " + quantity + ".");
        }
        p.adjustStock(sz, col, -quantity);
        productRepository.save(p);

        StockMovement m = new StockMovement();
        m.setProductId(p.getId());
        m.setProductName(p.getName());
        m.setProductCode(p.getCode());
        m.setSize(sz.isEmpty() ? null : sz);
        m.setColor(col.isEmpty() ? null : col);
        m.setType(StockMovement.TYPE_ADJUST);
        m.setDelta(-quantity);
        m.setReason(why);
        m.setCreatedBy(by);
        return repository.save(m);
    }
}
