package com.kosis.shop.service;

import com.kosis.shop.cart.Cart;
import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.OrderItem;
import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.Voucher;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Test logic đặt hàng / huỷ / hoàn kho của OrderService bằng repository giả (không cần database). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    VoucherService voucherService;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, productRepository, voucherService, PricingService.of(30_000, 500_000));
        // save() trả lại chính đối tượng; đơn mới được gán id = 7
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(7L);
            return o;
        });
    }

    // ---------------------------------------------------------------- helpers

    private Product product(long id, long price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("Áo " + id);
        p.setCode("P" + id);
        p.setPrice(price);
        p.setSizes("-");        // không chia size -> dùng 1 kho chung
        p.setStock(stock);
        when(productRepository.findByIdForUpdate(id)).thenReturn(Optional.of(p));
        return p;
    }

    private static CheckoutForm form(String payment) {
        CheckoutForm f = new CheckoutForm();
        f.setReceiverName(" An ");
        f.setPhone("0900000000");
        f.setAddress("1 Lê Lợi");
        f.setPaymentMethod(payment);
        return f;
    }

    private static Cart cartOf(long productId, int qty) {
        Cart c = new Cart();
        c.set(productId, "", "", qty);
        return c;
    }

    /** Đơn đã có sẵn trong DB, 1 dòng hàng */
    private Order existingOrder(Product p, int qty, String status) {
        Order o = new Order();
        o.setId(5L);
        o.setOrderCode("KS000005");
        o.setUsername("an");
        o.setStatus(status);
        o.setPaymentMethod(Order.PAYMENT_COD);
        OrderItem it = new OrderItem();
        it.setProductId(p.getId());
        it.setProductName(p.getName());
        it.setQuantity(qty);
        it.setUnitPrice(p.getPrice());
        o.addItem(it);
        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(o));
        return o;
    }

    private static String message(Runnable r) {
        return assertThrows(IllegalStateException.class, r::run).getMessage();
    }

    // ---------------------------------------------------------------- placeOrder

    @Test
    void placeOrderDeductsStockAndComputesTotal() {
        Product p = product(1, 200_000, 10);
        Order o = service.placeOrder("an", form(Order.PAYMENT_COD), cartOf(1, 2), null);

        assertEquals(8, p.getStock());
        assertEquals("KS000007", o.getOrderCode());
        assertEquals(Order.STATUS_PENDING, o.getStatus());
        assertEquals("An", o.getReceiverName());               // đã trim
        assertEquals(1, o.getItems().size());
        assertEquals(400_000, o.getItemsTotal());
        assertEquals(30_000, o.getShippingFee());              // 400.000 < 500.000
        assertEquals(430_000, o.getTotalAmount());
        verify(productRepository).saveAll(any());
    }

    @Test
    void placeOrderSnapshotsReturnWindow() {
        product(1, 200_000, 10);
        Order o = service.placeOrder("an", form(Order.PAYMENT_COD), cartOf(1, 1), null);

        assertEquals(7, o.getReturnWindowDays());   // không có SettingsService trong test -> mặc định 7
    }

    @Test
    void placeOrderGivesFreeShippingAtThreshold() {
        product(1, 200_000, 10);
        Order o = service.placeOrder("an", form(Order.PAYMENT_COD), cartOf(1, 3), null);
        assertEquals(0, o.getShippingFee());
        assertEquals(600_000, o.getTotalAmount());
    }

    @Test
    void bankOrderWaitsForPaymentReviewAndKeepsSlip() {
        product(1, 200_000, 10);
        Order o = service.placeOrder("an", form(Order.PAYMENT_BANK), cartOf(1, 1), "slip.jpg");
        assertEquals(Order.STATUS_PAYMENT_REVIEW, o.getStatus());
        assertEquals("slip.jpg", o.getSlipImage());
    }

    @Test
    void emptyCartIsRejected() {
        String msg = message(() -> service.placeOrder("an", form(Order.PAYMENT_COD), new Cart(), null));
        assertEquals("Giỏ hàng đang trống.", msg);
        verifyNoInteractions(productRepository);
    }

    @Test
    void notEnoughStockIsRejectedAndNothingIsSaved() {
        Product p = product(1, 200_000, 1);
        String msg = message(() -> service.placeOrder("an", form(Order.PAYMENT_COD), cartOf(1, 2), null));
        assertTrue(msg.contains("chỉ còn 1"), msg);
        assertEquals(1, p.getStock());
        verify(productRepository, never()).saveAll(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void soldOutProductIsReported() {
        product(1, 200_000, 0);
        String msg = message(() -> service.placeOrder("an", form(Order.PAYMENT_COD), cartOf(1, 1), null));
        assertTrue(msg.contains("vừa hết hàng"), msg);
    }

    @Test
    void productThatNoLongerExistsIsReported() {
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        String msg = message(() -> service.placeOrder("an", form(Order.PAYMENT_COD), cartOf(99, 1), null));
        assertTrue(msg.contains("không còn được bán"), msg);
    }

    @Test
    void sameProductInTwoLinesIsCheckedAgainstTheSharedStock() {
        Product p = product(1, 100_000, 3);
        Cart c = new Cart();
        c.set(1, "", "Đen", 2);
        c.set(1, "", "Trắng", 2);       // tổng 4 > kho 3
        String msg = message(() -> service.placeOrder("an", form(Order.PAYMENT_COD), c, null));
        assertTrue(msg.contains("chỉ còn"), msg);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void voucherReducesTotalAndIsRecordedOnOrder() {
        product(1, 200_000, 10);
        Voucher v = new Voucher();
        v.setCode("SALE10");
        v.setDiscountType(Voucher.PERCENT);
        v.setDiscountValue(10);
        v.setActive(true);
        when(voucherService.redeem(anyString(), anyLong())).thenReturn(v);

        CheckoutForm f = form(Order.PAYMENT_COD);
        f.setVoucherCode("sale10");
        Order o = service.placeOrder("an", f, cartOf(1, 2), null);

        assertEquals("SALE10", o.getVoucherCode());
        assertEquals(40_000, o.getDiscountAmount());
        assertEquals(30_000, o.getShippingFee());       // 360.000 < 500.000
        assertEquals(390_000, o.getTotalAmount());
    }

    // ---------------------------------------------------------------- cancelByCustomer

    @Test
    void customerCanCancelPendingOrderAndStockComesBack() {
        Product p = product(1, 200_000, 8);
        Order o = existingOrder(p, 2, Order.STATUS_PENDING);
        o.setVoucherCode("SALE10");

        Order result = service.cancelByCustomer(5L, "an");

        assertEquals(10, p.getStock());
        assertTrue(result.isRejected());
        assertTrue(result.isCancelledByCustomer());
        assertEquals("Đã hủy", result.getStatusLabel());
        assertTrue(result.isStockRestored());
        verify(voucherService).release("SALE10");
    }

    @Test
    void cancellingTwiceDoesNotRestoreStockTwice() {
        Product p = product(1, 200_000, 8);
        existingOrder(p, 2, Order.STATUS_PENDING);

        service.cancelByCustomer(5L, "an");
        String msg = message(() -> service.cancelByCustomer(5L, "an"));

        assertTrue(msg.contains("đã được huỷ hoặc bị từ chối"), msg);
        assertEquals(10, p.getStock());
    }

    @Test
    void customerCannotCancelSomeoneElsesOrder() {
        Product p = product(1, 200_000, 8);
        existingOrder(p, 2, Order.STATUS_PENDING);
        String msg = message(() -> service.cancelByCustomer(5L, "binh"));
        assertEquals("Không tìm thấy đơn hàng.", msg);
        assertEquals(8, p.getStock());
    }

    @Test
    void customerCannotCancelConfirmedOrder() {
        Product p = product(1, 200_000, 8);
        existingOrder(p, 2, Order.STATUS_CONFIRMED);
        String msg = message(() -> service.cancelByCustomer(5L, "an"));
        assertTrue(msg.contains("đã được cửa hàng xác nhận"), msg);
        assertEquals(8, p.getStock());
    }

    // ---------------------------------------------------------------- confirm / reject

    @Test
    void staffConfirmsCodOrder() {
        Product p = product(1, 200_000, 8);
        existingOrder(p, 1, Order.STATUS_PENDING);
        Order o = service.confirm(5L, "staff1");
        assertTrue(o.isConfirmed());
        assertEquals("staff1", o.getReviewedBy());
    }

    @Test
    void bankOrderWithoutSlipCannotBeConfirmed() {
        Product p = product(1, 200_000, 8);
        Order o = existingOrder(p, 1, Order.STATUS_PAYMENT_REVIEW);
        o.setPaymentMethod(Order.PAYMENT_BANK);
        String msg = message(() -> service.confirm(5L, "staff1"));
        assertTrue(msg.contains("chưa có ảnh bill"), msg);
    }

    @Test
    void alreadyHandledOrderCannotBeConfirmedAgain() {
        Product p = product(1, 200_000, 8);
        existingOrder(p, 1, Order.STATUS_CONFIRMED);
        String msg = message(() -> service.confirm(5L, "staff1"));
        assertTrue(msg.contains("đã được xử lý"), msg);
    }

    @Test
    void rejectRequiresAReason() {
        Product p = product(1, 200_000, 8);
        existingOrder(p, 1, Order.STATUS_PENDING);
        assertTrue(message(() -> service.reject(5L, "staff1", "  ")).contains("lý do"));
        assertTrue(message(() -> service.reject(5L, "staff1", "x".repeat(301))).contains("quá dài"));
    }

    @Test
    void rejectRestoresStockOnce() {
        Product p = product(1, 200_000, 8);
        existingOrder(p, 3, Order.STATUS_PENDING);

        Order o = service.reject(5L, "staff1", "Sai số tiền");

        assertEquals(11, p.getStock());
        assertTrue(o.isRejected());
        assertFalse(o.isCancelledByCustomer());
        assertEquals("Bị từ chối", o.getStatusLabel());
        assertEquals("Sai số tiền", o.getRejectReason());
        assertTrue(message(() -> service.reject(5L, "staff1", "Lại từ chối")).contains("đã được xử lý"));
        assertEquals(11, p.getStock());
    }

    // ---------------------------------------------------------------- giao hàng không thành công

    private Order shippingOrder(Product p, int qty) {
        Order o = existingOrder(p, qty, Order.STATUS_CONFIRMED);
        o.setShippingStatus(Order.SHIP_SHIPPING);
        return o;
    }

    @Test
    void failedDeliveryGivesStockBackOnce() {
        Product p = product(1, 200_000, 5);
        Order o = shippingOrder(p, 2);

        service.markFailed(5L, false, "Khách không nghe máy", "staff1");

        assertEquals(7, p.getStock());
        assertEquals(Order.SHIP_RETURNED, o.getShippingStatus());
        assertTrue(o.isStockRestored());
        // lần ghi nhận thứ 2 bị chặn (đơn không còn ở trạng thái vận chuyển) -> kho không bị cộng thêm
        assertThrows(IllegalStateException.class, () -> service.markFailed(5L, false, "Lại thử", "staff1"));
        assertEquals(7, p.getStock());
    }

    @Test
    void failedDeliveryNeedsAReason() {
        Product p = product(1, 200_000, 5);
        shippingOrder(p, 2);
        assertTrue(message(() -> service.markFailed(5L, true, " ", "staff1")).contains("lý do"));
        assertEquals(5, p.getStock());
    }

    @Test
    void reshippingAReturnedOrderDeductsStockAgain() {
        Product p = product(1, 200_000, 5);
        Order o = shippingOrder(p, 2);
        service.markFailed(5L, false, "Khách vắng nhà", "staff1");
        assertEquals(7, p.getStock());

        service.ship(5L, "SHOP", "", "", "staff1");

        assertEquals(5, p.getStock());
        assertFalse(o.isStockRestored());
        assertEquals(Order.SHIP_SHIPPING, o.getShippingStatus());
    }

    @Test
    void reshippingFailsWhenStockWasSoldInTheMeantime() {
        Product p = product(1, 200_000, 5);
        Order o = shippingOrder(p, 2);
        service.markFailed(5L, false, "Khách vắng nhà", "staff1");
        p.setStock(1);      // hàng đã bán cho người khác

        String msg = message(() -> service.ship(5L, "SHOP", "", "", "staff1"));

        assertTrue(msg.contains("Không đủ hàng"), msg);
        assertEquals(1, p.getStock());
        assertTrue(o.isStockRestored());
    }

    // ---------------------------------------------------------------- đối soát chuyển khoản

    private Order bankOrder(long id, String user, String code, long total) {
        Order o = new Order();
        o.setId(id);
        o.setUsername(user);
        o.setOrderCode(code);
        o.setPaymentMethod(Order.PAYMENT_BANK);
        o.setStatus(Order.STATUS_PAYMENT_REVIEW);
        o.setSlipImage("slip.jpg");
        o.setTotalAmount(total);
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void transferMatchingOrderCodeAndAmountIsAutoConfirmed() {
        Order o = bankOrder(7, "an", "KS000007", 430_000);
        when(orderRepository.findByStatusAndPaymentMethod(Order.STATUS_PAYMENT_REVIEW, Order.PAYMENT_BANK))
                .thenReturn(List.of(o));

        Order confirmed = service.autoConfirmByTransfer(430_000, "AN chuyen tien ks 000007");

        assertNotNull(confirmed);
        assertTrue(confirmed.isConfirmed());
        assertEquals(Order.CONFIRMED_BY_BANK, confirmed.getReviewedBy());
    }

    @Test
    void transferWithWrongAmountIsNotConfirmed() {
        Order o = bankOrder(7, "an", "KS000007", 430_000);
        when(orderRepository.findByStatusAndPaymentMethod(Order.STATUS_PAYMENT_REVIEW, Order.PAYMENT_BANK))
                .thenReturn(List.of(o));
        assertNull(service.autoConfirmByTransfer(400_000, "KS000007"));
        assertEquals(Order.STATUS_PAYMENT_REVIEW, o.getStatus());
    }

    @Test
    void ambiguousTransferIsLeftForStaff() {
        // 2 đơn cùng số tiền, cùng tài khoản, nội dung chỉ có tên tài khoản -> không biết là đơn nào
        Order a = bankOrder(7, "an", "KS000007", 430_000);
        Order b = bankOrder(8, "an", "KS000008", 430_000);
        when(orderRepository.findByStatusAndPaymentMethod(Order.STATUS_PAYMENT_REVIEW, Order.PAYMENT_BANK))
                .thenReturn(List.of(a, b));
        assertNull(service.autoConfirmByTransfer(430_000, "KS an"));
        assertEquals(Order.STATUS_PAYMENT_REVIEW, a.getStatus());
        assertEquals(Order.STATUS_PAYMENT_REVIEW, b.getStatus());
    }

    @Test
    void invalidTransferInputIsIgnored() {
        assertNull(service.autoConfirmByTransfer(0, "KS000007"));
        assertNull(service.autoConfirmByTransfer(430_000, "  --  "));
        verify(orderRepository, never()).findByStatusAndPaymentMethod(anyString(), anyString());
    }

    // ---------------------------------------------------------------- COD đặt cọc

    private OrderService depositService(int percent) {
        return new OrderService(orderRepository, productRepository, voucherService, PricingService.of(30_000, 500_000, percent));
    }

    @Test
    void codOrderWithDepositWaitsForDepositReviewAndKeepsSlip() {
        product(1, 200_000, 10);
        Order o = depositService(30).placeOrder("an", form(Order.PAYMENT_COD), cartOf(1, 2), "coc.jpg");   // 400.000 + 30.000 ship = 430.000

        assertEquals(Order.STATUS_PAYMENT_REVIEW, o.getStatus());
        assertEquals("coc.jpg", o.getSlipImage());
        assertEquals(129_000, o.getDepositAmount());           // 30% x 430.000
        assertEquals(301_000, o.getCodDue());                  // shipper chỉ thu phần còn lại
        assertEquals(129_000, o.getPayNow());
        assertTrue(o.isCodDeposit());
        assertTrue(o.isHasTransfer());
        assertFalse(o.isBank());
    }

    @Test
    void codOrderWithDepositWithoutSlipIsRejected() {
        product(1, 200_000, 10);
        String msg = message(() -> depositService(30).placeOrder("an", form(Order.PAYMENT_COD), cartOf(1, 1), null));
        assertTrue(msg.contains("đặt cọc"));
    }

    @Test
    void codOrderWithoutDepositSettingStaysPending() {
        product(1, 200_000, 10);
        Order o = depositService(0).placeOrder("an", form(Order.PAYMENT_COD), cartOf(1, 1), null);
        assertEquals(Order.STATUS_PENDING, o.getStatus());
        assertEquals(0, o.getDepositAmount());
        assertEquals(o.getTotalAmount(), o.getCodDue());
        assertFalse(o.isHasTransfer());
    }

    @Test
    void bankOrderIgnoresDepositSetting() {
        product(1, 200_000, 10);
        Order o = depositService(30).placeOrder("an", form(Order.PAYMENT_BANK), cartOf(1, 1), "slip.jpg");
        assertEquals(0, o.getDepositAmount());
        assertEquals(0, o.getCodDue());
        assertEquals(o.getTotalAmount(), o.getPayNow());
    }

    private Order codDepositOrder(long id, String code, long total, long deposit) {
        Order o = new Order();
        o.setId(id);
        o.setUsername("an");
        o.setOrderCode(code);
        o.setPaymentMethod(Order.PAYMENT_COD);
        o.setStatus(Order.STATUS_PAYMENT_REVIEW);
        o.setSlipImage("coc.jpg");
        o.setTotalAmount(total);
        o.setDepositAmount(deposit);
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void depositTransferMatchingCodOrderIsAutoConfirmed() {
        Order o = codDepositOrder(7, "KS000007", 430_000, 129_000);
        when(orderRepository.findByStatusAndPaymentMethod(Order.STATUS_PAYMENT_REVIEW, Order.PAYMENT_COD))
                .thenReturn(List.of(o));

        Order confirmed = service.autoConfirmByTransfer(129_000, "coc KS000007");

        assertNotNull(confirmed);
        assertTrue(confirmed.isConfirmed());
    }

    @Test
    void depositTransferWithWrongAmountIsNotConfirmed() {
        Order o = codDepositOrder(7, "KS000007", 430_000, 129_000);
        when(orderRepository.findByStatusAndPaymentMethod(Order.STATUS_PAYMENT_REVIEW, Order.PAYMENT_COD))
                .thenReturn(List.of(o));

        assertNull(service.autoConfirmByTransfer(100_000, "KS000007"));       // thiếu tiền cọc
        assertNull(service.autoConfirmByTransfer(430_000, "KS000007"));       // chuyển cả đơn: để nhân viên xử lý tay
        assertEquals(Order.STATUS_PAYMENT_REVIEW, o.getStatus());
    }

    // ---------------------------------------------------------------- hàm tiện ích

    @Test
    void compactKeepsOnlyUppercaseLettersAndDigits() {
        assertEquals("KS000012ABC", OrderService.compact("ks-000012 abc"));
        assertEquals("", OrderService.compact(null));
    }

    @Test
    void transferMemoIsBuiltFromUsername() {
        assertEquals("KSNGUYENVANA", OrderService.transferMemoOf("nguyen.van_a"));
        assertEquals("KS", OrderService.transferMemoOf(null));
    }

    @Test
    void transferMemoIsLimitedToTwentyCharacters() {
        assertEquals("KSABCDEFGHIJKLMNOPQRST", OrderService.transferMemoOf("abcdefghijklmnopqrstuvwxyz"));
    }
}
