package com.kosis.shop.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Tính tiền đơn hàng: tạm tính - giảm giá (voucher) + phí vận chuyển.
 * Phí vận chuyển cố định (shop.shipping.fee), MIỄN PHÍ khi tiền hàng sau giảm giá từ shop.shipping.free-threshold trở lên
 * (đặt free-threshold=0 để KHÔNG BAO GIỜ miễn phí, đặt fee=0 để miễn phí mọi đơn).
 */
@Service
public class PricingService {

    @Value("${shop.shipping.fee:30000}")
    private long shippingFee = 30000;

    @Value("${shop.shipping.free-threshold:500000}")
    private long freeThreshold = 500000;

    /** % tiền cọc khách phải chuyển khoản trước khi chọn COD (0 = tắt, COD không cần cọc) */
    @Value("${shop.cod.deposit-percent:30}")
    private int codDepositPercent = 30;

    /** Cài đặt do ADMIN chỉnh trên /dashboard/cai-dat (ghi đè giá trị trong properties). Không có (test) thì dùng các giá trị trên. */
    @Autowired(required = false)
    private SettingsService settings;

    public PricingService() {
    }

    /** Dùng trong test / nơi không có Spring */
    public static PricingService of(long fee, long freeThreshold) {
        return of(fee, freeThreshold, 0);
    }

    /** Dùng trong test / nơi không có Spring - có đặt cọc COD [depositPercent]% (0 = tắt) */
    public static PricingService of(long fee, long freeThreshold, int depositPercent) {
        PricingService p = new PricingService();
        p.shippingFee = fee;
        p.freeThreshold = freeThreshold;
        p.codDepositPercent = depositPercent;
        return p;
    }

    public long getShippingFee() { return Math.max(0, settings != null ? settings.getLong(SettingsService.SHIPPING_FEE) : shippingFee); }

    public long getFreeThreshold() { return Math.max(0, settings != null ? settings.getLong(SettingsService.SHIPPING_FREE_THRESHOLD) : freeThreshold); }

    /** % đặt cọc cho đơn COD (0-100). 0 = không bắt cọc */
    public int getCodDepositPercent() {
        int p = settings != null ? settings.getInt(SettingsService.COD_DEPOSIT_PERCENT) : codDepositPercent;
        return Math.max(0, Math.min(100, p));
    }

    public boolean isCodDepositEnabled() { return getCodDepositPercent() > 0; }

    /**
     * Tiền cọc cho đơn COD có tổng [total]: total x % rồi LÀM TRÒN LÊN tới 1.000₫ (số tiền chuyển khoản gọn, dễ đối soát),
     * không vượt quá tổng đơn. 0 nếu tắt đặt cọc.
     */
    public long depositFor(long total) {
        int pct = getCodDepositPercent();
        if (pct <= 0 || total <= 0) return 0;
        long raw = (total * pct + 99) / 100;                 // làm tròn lên tới đồng
        long rounded = ((raw + 999) / 1000) * 1000;          // làm tròn lên tới nghìn đồng
        return Math.min(rounded, total);
    }

    /** Phí vận chuyển cho đơn có tiền hàng (sau giảm giá) = [amount] */
    public long shippingFor(long amount) {
        if (amount <= 0) return 0;
        if (getFreeThreshold() > 0 && amount >= getFreeThreshold()) return 0;
        return getShippingFee();
    }

    /** Còn thiếu bao nhiêu để được miễn phí vận chuyển (0 nếu đã được miễn phí hoặc không có mốc miễn phí) */
    public long remainingForFreeShipping(long amount) {
        if (getFreeThreshold() <= 0 || getShippingFee() <= 0) return 0;
        return Math.max(0, getFreeThreshold() - Math.max(0, amount));
    }

    public Quote quote(long subtotal, long discount, String voucherCode) {
        long d = Math.max(0, Math.min(discount, subtotal));
        long ship = shippingFor(subtotal - d);
        return new Quote(subtotal, d, ship, subtotal - d + ship, voucherCode);
    }

    /** Kết quả tính tiền (lớp thường có getter để Thymeleaf đọc được) */
    public static class Quote {
        private final long subtotal;
        private final long discount;
        private final long shipping;
        private final long total;
        private final String voucherCode;

        public Quote(long subtotal, long discount, long shipping, long total, String voucherCode) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.shipping = shipping;
            this.total = total;
            this.voucherCode = voucherCode;
        }

        public long getSubtotal() { return subtotal; }
        public long getDiscount() { return discount; }
        public long getShipping() { return shipping; }
        public long getTotal() { return total; }
        public String getVoucherCode() { return voucherCode; }
    }
}
