package com.kosis.shop.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;

/**
 * Thông tin tài khoản ngân hàng nhận tiền của cửa hàng - đọc từ application.properties (tiền tố shop.bank).
 * Mã QR được tạo bằng VietQR (img.vietqr.io) đã điền sẵn số tài khoản, số tiền và nội dung chuyển khoản,
 * hoặc dùng ảnh QR tự đặt (shop.bank.qr-image) nếu cửa hàng muốn dùng QR riêng.
 */
@Component
@ConfigurationProperties(prefix = "shop.bank")
public class BankProperties {

    private static final Logger log = LoggerFactory.getLogger(BankProperties.class);

    /** Mã ngân hàng theo VietQR: VCB, MB, TCB, ACB, BIDV, VPB, TPB, ICB (VietinBank), STB, ... */
    private String bankId = "VCB";
    /** Mã BIN 6 số của ngân hàng (NAPAS). Để trống = tự tra theo bankId ở bảng BIN bên dưới */
    private String bin = "";
    private String bankName = "Vietcombank";
    private String accountNumber = "0000000000";
    private String accountName = "TEN CHU TAI KHOAN";
    /** Ảnh QR tự đặt, VD: /images/bank-qr.png (để trống = tự tạo QR VietQR theo số tiền từng đơn) */
    private String qrImage = "";

    /** Mã BIN (NAPAS) của các ngân hàng phổ biến - dùng để tự tạo mã QR VietQR ngay trong ứng dụng */
    private static final Map<String, String> BINS = Map.ofEntries(
            Map.entry("VCB", "970436"), Map.entry("ICB", "970415"), Map.entry("CTG", "970415"),
            Map.entry("BIDV", "970418"), Map.entry("VBA", "970405"), Map.entry("AGRIBANK", "970405"),
            Map.entry("MB", "970422"), Map.entry("TCB", "970407"), Map.entry("ACB", "970416"),
            Map.entry("VPB", "970432"), Map.entry("TPB", "970423"), Map.entry("STB", "970403"),
            Map.entry("HDB", "970437"), Map.entry("VIB", "970441"), Map.entry("SHB", "970443"),
            Map.entry("OCB", "970448"), Map.entry("MSB", "970426"), Map.entry("SCB", "970429"),
            Map.entry("EIB", "970431"), Map.entry("LPB", "970449"), Map.entry("SEAB", "970440"));

    /** Cảnh báo khi chưa cấu hình tài khoản nhận tiền (thông tin này nằm ở biến môi trường, không nằm trong Git) */
    @PostConstruct
    void warnIfNotConfigured() {
        if (accountNumber == null || accountNumber.isBlank() || accountName == null || accountName.isBlank()) {
            log.warn("Chưa cấu hình tài khoản ngân hàng nhận tiền: hãy đặt KOSIS_BANK_ACCOUNT_NUMBER và "
                    + "KOSIS_BANK_ACCOUNT_NAME (hoặc shop.bank.* trong local.properties). Trang thanh toán sẽ không hiện mã QR.");
        }
    }

    /** BIN đang dùng: ưu tiên shop.bank.bin, không có thì tra theo bankId; rỗng nếu không biết */
    public String resolvedBin() {
        if (bin != null && bin.trim().matches("\\d{6}")) return bin.trim();
        return BINS.getOrDefault(bankId == null ? "" : bankId.trim().toUpperCase(), "");
    }

    /**
     * Chuỗi dữ liệu mã QR chuẩn VietQR (NAPAS 247 / EMVCo) - đã điền sẵn ngân hàng, số tài khoản,
     * số tiền và nội dung chuyển khoản. Trang thanh toán vẽ mã QR từ chuỗi này ngay trên trình duyệt,
     * KHÔNG phụ thuộc website bên ngoài. Trả về chuỗi rỗng nếu chưa cấu hình đúng BIN / số tài khoản.
     */
    public String qrPayload(long amount, String content) {
        String b = resolvedBin();
        String acc = accountNumber == null ? "" : accountNumber.trim();
        if (b.isEmpty() || acc.isEmpty()) return "";

        String beneficiary = tlv("00", b) + tlv("01", acc);
        String merchant = tlv("00", "A000000727") + tlv("01", beneficiary) + tlv("02", "QRIBFTTA");

        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));                       // phiên bản
        sb.append(tlv("01", amount > 0 ? "12" : "11"));   // 12 = QR động (có số tiền)
        sb.append(tlv("38", merchant));
        sb.append(tlv("53", "704"));                      // VND
        if (amount > 0) sb.append(tlv("54", String.valueOf(amount)));
        sb.append(tlv("58", "VN"));
        String memo = asciiMemo(content);
        if (!memo.isEmpty()) sb.append(tlv("62", tlv("08", memo)));
        sb.append("6304");
        sb.append(String.format("%04X", crc16(sb.toString().getBytes(StandardCharsets.UTF_8))));
        return sb.toString();
    }

    private static String tlv(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    /** Nội dung chuyển khoản: bỏ dấu, chỉ giữ chữ/số/khoảng trắng, tối đa 25 ký tự */
    private static String asciiMemo(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .replaceAll("[^A-Za-z0-9 ]", "").trim();
        return n.length() > 25 ? n.substring(0, 25).trim() : n;
    }

    /** CRC-16/CCITT-FALSE (đa thức 0x1021, khởi tạo 0xFFFF) theo chuẩn EMVCo */
    private static int crc16(byte[] data) {
        int crc = 0xFFFF;
        for (byte x : data) {
            crc ^= (x & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return crc;
    }

    /** Địa chỉ ảnh QR (dự phòng) cho đơn có số tiền `amount` và nội dung chuyển khoản `content` */
    public String qrUrl(long amount, String content) {
        if (qrImage != null && !qrImage.isBlank()) {
            return qrImage;
        }
        return "https://img.vietqr.io/image/" + enc(bankId) + "-" + enc(accountNumber) + "-compact2.png"
                + "?amount=" + amount
                + "&addInfo=" + enc(content)
                + "&accountName=" + enc(accountName);
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public String getBankId() { return bankId; }
    public void setBankId(String bankId) { this.bankId = bankId; }

    public String getBin() { return bin; }
    public void setBin(String bin) { this.bin = bin; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getQrImage() { return qrImage; }
    public void setQrImage(String qrImage) { this.qrImage = qrImage; }
}
