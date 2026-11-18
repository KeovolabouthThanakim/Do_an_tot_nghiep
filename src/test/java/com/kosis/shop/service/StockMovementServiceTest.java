package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.StockMovement;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StockMovementServiceTest {

    StockMovementRepository repo;
    ProductRepository products;
    StockMovementService service;

    @BeforeEach
    void setUp() {
        repo = mock(StockMovementRepository.class);
        products = mock(ProductRepository.class);
        service = new StockMovementService(repo, products);
        when(repo.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(products.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product plain(long id, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("Túi thử");
        p.setCode("KS" + id);
        p.setCategory("Túi");
        p.setSizes("-");
        p.setStock(stock);
        when(products.findByIdForUpdate(id)).thenReturn(Optional.of(p));
        return p;
    }

    private Product sized(long id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Áo thử");
        p.setCode("KS" + id);
        p.setCategory("Áo");
        p.setSizes("S,M");
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("S", 3);
        m.put("M", 5);
        p.setSizeStockMap(m);
        when(products.findByIdForUpdate(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void adjustDownReducesStockAndLogsReason() {
        Product p = plain(1, 10);
        StockMovement m = service.adjustDown(1L, null, 4, "Kiểm kê thiếu", "admin");
        assertEquals(6, p.getStock());
        assertEquals(-4, m.getDelta());
        assertEquals(StockMovement.TYPE_ADJUST, m.getType());
        assertEquals("Kiểm kê thiếu", m.getReason());
        assertEquals("admin", m.getCreatedBy());
    }

    @Test
    void reasonIsRequired() {
        plain(1, 10);
        assertThrows(IllegalArgumentException.class, () -> service.adjustDown(1L, null, 1, "  ", "admin"));
        verify(repo, never()).save(any());
    }

    @Test
    void cannotReduceBelowZero() {
        Product p = plain(1, 2);
        assertThrows(IllegalArgumentException.class, () -> service.adjustDown(1L, null, 3, "Hỏng", "admin"));
        assertEquals(2, p.getStock());
        verify(repo, never()).save(any());
    }

    @Test
    void perSizeProductNeedsSizeAndReducesThatSize() {
        Product p = sized(2);
        assertThrows(IllegalArgumentException.class, () -> service.adjustDown(2L, "", 1, "Hỏng", "admin"));
        service.adjustDown(2L, "m", 2, "Hỏng", "admin");
        assertEquals(3, p.getStockOf("M"));
        assertEquals(3, p.getStockOf("S"));
        assertEquals(6, p.getStock());
    }

    @Test
    void sizeCannotGoBelowItsOwnStock() {
        Product p = sized(2);
        assertThrows(IllegalArgumentException.class, () -> service.adjustDown(2L, "S", 4, "Hỏng", "admin"));
        assertEquals(3, p.getStockOf("S"));
    }
}
