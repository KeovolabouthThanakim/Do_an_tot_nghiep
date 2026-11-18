package com.kosis.shop.service;

import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.Review;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Đánh giá sản phẩm: chỉ khách ĐÃ NHẬN hàng (đơn đã giao thành công có sản phẩm đó) mới được đánh giá;
 * mỗi khách 1 đánh giá cho mỗi sản phẩm (gửi lại = sửa đánh giá cũ).
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public ReviewService(ReviewRepository reviewRepository, OrderRepository orderRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /** Khách đã mua và nhận sản phẩm này chưa? */
    public boolean hasPurchased(String username, Long productId) {
        if (username == null || productId == null) return false;
        return orderRepository.countDeliveredWithProduct(username, productId, Order.STATUS_CONFIRMED, Order.SHIP_DELIVERED) > 0;
    }

    @Transactional
    public Review submit(Long productId, String username, int rating, String comment) {
        if (!productRepository.existsById(productId)) {
            throw new IllegalStateException("Không tìm thấy sản phẩm.");
        }
        if (!hasPurchased(username, productId)) {
            throw new IllegalStateException("Bạn chỉ có thể đánh giá sản phẩm sau khi đã nhận hàng.");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalStateException("Vui lòng chọn số sao từ 1 đến 5.");
        }
        String text = comment == null ? "" : comment.trim();
        if (text.length() > Review.MAX_COMMENT) {
            throw new IllegalStateException("Nhận xét quá dài (tối đa " + Review.MAX_COMMENT + " ký tự).");
        }
        Review r = reviewRepository.findByProductIdAndUsername(productId, username).orElse(null);
        if (r == null) {
            r = new Review();
            r.setProductId(productId);
            r.setUsername(username);
        } else {
            r.setUpdatedAt(LocalDateTime.now());
        }
        r.setRating(rating);
        r.setComment(text.isEmpty() ? null : text);
        r.setHidden(false);
        return reviewRepository.save(r);
    }

    public List<Review> visibleFor(Long productId) {
        return reviewRepository.findByProductIdAndHiddenFalseOrderByCreatedAtDesc(productId);
    }

    public Review findMine(Long productId, String username) {
        return username == null ? null : reviewRepository.findByProductIdAndUsername(productId, username).orElse(null);
    }

    public long count(Long productId) {
        return reviewRepository.countByProductIdAndHiddenFalse(productId);
    }

    /** Điểm trung bình (0 nếu chưa có đánh giá) */
    public double average(Long productId) {
        Double a = reviewRepository.averageRating(productId);
        return a == null ? 0 : a;
    }

    @Transactional
    public void setHidden(Long reviewId, boolean hidden) {
        Review r = reviewRepository.findById(reviewId).orElseThrow(() -> new IllegalStateException("Không tìm thấy đánh giá."));
        r.setHidden(hidden);
        reviewRepository.save(r);
    }
}
