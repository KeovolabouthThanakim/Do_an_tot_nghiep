package com.kosis.shop.service;

import com.kosis.shop.config.I18n;
import com.kosis.shop.entity.Voucher;
import com.kosis.shop.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Kiểm tra và tính lượt dùng mã giảm giá. Lỗi nghiệp vụ ném IllegalStateException với nội dung tiếng Việt. */
@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;

    public VoucherService(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    /** Một mã đang dùng được, kèm thông tin riêng cho đơn hiện tại (để khách chọn ở trang thanh toán). */
    public static class Option {
        private final Voucher voucher;
        private final long discount;
        private final long missing;

        Option(Voucher voucher, long discount, long missing) {
            this.voucher = voucher;
            this.discount = discount;
            this.missing = missing;
        }

        public Voucher getVoucher() { return voucher; }

        /** Số tiền sẽ được giảm nếu áp dụng mã này (0 nếu đơn chưa đủ điều kiện) */
        public long getDiscount() { return discount; }

        /** Đơn hiện tại đã đủ điều kiện dùng mã? */
        public boolean isEligible() { return missing == 0 && discount > 0; }

        /** Cần mua thêm bao nhiêu (₫) để đủ điều kiện đơn tối thiểu (0 nếu đã đủ) */
        public long getMissing() { return missing; }
    }

    /**
     * Các mã khách có thể thấy ở trang thanh toán: đang bật, chưa hết hạn, còn lượt.
     * Mã đủ điều kiện xếp trước (giảm nhiều nhất lên đầu), mã chưa đủ đơn tối thiểu xếp sau (gần đủ nhất trước).
     */
    public List<Option> available(long subtotal) {
        List<Option> out = new ArrayList<>();
        for (Voucher v : voucherRepository.findAllByOrderByCreatedAtDesc()) {
            if (!v.isUsable()) continue;
            long missing = v.getMinOrder() > 0 ? Math.max(0, v.getMinOrder() - subtotal) : 0;
            long discount = missing == 0 ? v.discountFor(subtotal) : 0;
            if (missing == 0 && discount <= 0) continue;   // mã không giảm được gì cho đơn này: không hiện
            out.add(new Option(v, discount, missing));
        }
        out.sort(Comparator.comparing((Option o) -> !o.isEligible())
                .thenComparing(Comparator.comparingLong(Option::getDiscount).reversed())
                .thenComparingLong(Option::getMissing));
        return out;
    }

    /** Mã tốt nhất (giảm nhiều nhất) mà đơn hiện tại dùng được ngay; null nếu không có. */
    public Voucher bestFor(long subtotal) {
        return available(subtotal).stream().filter(Option::isEligible).findFirst().map(Option::getVoucher).orElse(null);
    }

    /** Kiểm tra mã có dùng được cho đơn có tạm tính [subtotal] không (không cộng lượt dùng) */
    public Voucher validate(String code, long subtotal) {
        String c = Voucher.normalize(code);
        if (c.isEmpty()) throw new IllegalStateException(I18n.t("voucher.enter.code", "Vui lòng nhập mã giảm giá."));
        Voucher v = voucherRepository.findByCode(c)
                .orElseThrow(() -> new IllegalStateException(I18n.t("voucher.not.exist", "Mã giảm giá \"{0}\" không tồn tại.", c)));
        check(v, subtotal);
        return v;
    }

    /** Kiểm tra lại dưới khoá rồi CỘNG 1 lượt dùng - gọi trong transaction đặt hàng */
    @Transactional
    public Voucher redeem(String code, long subtotal) {
        String c = Voucher.normalize(code);
        Voucher v = voucherRepository.findByCodeForUpdate(c)
                .orElseThrow(() -> new IllegalStateException(I18n.t("voucher.not.exist", "Mã giảm giá \"{0}\" không tồn tại.", c)));
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
        if (!v.isActive()) throw new IllegalStateException(I18n.t("voucher.disabled", "Mã giảm giá này đã bị tắt."));
        if (v.isExpired()) throw new IllegalStateException(I18n.t("voucher.expired", "Mã giảm giá này đã hết hạn."));
        if (v.isExhausted()) throw new IllegalStateException(I18n.t("voucher.exhausted", "Mã giảm giá này đã hết lượt sử dụng."));
        if (v.getMinOrder() > 0 && subtotal < v.getMinOrder()) {
            throw new IllegalStateException(I18n.t("voucher.min.order", "Mã này áp dụng cho đơn từ {0} (tạm tính hiện tại {1}).", money(v.getMinOrder()), money(subtotal)));
        }
        if (v.discountFor(subtotal) <= 0) throw new IllegalStateException(I18n.t("voucher.not.applicable", "Mã giảm giá này không áp dụng được cho đơn hiện tại."));
    }

    static String money(long v) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator(I18n.isEnglish() ? ',' : '.');
        return new DecimalFormat("#,##0", sym).format(v) + " ₫";
    }
}
