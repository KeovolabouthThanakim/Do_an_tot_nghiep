package com.kosis.shop.service;

import com.kosis.shop.entity.Order;
import com.kosis.shop.entity.Review;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock
    ReviewRepository reviewRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    ProductRepository productRepository;

    ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, orderRepository, productRepository);
        when(productRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.findByProductIdAndUsername(1L, "an")).thenReturn(Optional.empty());
    }

    private void customerBought(boolean bought) {
        when(orderRepository.countDeliveredWithProduct("an", 1L, Order.STATUS_CONFIRMED, Order.SHIP_DELIVERED))
                .thenReturn(bought ? 1L : 0L);
    }

    private String error(int rating, String comment) {
        return assertThrows(IllegalStateException.class, () -> service.submit(1L, "an", rating, comment)).getMessage();
    }

    @Test
    void customerWhoReceivedTheProductCanReview() {
        customerBought(true);
        Review r = service.submit(1L, "an", 5, "  Rất đẹp  ");
        assertEquals(5, r.getRating());
        assertEquals("Rất đẹp", r.getComment());       // đã trim
        assertEquals("an", r.getUsername());
        assertFalse(r.isHidden());
    }

    @Test
    void emptyCommentIsStoredAsNull() {
        customerBought(true);
        assertNull(service.submit(1L, "an", 4, "   ").getComment());
    }

    @Test
    void customerWhoNeverReceivedTheProductCannotReview() {
        customerBought(false);
        assertTrue(error(5, "x").contains("sau khi đã nhận hàng"));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void ratingMustBeBetweenOneAndFive() {
        customerBought(true);
        assertTrue(error(0, "x").contains("1 đến 5"));
        assertTrue(error(6, "x").contains("1 đến 5"));
    }

    @Test
    void tooLongCommentIsRejected() {
        customerBought(true);
        assertTrue(error(5, "x".repeat(Review.MAX_COMMENT + 1)).contains("quá dài"));
    }

    @Test
    void unknownProductIsRejected() {
        when(productRepository.existsById(99L)).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.submit(99L, "an", 5, "x"));
    }

    @Test
    void reviewingAgainEditsTheOldReviewAndUnhidesIt() {
        customerBought(true);
        Review old = new Review();
        old.setProductId(1L);
        old.setUsername("an");
        old.setRating(2);
        old.setHidden(true);
        when(reviewRepository.findByProductIdAndUsername(1L, "an")).thenReturn(Optional.of(old));

        Review r = service.submit(1L, "an", 5, "Đã đổi ý");

        assertSame(old, r);
        assertEquals(5, r.getRating());
        assertFalse(r.isHidden());
        assertNotNull(r.getUpdatedAt());
    }

    @Test
    void averageIsZeroWhenThereAreNoReviews() {
        when(reviewRepository.averageRating(1L)).thenReturn(null);
        assertEquals(0.0, service.average(1L));
        when(reviewRepository.averageRating(1L)).thenReturn(4.5);
        assertEquals(4.5, service.average(1L));
    }

    @Test
    void staffCanHideAndUnhideAReview() {
        Review r = new Review();
        when(reviewRepository.findById(10L)).thenReturn(Optional.of(r));
        service.setHidden(10L, true);
        assertTrue(r.isHidden());
        service.setHidden(10L, false);
        assertFalse(r.isHidden());
    }

    @Test
    void hidingAMissingReviewFails() {
        when(reviewRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.setHidden(404L, true));
    }

    @Test
    void anonymousVisitorHasNoReviewOfTheirOwn() {
        assertNull(service.findMine(1L, null));
        assertFalse(service.hasPurchased(null, 1L));
    }
}
