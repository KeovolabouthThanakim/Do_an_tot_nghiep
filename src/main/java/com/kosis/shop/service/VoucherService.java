package com.kosis.shop.service;

import com.kosis.shop.entity.Voucher;
import com.kosis.shop.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Kiểm tra và tính lượt dùng mã giảm giá. Lỗi nghiệp vụ ném IllegalStateException với nội dung tiếng Việt. */
@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;

    public VoucherService(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    /** Kiểm tra mã có dùng được cho đơn có tạm tính [subtotal] không (không cộng lượt dùng) */
    public Voucher validate(String code, long subtotal) {
        String c = Voucher.normalize(code);
        if (c.isEmpty()) throw new IllegalStateException("Vui lòng nhập mã giảm giá.");
        Voucher v = voucherRepository.findByCode(c)
                .orElseThrow(() -> new IllegalStateException("Mã giảm giá \"" + c + "\" không tồn tại."));
        check(v, subtotal);
        return v;
    }

    /** Kiểm tra lại dưới khoá rồi CỘNG 1 lượt dùng - gọi trong transaction đặt hàng */
    @Transactional
    public Voucher redeem(String code, long subtotal) {
        String c = Voucher.normalize(code);
        Voucher v = voucherRepository.findByCodeForUpdate(c)
                .orElseThrow(() -> new IllegalStateException("Mã giảm giá \"" + c + "\" không tồn tại."));
        check(v, subtotal);
        v.setUsedCount(v.getUsedCount() + 1);
        return voucherRepository.save(v);
    }

    /** Trả lại 1 lượt dùng khi đơn bị từ chối / huỷ */
    @Transactional
    public void release(String code) {
        if (code == null || code.isBlank()) return;
        voucherRepository.findByCodeForUpdate(Voucher.normalize(code)).ifPresent(v -> {
            if (v.getUsedCount() > 0) {
                v.setUsedCount(v.getUsedCount() - 1);
                voucherRepository.save(v);
            }
        });
    }

    private static void check(Voucher v, long subtotal) {
        if (!v.isActive()) throw new IllegalStateException("Mã giảm giá này đã bị tắt.");
        if (v.isExpired()) throw new IllegalStateException("Mã giảm giá này đã hết hạn.");
        if (v.isExhausted()) throw new IllegalStateException("Mã giảm giá này đã hết lượt sử dụng.");
        if (v.getMinOrder() > 0 && subtotal < v.getMinOrder()) {
            throw new IllegalStateException("Mã này áp dụng cho đơn từ " + money(v.getMinOrder()) + " (tạm tính hiện tại " + money(subtotal) + ").");
        }
        if (v.discountFor(subtotal) <= 0) throw new IllegalStateException("Mã giảm giá này không áp dụng được cho đơn hiện tại.");
    }

    static String money(long v) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", sym).format(v) + " ₫";
    }
}
