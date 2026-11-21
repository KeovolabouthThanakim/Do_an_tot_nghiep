package com.kosis.shop.service;

import com.kosis.shop.config.I18n;
import com.kosis.shop.cart.Cart;
import com.kosis.shop.entity.Carrier;
import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.OrderItem;
import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.Voucher;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.entity.StockMovement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final VoucherService voucherService;
    private final PricingService pricingService;

    /** Sổ kho (không bắt buộc: bỏ trống thì chỉ không ghi lịch sử). */
    @Autowired(required = false)
    private StockMovementService movements;

    /** Sau bao nhiêu ngày kể từ lúc vận chuyển báo đã tới nơi mà khách không phản hồi thì tự hoàn tất đơn (khuyên dùng 3-7) */
    @Value("${kosis.shipping.auto-complete-days:5}")
    private int autoCompleteDays;

    /** Cài đặt do ADMIN chỉnh trên /dashboard/cai-dat (ghi đè giá trị trong properties). Không có (test) thì dùng autoCompleteDays. */
    @Autowired(required = false)
    private SettingsService settings;

    /** Thời hạn đổi trả hiện hành, chụp vào từng đơn lúc đặt hàng (ReturnService không dùng được ở đây vì nó phụ thuộc OrderService) */
    @Value("${kosis.returns.window-days:7}")
    private int returnWindowDays = 7;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        VoucherService voucherService, PricingService pricingService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.voucherService = voucherService;
        this.pricingService = pricingService;
    }

    /**
     * Tạo đơn từ giỏ hàng: kiểm tra tồn kho lần cuối, trừ kho, chụp lại giá, lưu đơn.
     * Toàn bộ nằm trong 1 transaction - lỗi ở bất kỳ sản phẩm nào thì không trừ kho sản phẩm nào.
     * @throws IllegalStateException với nội dung tiếng Việt để hiện cho khách
     */
    @Transactional
    public Order placeOrder(String username, CheckoutForm form, Cart cart, String slipFile) {
        if (cart.isEmpty()) {
            throw new IllegalStateException(I18n.t("order.cart.empty", "Giỏ hàng đang trống."));
        }

        Order order = new Order();
        order.setUsername(username);
        order.setReceiverName(form.getReceiverName().trim());
        order.setPhone(form.getPhone().trim());
        order.setAddress(form.getAddress().trim());
        String note = form.getNote() == null ? "" : form.getNote().trim();
        order.setNote(note.isEmpty() ? null : note);
        order.setPaymentMethod(form.getPaymentMethod());
        // Trạng thái + ảnh bill được quyết định SAU khi tính xong tổng tiền (tiền cọc COD tính theo tổng đơn) - xem bên dưới.

        // Kiểm tra + trừ kho theo từng dòng (sản phẩm + size).
        // Sản phẩm đã chia tồn kho theo size: trừ đúng kho của size đó. Chưa chia: các size dùng chung 1 kho.
        // KHOÁ dòng sản phẩm (SELECT ... FOR UPDATE) theo thứ tự id tăng dần: 2 khách đặt cùng lúc món cuối cùng
        // sẽ phải xếp hàng, người sau thấy kho đã hết thay vì cùng trừ kho -> không bán lố / kho không âm.
        // Khoá theo thứ tự cố định để 2 đơn nhiều sản phẩm không khoá chéo nhau gây deadlock.
        Map<Long, Product> products = new LinkedHashMap<>();
        TreeSet<Long> productIds = new TreeSet<>();
        for (String key : cart.getItems().keySet()) {
            productIds.add(Cart.productIdOf(key));
        }
        for (Long pid : productIds) {
            Product locked = productRepository.findByIdForUpdate(pid)
                    .orElseThrow(() -> new IllegalStateException(I18n.t("order.cart.unavailable", "Có sản phẩm trong giỏ không còn được bán. Vui lòng kiểm tra lại giỏ hàng.")));
            if (locked.isPendingImport()) {
                throw new IllegalStateException(I18n.t("order.cart.unavailable", "Có sản phẩm trong giỏ không còn được bán. Vui lòng kiểm tra lại giỏ hàng."));
            }
            products.put(pid, locked);
        }
        for (Map.Entry<String, Integer> e : cart.getItems().entrySet()) {
            String size = Cart.sizeOf(e.getKey());
            Product p = products.get(Cart.productIdOf(e.getKey()));
            int qty = e.getValue();
            String colorOfLine = Cart.colorOf(e.getKey());
            int have = p.getStockOf(size, colorOfLine);
            String label = I18n.t("order.label.product", "Sản phẩm \"{0}\"", p.getName())
                    + (size.isEmpty() ? "" : " " + I18n.t("order.label.size", "(size {0})", size))
                    + (colorOfLine.isEmpty() ? "" : " " + I18n.t("order.label.color", "(màu {0})", colorOfLine));
            if (have < qty) {
                throw new IllegalStateException(have <= 0
                        ? I18n.t("order.stock.out", "{0} vừa hết hàng.", label)
                        : I18n.t("order.stock.low", "{0} chỉ còn {1} trong kho.", label, have));
            }
            p.adjustStock(size, colorOfLine, -qty);
        }
        productRepository.saveAll(products.values());

        long total = 0;
        for (Map.Entry<String, Integer> e : cart.getItems().entrySet()) {
            Product p = products.get(Cart.productIdOf(e.getKey()));
            String size = Cart.sizeOf(e.getKey());
            String color = Cart.colorOf(e.getKey());
            int qty = e.getValue();
            Product.ColorOption co = p.findColor(color);

            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductCode(p.getCode());
            item.setImage(co != null && co.getImage() != null ? co.getImage() : p.getImage());
            item.setColor(color.isEmpty() ? null : color);
            item.setUnitPrice(p.getPrice());
            item.setQuantity(qty);
            item.setSize(size.isEmpty() ? null : size);
            order.addItem(item);
            total += p.getPrice() * qty;
        }

        // Mã giảm giá (kiểm tra lại + cộng lượt dùng ngay trong transaction) và phí vận chuyển - luôn tính lại ở server
        long discount = 0;
        String voucherCode = form.getVoucherCode() == null ? "" : form.getVoucherCode().trim();
        if (!voucherCode.isEmpty()) {
            Voucher v = voucherService.redeem(voucherCode, total);
            discount = v.discountFor(total);
            order.setVoucherCode(v.getCode());
        }
        PricingService.Quote quote = pricingService.quote(total, discount, order.getVoucherCode());
        order.setDiscountAmount(quote.getDiscount());
        order.setShippingFee(quote.getShipping());
        order.setTotalAmount(quote.getTotal());
        order.setReturnWindowDays(currentReturnWindowDays());

        // Đơn NÀO cũng phải chờ nhân viên/admin kiểm tra trước:
        //  - Chuyển khoản: chờ kiểm tra ảnh bill + tiền đã về tài khoản
        //  - COD có đặt cọc: khách phải chuyển khoản tiền cọc + gửi ảnh bill -> chờ kiểm tra tiền cọc đã về
        //  - COD không cọc (tắt đặt cọc): chờ xác nhận đơn
        boolean bank = Order.PAYMENT_BANK.equals(form.getPaymentMethod());
        long deposit = Order.PAYMENT_COD.equals(form.getPaymentMethod()) ? pricingService.depositFor(quote.getTotal()) : 0;
        order.setDepositAmount(deposit);
        if (bank || deposit > 0) {
            if (deposit > 0 && (slipFile == null || slipFile.isBlank())) {
                throw new IllegalStateException(I18n.t("order.cod.deposit.required", "Đơn COD cần đặt cọc trước: vui lòng chuyển khoản tiền cọc và tải ảnh bill."));
            }
            order.setSlipImage(slipFile);
            order.setStatus(Order.STATUS_PAYMENT_REVIEW);
        } else {
            order.setStatus(Order.STATUS_PENDING);
        }

        Order saved = orderRepository.save(order);
        saved.setOrderCode(String.format("KS%06d", saved.getId()));
        Order result = orderRepository.save(saved);
        if (movements != null) {
            for (OrderItem it : result.getItems()) {
                movements.record(it.getProductId(), it.getProductName(), it.getProductCode(), it.getSize(), it.getColor(),
                        StockMovement.TYPE_SALE, -it.getQuantity(), null, result.getOrderCode(), username);
            }
        }
        return result;
    }

    public Order findById(Long id) {
        return orderRepository.findById(id).orElse(null);
    }

    public List<Order> findAllNewestFirst() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Đơn của 1 khách, mới nhất lên đầu */
    public List<Order> findByUsernameNewestFirst(String username) {
        return orderRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    /** Số đơn đang chờ nhân viên/admin xử lý */
    public long countAwaitingReview() {
        return orderRepository.countByStatusIn(List.of(Order.STATUS_PENDING, Order.STATUS_PAYMENT_REVIEW));
    }

    // ==================== TỒN KHO (hoàn / trừ lại) ====================

    /** Các dòng của đơn, sắp theo id sản phẩm tăng dần (khoá kho luôn theo cùng một thứ tự để tránh deadlock) */
    private static List<OrderItem> itemsByProductId(Order o) {
        List<OrderItem> list = new ArrayList<>();
        for (OrderItem it : o.getItems()) {
            if (it.getProductId() != null) list.add(it);
        }
        list.sort(Comparator.comparing(OrderItem::getProductId));
        return list;
    }

    private static String sizeOf(OrderItem it) {
        return it.getSize() == null ? "" : it.getSize();
    }

    private static String colorOf(OrderItem it) {
        return it.getColor() == null ? "" : it.getColor();
    }

    /** Cộng lại tồn kho của cả đơn (nếu chưa cộng). Gọi trong transaction, đơn đã được khoá. */
    private void restoreStock(Order o) {
        if (o.isStockRestored()) return;
        for (OrderItem it : itemsByProductId(o)) {
            productRepository.findByIdForUpdate(it.getProductId()).ifPresent(p -> {
                p.adjustStock(sizeOf(it), colorOf(it), it.getQuantity());
                productRepository.save(p);
                if (movements != null) {
                    movements.record(p.getId(), p.getName(), p.getCode(), sizeOf(it), colorOf(it), StockMovement.TYPE_RESTORE,
                            it.getQuantity(), "Hoàn kho (đơn huỷ / từ chối / giao không thành công)", o.getOrderCode(), null);
                }
            });
        }
        o.setStockRestored(true);
    }

    /**
     * Trừ kho lại cho đơn đã được hoàn kho trước đó (giao lại đơn sau khi giao không thành công).
     * Không đủ hàng thì báo lỗi và KHÔNG trừ gì cả.
     */
    private void deductStockAgain(Order o) {
        if (!o.isStockRestored()) return;
        Map<Long, Product> locked = new LinkedHashMap<>();
        for (OrderItem it : itemsByProductId(o)) {
            if (!locked.containsKey(it.getProductId())) {
                productRepository.findByIdForUpdate(it.getProductId()).ifPresent(p -> locked.put(p.getId(), p));
            }
        }
        for (OrderItem it : itemsByProductId(o)) {
            Product p = locked.get(it.getProductId());
            if (p == null) continue;   // sản phẩm đã bị xoá khỏi kho: không còn gì để trừ
            if (p.getStockOf(sizeOf(it), colorOf(it)) < it.getQuantity()) {
                throw new IllegalStateException("Không đủ hàng trong kho để giao lại: \"" + it.getProductName() + "\""
                        + (sizeOf(it).isEmpty() ? "" : " (size " + sizeOf(it) + ")")
                        + (colorOf(it).isEmpty() ? "" : " (màu " + colorOf(it) + ")")
                        + ". Vui lòng nhập thêm hàng vào kho trước.");
            }
            p.adjustStock(sizeOf(it), colorOf(it), -it.getQuantity());
            if (movements != null) {
                movements.record(p.getId(), p.getName(), p.getCode(), sizeOf(it), colorOf(it), StockMovement.TYPE_SALE,
                        -it.getQuantity(), "Giao lại đơn", o.getOrderCode(), null);
            }
        }
        productRepository.saveAll(locked.values());
        o.setStockRestored(false);
    }

    /**
     * Nhân viên/admin XÁC NHẬN đơn (đã kiểm tra bill / tiền đã về).
     * @throws IllegalStateException với nội dung tiếng Việt nếu đơn không còn ở trạng thái chờ
     */
    @Transactional
    public Order confirm(Long id, String staff) {
        Order o = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng.")));
        if (!o.isAwaitingReview()) {
            throw new IllegalStateException("Đơn hàng này đã được xử lý rồi (" + o.getStatusLabel() + ").");
        }
        if (o.isHasTransfer() && !o.isHasSlip()) {
            throw new IllegalStateException(o.isBank()
                    ? "Đơn chuyển khoản chưa có ảnh bill nên không thể xác nhận."
                    : "Đơn COD đặt cọc chưa có ảnh bill chuyển khoản cọc nên không thể xác nhận.");
        }
        o.setStatus(Order.STATUS_CONFIRMED);
        o.setReviewedBy(staff);
        o.setReviewedAt(LocalDateTime.now());
        o.setRejectReason(null);
        return orderRepository.save(o);
    }

    /** Chuỗi dùng để so khớp nội dung chuyển khoản: chữ HOA + số, bỏ dấu cách/ký tự lạ */
    static String compact(String s) {
        return s == null ? "" : s.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** Nội dung chuyển khoản mà trang thanh toán gợi ý cho khách: "KS" + tên tài khoản (tối đa 20 ký tự) */
    static String transferMemoOf(String username) {
        String clean = username == null ? "" : username.replaceAll("[^A-Za-z0-9]", "");
        if (clean.length() > 20) clean = clean.substring(0, 20);
        return compact("KS" + clean);
    }

    /**
     * ĐỐI SOÁT TỰ ĐỘNG: ngân hàng báo có khoản tiền [amount] với nội dung [content] (webhook).
     * Tìm đơn chuyển khoản đang chờ kiểm tra có ĐÚNG số tiền và nội dung chứa mã đơn (hoặc nội dung gợi ý "KS tên-tài-khoản").
     * Chỉ tự xác nhận khi tìm được DUY NHẤT một đơn khớp; trùng nhiều đơn hoặc không khớp thì để nhân viên xử lý tay.
     * @return đơn vừa được tự xác nhận, hoặc null nếu không tự xác nhận được
     */
    @Transactional
    public Order autoConfirmByTransfer(long amount, String content) {
        String text = compact(content);
        if (amount <= 0 || text.isEmpty()) return null;

        // Đơn chuyển khoản: số tiền = tổng đơn. Đơn COD đặt cọc: số tiền = tiền cọc (getPayNow).
        List<Order> sameAmount = new ArrayList<>();
        List<Order> waiting = new ArrayList<>(orderRepository.findByStatusAndPaymentMethod(Order.STATUS_PAYMENT_REVIEW, Order.PAYMENT_BANK));
        List<Order> codWaiting = orderRepository.findByStatusAndPaymentMethod(Order.STATUS_PAYMENT_REVIEW, Order.PAYMENT_COD);
        if (codWaiting != null) waiting.addAll(codWaiting);
        for (Order o : waiting) {
            if (o.isHasTransfer() && o.getPayNow() == amount && o.isHasSlip()) sameAmount.add(o);
        }
        if (sameAmount.isEmpty()) return null;

        // Ưu tiên khớp theo mã đơn (chính xác nhất), sau đó mới khớp theo nội dung gợi ý của tài khoản
        List<Order> matched = new ArrayList<>();
        for (Order o : sameAmount) {
            if (o.getOrderCode() != null && text.contains(compact(o.getOrderCode()))) matched.add(o);
        }
        if (matched.isEmpty()) {
            for (Order o : sameAmount) {
                if (text.contains(transferMemoOf(o.getUsername()))) matched.add(o);
            }
        }
        if (matched.size() != 1) return null;
        try {
            return confirm(matched.get(0).getId(), Order.CONFIRMED_BY_BANK);
        } catch (IllegalStateException e) {
            return null;   // vừa bị nhân viên xử lý / khách huỷ trước đó
        }
    }

    /**
     * Nhân viên/admin TỪ CHỐI đơn (bill sai, chưa nhận được tiền...) -> hoàn lại tồn kho.
     * @throws IllegalStateException với nội dung tiếng Việt
     */
    @Transactional
    public Order reject(Long id, String staff, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3) {
            throw new IllegalStateException("Vui lòng nhập lý do từ chối (để khách biết vì sao).");
        }
        if (why.length() > 300) {
            throw new IllegalStateException("Lý do quá dài (tối đa 300 ký tự).");
        }
        Order o = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng.")));
        if (!o.isAwaitingReview()) {
            throw new IllegalStateException("Đơn hàng này đã được xử lý rồi (" + o.getStatusLabel() + ").");
        }
        // Hoàn lại tồn kho đã trừ lúc đặt hàng + trả lại lượt dùng mã giảm giá
        restoreStock(o);
        voucherService.release(o.getVoucherCode());
        o.setStatus(Order.STATUS_REJECTED);
        o.setReviewedBy(staff);
        o.setReviewedAt(LocalDateTime.now());
        o.setRejectReason(why);
        return orderRepository.save(o);
    }

    /**
     * KHÁCH tự HUỶ đơn của mình khi đơn còn chờ nhân viên kiểm tra/xác nhận (PENDING / PAYMENT_REVIEW) -> hoàn lại tồn kho.
     * Đơn đã xác nhận thì khách không tự huỷ được (phải liên hệ cửa hàng).
     * Đơn chuyển khoản đã chuyển tiền: nhân viên sẽ hoàn tiền thủ công theo thông tin liên hệ của khách.
     * @throws IllegalStateException với nội dung tiếng Việt
     */
    @Transactional
    public Order cancelByCustomer(Long id, String username) {
        Order o = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng.")));
        if (!Objects.equals(o.getUsername(), username)) {
            throw new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng."));
        }
        if (!o.isCustomerCanCancel()) {
            throw new IllegalStateException(o.isRejected()
                    ? I18n.t("order.already.cancelled", "Đơn này đã được huỷ hoặc bị từ chối rồi.")
                    : I18n.t("order.cannot.cancel", "Đơn này đã được cửa hàng xác nhận nên không tự huỷ được. Vui lòng liên hệ cửa hàng để được hỗ trợ."));
        }
        restoreStock(o);
        voucherService.release(o.getVoucherCode());
        o.setStatus(Order.STATUS_REJECTED);
        o.setReviewedBy(Order.CANCELLED_BY_CUSTOMER);
        o.setReviewedAt(LocalDateTime.now());
        o.setRejectReason(o.isBank() ? I18n.t("order.cancel.reason.bank", "Khách tự huỷ đơn (đơn chuyển khoản: cửa hàng sẽ hoàn tiền nếu bạn đã chuyển)")
                : o.isCodDeposit() ? I18n.t("order.cancel.reason.cod", "Khách tự huỷ đơn (đơn COD đặt cọc: cửa hàng sẽ liên hệ xử lý tiền cọc nếu bạn đã chuyển)")
                : I18n.t("order.cancel.reason", "Khách tự huỷ đơn"));
        return orderRepository.save(o);
    }

    // ==================== GIAO HÀNG ====================

    private Order confirmedOrder(Long id) {
        Order o = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng.")));
        if (!o.isConfirmed()) {
            throw new IllegalStateException("Chỉ đơn đã được xác nhận mới giao hàng được.");
        }
        return o;
    }

    private static String clean(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /**
     * Giao đơn cho đơn vị vận chuyển (hoặc đổi hãng / mã vận đơn khi đang vận chuyển, hoặc giao lại sau khi hoàn hàng).
     * @throws IllegalStateException với nội dung tiếng Việt
     */
    @Transactional
    public Order ship(Long id, String carrierCode, String trackingCode, String note, String staff) {
        Order o = confirmedOrder(id);
        if (o.isDelivered()) {
            throw new IllegalStateException("Đơn này đã giao thành công, không sửa thông tin vận chuyển được nữa.");
        }
        if (o.isArrived()) {
            throw new IllegalStateException("Vận chuyển đã báo giao tới khách, đang chờ khách xác nhận nên không đổi thông tin vận chuyển được.");
        }
        Carrier c = Carrier.fromCode(carrierCode);
        if (c == null) {
            throw new IllegalStateException("Vui lòng chọn đơn vị vận chuyển.");
        }
        String code = clean(trackingCode, 61);
        if (c.isNeedsCode() && code.isEmpty()) {
            throw new IllegalStateException("Vui lòng nhập mã vận đơn của " + c.getLabel() + ".");
        }
        if (code.length() > 60 || !code.matches("[A-Za-z0-9._\\-]*")) {
            throw new IllegalStateException("Mã vận đơn chỉ gồm chữ, số, dấu chấm, gạch ngang và tối đa 60 ký tự.");
        }
        // Giao lại đơn đã hoàn / khách từ chối: kho đã được cộng lại lúc đó nên phải trừ lại
        deductStockAgain(o);
        boolean startNow = !o.isShipping();
        o.setCarrier(c.name());
        o.setTrackingCode(code.isEmpty() ? null : code);
        String n = clean(note, 300);
        o.setShippingNote(n.isEmpty() ? null : n);
        o.setShippingStatus(Order.SHIP_SHIPPING);
        if (startNow || o.getShippedAt() == null) {
            o.setShippedAt(LocalDateTime.now());
        }
        o.setArrivedAt(null);
        o.setDeliveredAt(null);
        o.setDeliveredBy(null);
        o.setShippingBy(staff);
        return orderRepository.save(o);
    }

    /** Thời hạn đổi trả đang áp dụng (cùng giới hạn 1-60 với ReturnService.getWindowDays) */
    private int currentReturnWindowDays() {
        int days = settings != null ? settings.getInt(SettingsService.RETURNS_WINDOW_DAYS) : returnWindowDays;
        return Math.max(1, Math.min(60, days));
    }

    /** Số ngày chờ khách xác nhận trước khi tự hoàn tất (giới hạn 1-30 để cấu hình sai không gây hại) */
    public int getAutoCompleteDays() {
        int days = settings != null ? settings.getInt(SettingsService.SHIPPING_AUTO_COMPLETE_DAYS) : autoCompleteDays;
        return Math.max(1, Math.min(30, days));
    }

    /** Vận chuyển báo đã giao tới khách -> chờ khách xác nhận; quá hạn không phản hồi thì tự hoàn tất */
    @Transactional
    public Order markArrived(Long id, String staff) {
        Order o = confirmedOrder(id);
        if (!o.isShipping()) {
            throw new IllegalStateException("Chỉ đơn đang vận chuyển mới ghi nhận được là đã giao tới khách.");
        }
        o.setShippingStatus(Order.SHIP_ARRIVED);
        o.setArrivedAt(LocalDateTime.now());
        o.setShippingBy(staff);
        return orderRepository.save(o);
    }

    private void finishDelivered(Order o, String by) {
        o.setShippingStatus(Order.SHIP_DELIVERED);
        o.setDeliveredAt(LocalDateTime.now());
        o.setDeliveredBy(by);
    }

    /** Nhân viên ghi nhận khách đã nhận hàng (từ trạng thái đang vận chuyển hoặc đã tới nơi) */
    @Transactional
    public Order markDelivered(Long id, String staff) {
        Order o = confirmedOrder(id);
        if (!o.isShipping() && !o.isArrived()) {
            throw new IllegalStateException("Đơn chưa được giao cho đơn vị vận chuyển nên chưa thể đánh dấu đã giao.");
        }
        finishDelivered(o, staff);
        o.setShippingBy(staff);
        return orderRepository.save(o);
    }

    /**
     * KHÁCH tự bấm "Tôi đã nhận được hàng" ở trang "Đơn hàng của tôi".
     * Chỉ chủ đơn mới bấm được; đơn phải đang vận chuyển hoặc đã tới nơi.
     */
    @Transactional
    public Order confirmReceivedByCustomer(Long id, String username) {
        Order o = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng.")));
        if (!Objects.equals(o.getUsername(), username)) {
            throw new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng."));
        }
        if (o.isDelivered()) {
            throw new IllegalStateException(I18n.t("order.already.received", "Đơn này đã được xác nhận nhận hàng rồi."));
        }
        if (!o.isCustomerCanConfirm()) {
            throw new IllegalStateException(I18n.t("order.not.shipped.yet", "Đơn này chưa được giao cho đơn vị vận chuyển nên chưa thể xác nhận nhận hàng."));
        }
        finishDelivered(o, "@customer");
        return orderRepository.save(o);
    }

    /**
     * Tự hoàn tất các đơn "đã tới nơi" quá hạn mà khách không phản hồi (chạy định kỳ bởi ShippingScheduler).
     * @return số đơn đã chuyển sang "đã giao thành công"
     */
    @Transactional
    public int autoCompleteArrived() {
        LocalDateTime limit = LocalDateTime.now().minusDays(getAutoCompleteDays());
        List<Order> due = orderRepository.findByShippingStatusAndArrivedAtBefore(Order.SHIP_ARRIVED, limit);
        for (Order o : due) {
            finishDelivered(o, "@auto");
        }
        orderRepository.saveAll(due);
        return due.size();
    }

    /**
     * Giao không thành công. refused = true: KHÁCH KHÔNG NHẬN hàng (từ chối / không nghe máy / không có mặt) -> tính vào cảnh báo;
     * false: lý do khác (sai địa chỉ, thất lạc, hư hỏng...). Bắt buộc ghi lý do.
     * Tồn kho được CỘNG LẠI NGAY (hàng đang hoàn về cửa hàng) để có thể bán tiếp; chỉ cộng một lần.
     * Nếu hàng về bị hư hỏng thì nhân viên trừ lại ở trang kho. Giao lại đơn này thì kho tự trừ lại.
     */
    @Transactional
    public Order markFailed(Long id, boolean refused, String reason, String staff) {
        Order o = confirmedOrder(id);
        if (!o.isShipping() && !o.isArrived()) {
            throw new IllegalStateException("Chỉ đơn đang vận chuyển mới ghi nhận giao không thành công được.");
        }
        String why = clean(reason, 300);
        if (why.length() < 3) {
            throw new IllegalStateException("Vui lòng nhập lý do giao không thành công.");
        }
        restoreStock(o);
        o.setShippingStatus(refused ? Order.SHIP_REFUSED : Order.SHIP_RETURNED);
        o.setShippingNote(why);
        o.setArrivedAt(null);
        o.setDeliveredAt(null);
        o.setDeliveredBy(null);
        o.setShippingBy(staff);
        return orderRepository.save(o);
    }

    /**
     * Hàng khách trả về còn nguyên vẹn -> cộng lại tồn kho của cả đơn (chỉ cộng một lần).
     * @return true nếu đã cộng kho, false nếu đơn đã được hoàn kho trước đó
     */
    @Transactional
    public boolean restockAfterReturn(Long id) {
        Order o = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException(I18n.t("order.not.found", "Không tìm thấy đơn hàng.")));
        if (o.isStockRestored()) return false;
        restoreStock(o);
        orderRepository.save(o);
        return true;
    }

    // ----- Cảnh báo khách từng không nhận hàng -----

    /** Số đơn trước đây của khách (cùng tài khoản hoặc cùng số điện thoại nhận) bị khách không nhận hàng */
    public long refusalHistory(Order o) {
        if (o == null || o.getId() == null) return 0;
        return orderRepository.countRefusedHistory(Order.SHIP_REFUSED, o.getId(), o.getUsername(), o.getPhone());
    }

    /** orderId -> số lần khách từng không nhận hàng (chỉ chứa đơn có lịch sử > 0), tính 1 lần cho cả danh sách */
    public Map<Long, Long> refusalHistoryFor(List<Order> orders) {
        Map<Long, Long> result = new HashMap<>();
        List<Order> refused = orderRepository.findByShippingStatus(Order.SHIP_REFUSED);
        if (refused.isEmpty()) return result;
        for (Order o : orders) {
            long n = refused.stream()
                    .filter(r -> !Objects.equals(r.getId(), o.getId()))
                    .filter(r -> Objects.equals(r.getUsername(), o.getUsername())
                            || (r.getPhone() != null && r.getPhone().equals(o.getPhone())))
                    .count();
            if (n > 0) result.put(o.getId(), n);
        }
        return result;
    }
}
