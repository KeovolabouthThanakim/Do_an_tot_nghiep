package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import com.kosis.shop.repository.ProductRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Sản phẩm "chờ nhập hàng" không được vào giỏ, kể cả khi ai đó biết id. */
class CartPendingImportTest {

    ProductRepository repo;
    CartService service;
    HttpSession session;

    @BeforeEach
    void setUp() {
        repo = mock(ProductRepository.class);
        service = new CartService(repo);
        session = new MockHttpSession();
    }

    private Product product(long id, boolean pending, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("Áo thử");
        p.setCode("KS" + id);
        p.setCategory("Áo");
        p.setSizes("-");
        p.setStock(stock);
        p.setPendingImport(pending);
        when(repo.findById(id)).thenReturn(Optional.of(p));
        when(repo.findListedById(id)).thenReturn(Optional.of(p).filter(Product::isListed));
        return p;
    }

    @Test
    void pendingProductCannotBeAdded() {
        product(1, true, 0);
        assertThrows(IllegalArgumentException.class, () -> service.add(session, 1, null, null, 1));
    }

    @Test
    void listedProductCanBeAdded() {
        product(2, false, 5);
        assertEquals(1, service.add(session, 2, null, null, 1));
    }

    @Test
    void cartDropsLineWhenProductGoesBackToPending() {
        Product p = product(3, false, 5);
        service.add(session, 3, null, null, 2);
        p.setPendingImport(true);
        when(repo.findAllById(java.util.List.of(3L))).thenReturn(java.util.List.of(p));
        assertTrue(service.view(session).getLines().isEmpty());
    }
}
