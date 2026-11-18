package com.kosis.shop.entity;

/**
 * Các đơn vị vận chuyển cửa hàng có thể chọn khi giao hàng.
 * trackUrl: trang tra cứu hành trình của hãng; appendCode = true thì mã vận đơn được gắn thẳng vào cuối đường dẫn.
 * Hãng nào đổi đường dẫn tra cứu thì chỉ cần sửa ở file này.
 */
public enum Carrier {
    GHN("Giao Hàng Nhanh (GHN)", "https://donhang.ghn.vn/?order_code=", true, true),
    GHTK("Giao Hàng Tiết Kiệm (GHTK)", "https://i.ghtk.vn/", true, true),
    VIETTEL_POST("Viettel Post", "https://viettelpost.com.vn/tra-cuu-hanh-trinh-don/", false, true),
    JT("J&T Express", "https://jtexpress.vn/vi/tracking", false, true),
    VNPOST("VNPost (Bưu điện)", "https://www.vnpost.vn/vi-vn/tra-cuu-hanh-trinh/buu-pham", false, true),
    NINJA_VAN("Ninja Van", "https://www.ninjavan.co/vi-vn/tracking", false, true),
    SPX("SPX Express", "https://spx.vn/track", false, true),
    AHAMOVE("Ahamove (giao nhanh nội thành)", null, false, false),
    SHOP("Cửa hàng tự giao", null, false, false);

    private final String label;
    private final String trackUrl;
    private final boolean appendCode;
    private final boolean needsCode;

    Carrier(String label, String trackUrl, boolean appendCode, boolean needsCode) {
        this.label = label;
        this.trackUrl = trackUrl;
        this.appendCode = appendCode;
        this.needsCode = needsCode;
    }

    public String getLabel() { return label; }

    /** Có bắt buộc nhập mã vận đơn không (tự giao / giao nhanh thì không) */
    public boolean isNeedsCode() { return needsCode; }

    /** Đường dẫn tra cứu hành trình; null nếu hãng không có trang tra cứu */
    public String urlFor(String code) {
        if (trackUrl == null) return null;
        if (appendCode && code != null && !code.isBlank()) return trackUrl + code;
        return trackUrl;
    }

    public static Carrier fromCode(String code) {
        if (code == null) return null;
        for (Carrier c : values()) {
            if (c.name().equalsIgnoreCase(code.trim())) return c;
        }
        return null;
    }
}
