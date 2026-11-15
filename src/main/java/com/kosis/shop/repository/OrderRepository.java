package com.kosis.shop.repository;

import com.kosis.shop.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Lấy đơn và KHOÁ dòng cho tới hết transaction: nhân viên xác nhận/từ chối và khách huỷ đơn cùng lúc
     * thì chỉ một bên thắng, không bị ghi đè lên nhau hay hoàn kho 2 lần.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /** Mới nhất lên đầu (trang quản lý đơn hàng) */
    List<Order> findAllByOrderByCreatedAtDesc();

    /** Đơn của 1 khách, mới nhất lên đầu (trang "Đơn hàng của tôi") */
    List<Order> findByUsernameOrderByCreatedAtDesc(String username);

    /** Đếm đơn theo trạng thái (số đơn đang chờ kiểm tra hiện trên sidebar) */
    long countByStatusIn(Collection<String> statuses);

    /** Số đơn đã xác nhận nhưng chưa giao cho đơn vị vận chuyển (badge "Giao hàng" ở sidebar) */
    @Query("select count(o) from Order o where o.status = :status and (o.shippingStatus is null or o.shippingStatus = :ship)")
    long countAwaitingShipment(@Param("status") String status, @Param("ship") String ship);

    /** Đơn theo trạng thái + phương thức thanh toán (đối soát chuyển khoản tự động) */
    List<Order> findByStatusAndPaymentMethod(String status, String paymentMethod);

    /** Đơn theo trạng thái giao hàng */
    List<Order> findByShippingStatus(String shippingStatus);

    /** Đơn "đã tới nơi" mà vận chuyển báo từ trước mốc [before] - dùng để tự hoàn tất khi khách không phản hồi */
    List<Order> findByShippingStatusAndArrivedAtBefore(String shippingStatus, LocalDateTime before);

    /** Số đơn TRƯỚC ĐÂY khách không nhận hàng (theo tài khoản HOẶC số điện thoại nhận, bỏ qua chính đơn này) */
    @Query("select count(o) from Order o where o.shippingStatus = :s and o.id <> :id and (o.username = :u or o.phone = :p)")
    long countRefusedHistory(@Param("s") String s, @Param("id") Long id, @Param("u") String username, @Param("p") String phone);

    /** [username, số đơn khách không nhận hàng] - trang khách hàng */
    @Query("select o.username, count(o) from Order o where o.shippingStatus = :s group by o.username")
    List<Object[]> refusedCountsByUser(@Param("s") String s);

    /** 5 đơn mới nhất (bảng "Đơn hàng gần đây" ở trang tổng quan) */
    List<Order> findTop5ByOrderByCreatedAtDesc();

    /** Số đơn được đặt trong khoảng thời gian [from, to) */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    /** Doanh thu = tổng tiền các đơn ĐÃ XÁC NHẬN trong khoảng thời gian [from, to) (tính theo lúc xác nhận) */
    @Query("select coalesce(sum(o.totalAmount), 0) from Order o "
            + "where o.status = :status and o.reviewedAt >= :from and o.reviewedAt < :to")
    long sumRevenue(@Param("status") String status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Các đơn ĐÃ XÁC NHẬN trong khoảng [from, to) theo lúc xác nhận (báo cáo doanh thu), cũ -> mới */
    @Query("select o from Order o where o.status = :status and o.reviewedAt >= :from and o.reviewedAt < :to "
            + "order by o.reviewedAt asc")
    List<Order> findConfirmedBetween(@Param("status") String status, @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    /**
     * Như {@link #findConfirmedBetween} nhưng nạp luôn các dòng hàng bằng 1 câu truy vấn (báo cáo lợi nhuận
     * cần giá vốn từng dòng - tránh mỗi đơn 1 truy vấn riêng).
     */
    @Query("select distinct o from Order o left join fetch o.items "
            + "where o.status = :status and o.reviewedAt >= :from and o.reviewedAt < :to "
            + "order by o.reviewedAt asc")
    List<Order> findConfirmedWithItemsBetween(@Param("status") String status, @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /**
     * Sản phẩm bán chạy trong khoảng [from, to), doanh thu cao -> thấp:
     * [tên, mã, tổng số lượng, tổng tiền hàng, giá vốn các dòng CÓ giá vốn, tiền hàng của các dòng CÓ giá vốn].
     * Lợi nhuận sản phẩm = cột 5 - cột 4 (trước giảm giá voucher; dòng không có giá vốn bị bỏ qua).
     */
    @Query("select i.productName, i.productCode, sum(i.quantity), sum(i.unitPrice * i.quantity), "
            + "sum(case when i.unitCost is not null then i.unitCost * i.quantity else 0 end), "
            + "sum(case when i.unitCost is not null then i.unitPrice * i.quantity else 0 end) "
            + "from Order o join o.items i "
            + "where o.status = :status and o.reviewedAt >= :from and o.reviewedAt < :to "
            + "group by i.productId, i.productName, i.productCode "
            + "order by sum(i.unitPrice * i.quantity) desc")
    List<Object[]> topProductsBetween(@Param("status") String status, @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to, Pageable pageable);

    /**
     * Thống kê theo từng tài khoản (trang khách hàng): [username, tổng số đơn, tổng tiền các đơn ĐÃ XÁC NHẬN].
     */
    @Query("select o.username, count(o), coalesce(sum(case when o.status = :status then o.totalAmount else 0 end), 0) "
            + "from Order o group by o.username")
    List<Object[]> customerOrderStats(@Param("status") String status);

    /** Số đơn khách đã NHẬN HÀNG thành công có chứa sản phẩm này (khách chỉ được đánh giá sản phẩm đã mua) */
    @Query("select count(o) from Order o join o.items i where o.username = :u and o.status = :status "
            + "and o.shippingStatus = :ship and i.productId = :pid")
    long countDeliveredWithProduct(@Param("u") String username, @Param("pid") Long productId,
                                   @Param("status") String status, @Param("ship") String ship);

    /** Đổi tên đăng nhập: chuyển luôn các đơn đã đặt sang tên mới (đơn lưu username dạng chữ, không có khoá ngoại) */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.username = :newName where o.username = :oldName")
    int renameCustomer(@Param("oldName") String oldName, @Param("newName") String newName);

    /** Đổi tên đăng nhập: cập nhật tên người xử lý trong các đơn đã xác nhận / từ chối */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.reviewedBy = :newName where o.reviewedBy = :oldName")
    int renameReviewer(@Param("oldName") String oldName, @Param("newName") String newName);
}
