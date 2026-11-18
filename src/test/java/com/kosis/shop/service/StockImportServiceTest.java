package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.StockImport;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.StockImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockImportServiceTest {

    @Mock StockImportRepository importRepository;
    @Mock ProductRepository productRepository;

    StockImportService service;

    @BeforeEach
    void setUp() {
        service = new StockImportService(importRepository, productRepository);
        when(importRepository.save(any(StockImport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product product(long id, int stock, long cost) {
        Product p = new Product();
        p.setId(id);
        p.setName("Áo thử");
        p.setCode("KS" + id);
        p.setCategory("Áo");
        p.setSizes("-");            // không chia size
        p.setStock(stock);
        p.setImportPrice(cost);
        when(productRepository.findByIdForUpdate(id)).thenReturn(Optional.of(p));
        return p;
    }

    private StockImport create(List<StockImportService.Line> lines) {
        return service.create("Xưởng A", "0909", "HD1", LocalDate.now(), "", lines, "admin");
    }

    @Test
    void addsStockAndAveragesCost() {
        Product p = product(1, 10, 100_000);
        StockImport s = create(List.of(new StockImportService.Line(1L, null, 10, 120_000)));
        assertEquals(20, p.getStock());
        assertEquals(110_000, p.getImportPrice());
        assertEquals(1_200_000, s.getTotalCost());
        assertEquals(10, s.getTotalQty());
    }

    @Test
    void firstImportListsPendingProduct() {
        Product p = product(1, 0, 0);
        p.setPendingImport(true);
        assertFalse(p.isListed());
        create(List.of(new StockImportService.Line(1L, null, 5, 120_000)));
        assertFalse(p.isPendingImport());
        assertTrue(p.isListed());
        assertEquals(5, p.getStock());
        assertEquals(120_000, p.getImportPrice());
    }

    @Test
    void failedImportKeepsProductPending() {
        Product p = product(1, 0, 0);
        p.setPendingImport(true);
        assertThrows(IllegalArgumentException.class,
                () -> create(List.of(new StockImportService.Line(1L, null, 0, 120_000))));
        assertTrue(p.isPendingImport());
    }

    @Test
    void existingProductStaysListedAfterImport() {
        Product p = product(1, 10, 100_000);   // hàng cũ: pendingImport = null
        assertTrue(p.isListed());
        create(List.of(new StockImportService.Line(1L, null, 1, 100_000)));
        assertTrue(p.isListed());
    }

    @Test
    void importWritesAMovementPerLine() {
        StockMovementService movements = org.mockito.Mockito.mock(StockMovementService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "movements", movements);
        product(1, 0, 0);
        create(List.of(new StockImportService.Line(1L, null, 5, 120_000)));
        org.mockito.Mockito.verify(movements).record(org.mockito.ArgumentMatchers.eq(1L), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("IMPORT"), org.mockito.ArgumentMatchers.eq(5), any(), any(),
                org.mockito.ArgumentMatchers.eq("admin"));
    }

    @Test
    void emptyStockTakesNewCost() {
        Product p = product(1, 0, 90_000);
        create(List.of(new StockImportService.Line(1L, null, 5, 120_000)));
        assertEquals(5, p.getStock());
        assertEquals(120_000, p.getImportPrice());
    }

    @Test
    void perSizeProductNeedsSizeAndAddsToThatSize() {
        Product p = product(1, 0, 0);
        p.setSizes("S,M");
        p.setSizeStockMap(Map.of("S", 1, "M", 2));
        assertThrows(IllegalArgumentException.class,
                () -> create(List.of(new StockImportService.Line(1L, "", 3, 50_000))));
        create(List.of(new StockImportService.Line(1L, "m", 3, 50_000)));
        assertEquals(5, p.getStockOf("M"));
        assertEquals(1, p.getStockOf("S"));
        assertEquals(6, p.getStock());
    }

    @Test
    void rejectsBadInput() {
        product(1, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> service.create(" ", "", "", LocalDate.now(), "",
                List.of(new StockImportService.Line(1L, null, 1, 1000)), "a"));
        assertThrows(IllegalArgumentException.class, () -> create(List.of()));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(new StockImportService.Line(1L, null, 0, 1000))));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(new StockImportService.Line(1L, null, 1, 0))));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(new StockImportService.Line(null, null, 1, 1000))));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(new StockImportService.Line(99L, null, 1, 1000))));
    }

    // ---------- Tạo sản phẩm kèm tồn đầu kỳ ----------

    /** Sản phẩm MỚI (chưa có id): save() gán id, findByIdForUpdate() trả lại đúng sản phẩm đó. */
    private Product newProduct(long id, String sizes, String colors) {
        Product p = new Product();
        p.setName("Áo mới");
        p.setCode("N" + id);
        p.setCategory("Áo");
        p.setGender("MEN");
        p.setSizes(sizes);
        p.setColors(colors);
        when(productRepository.save(p)).thenAnswer(inv -> {
            p.setId(id);
            return p;
        });
        when(productRepository.findByIdForUpdate(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void openingStockCreatesSlipAndListsProduct() {
        Product p = newProduct(7, "-", null);
        StockImport s = service.createProduct(p, List.of(new StockImportService.OpeningLine(null, null, 12, 150_000)), "admin");
        assertNotNull(s);
        assertEquals(StockImportService.OPENING_SUPPLIER, s.getSupplier());
        assertEquals(12, s.getTotalQty());
        assertEquals(12, p.getStock());
        assertEquals(150_000, p.getImportPrice());
        assertFalse(p.isPendingImport());
        assertTrue(p.isListed());
    }

    @Test
    void openingStockIsSplitByColorAndSizeWithAverageCost() {
        Product p = newProduct(8, "S,M", "Olive:#6b7a46,Đen:#111111");
        StockImport s = service.createProduct(p, List.of(
                new StockImportService.OpeningLine("S", "Olive", 3, 100_000),
                new StockImportService.OpeningLine("M", "Đen", 5, 120_000)), "admin");
        assertEquals(3, p.getStockOf("S", "Olive"));
        assertEquals(5, p.getStockOf("M", "Đen"));
        assertEquals(0, p.getStockOf("M", "Olive"));
        assertEquals(8, p.getStock());
        assertEquals(112_500, p.getImportPrice());      // trung bình theo số lượng: (3x100.000 + 5x120.000) / 8
        assertEquals(2, s.getItems().size());
    }

    @Test
    void productWithoutOpeningStockStaysPendingAndWritesNoSlip() {
        Product p = newProduct(9, "-", null);
        assertNull(service.createProduct(p, List.of(), "admin"));
        assertTrue(p.isPendingImport());
        assertEquals(0, p.getStock());
        assertEquals(0, p.getImportPrice());
        org.mockito.Mockito.verify(importRepository, org.mockito.Mockito.never()).save(any(StockImport.class));
    }

    @Test
    void openingStockNeedsCost() {
        Product p = newProduct(10, "-", null);
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(p,
                List.of(new StockImportService.OpeningLine(null, null, 5, 0)), "admin"));
    }

    @Test
    void manyProductsSplitIntoSlipsOfMaxLines() {
        List<StockImportService.NewProduct> items = new java.util.ArrayList<>();
        for (int i = 1; i <= StockImportService.MAX_LINES + 1; i++) {
            items.add(new StockImportService.NewProduct(newProduct(100 + i, "-", null),
                    List.of(new StockImportService.OpeningLine(null, null, 1, 10_000))));
        }
        List<StockImport> slips = service.createProducts(items, "admin");
        assertEquals(2, slips.size());
        assertEquals(StockImportService.MAX_LINES, slips.get(0).getItems().size());
        assertEquals(1, slips.get(1).getItems().size());
    }
}
