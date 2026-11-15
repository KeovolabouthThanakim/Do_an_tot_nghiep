package com.kosis.shop.service;

/** Dữ liệu khách nhập ở trang thanh toán */
public class CheckoutForm {

    private String receiverName;
    private String phone;
    private String address;
    private String note;
    private String paymentMethod = "COD";
    /** Mã giảm giá khách nhập (không bắt buộc) */
    private String voucherCode;

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
}
