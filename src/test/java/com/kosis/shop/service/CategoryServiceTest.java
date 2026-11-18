package com.kosis.shop.service;

import com.kosis.shop.entity.Product;
import com.kosis.shop.entity.ProductCategory;
import com.kosis.shop.repository.ProductCategoryRepository;
import com.kosis.shop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** CategoryService với repository giả lập trong bộ nhớ (không cần database). */
class CategoryServiceTest {

    private final List<ProductCategory> store = new ArrayList<>();
    private final List<Object[]> productCounts = new ArrayList<>();   // [tên danh mục, số sản phẩm]
    private final List<String> usedByProducts = new ArrayList<>();
    private long nextId = 1;
    private ProductCategoryRepository repo;
    private ProductRepository products;
    private MockEnvironment env;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        store.clear();
        productCounts.clear();
        usedByProducts.clear();
        nextId = 1;

        repo = mock(ProductCategoryRepository.class);
        when(repo.findAllByOrderBySortOrderAscIdAsc()).thenAnswer(inv -> {
            List<ProductCategory> copy = new ArrayList<>(store);
            copy.sort((a, b) -> a.getSortOrder() != b.getSortOrder()
                    ? Integer.compare(a.getSortOrder(), b.getSortOrder()) : Long.compare(a.getId(), b.getId()));
            return copy;
        });
        when(repo.count()).thenAnswer(inv -> (long) store.size());
        when(repo.findById(anyLong())).thenAnswer(inv ->
                store.stream().filter(c -> c.getId().equals(inv.getArgument(0))).findFirst());
        when(repo.save(any(ProductCategory.class))).thenAnswer(inv -> {
            ProductCategory c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(nextId++);
                store.add(c);
            }
            return c;
        });
        doAnswer(inv -> {
            Iterable<ProductCategory> items = inv.getArgument(0);
            for (ProductCategory c : items) {
                if (c.getId() == null) {
                    c.setId(nextId++);
                    store.add(c);
                }
            }
            return null;
        }).when(repo).saveAll(any());
        doAnswer(inv -> {
            store.remove((ProductCategory) inv.getArgument(0));
            return null;
        }).when(repo).delete(any(ProductCategory.class));

        products = mock(ProductRepository.class);
        when(products.countGroupedByCategory()).thenAnswer(inv -> new ArrayList<>(productCounts));
        when(products.findDistinctCategories()).thenAnswer(inv -> new ArrayList<>(usedByProducts));
        when(products.countByCategoryIgnoreCase(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return productCounts.stream().filter(r -> ((String) r[0]).equalsIgnoreCase(name))
                    .mapToLong(r -> ((Number) r[1]).longValue()).sum();
        });
        when(products.renameCategory(anyString(), anyString())).thenAnswer(inv -> {
            String from = inv.getArgument(0);
            String to = inv.getArgument(1);
            int n = 0;
            for (Object[] r : productCounts) {
                if (((String) r[0]).equalsIgnoreCase(from)) {
                    n += ((Number) r[1]).intValue();
                    r[0] = to;
                }
            }
            return n;
        });

        env = new MockEnvironment();
        service = new CategoryService(repo, products, env);
        service.init();
    }

    private ProductCategory byName(String name) {
        return store.stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- khởi tạo

    @Test
    void firstRunSeedsTheDefaultCategories() {
        assertEquals(Product.CATEGORIES, service.names());
        assertEquals(Product.CATEGORIES.size(), store.size());
    }

    @Test
    void seedKeepsLegacyCategoriesAlreadyUsedByProducts() {
        store.clear();
        usedByProducts.add("Outerwear");
        usedByProducts.add("  áo  ");          // trùng "Áo" mặc định (không phân biệt hoa thường) -> không thêm lần nữa
        service = new CategoryService(repo, products, env);
        service.init();

        assertTrue(service.names().contains("Outerwear"));
        assertEquals(Product.CATEGORIES.size() + 1, service.names().size());
    }

    @Test
    void doesNotReseedWhenCategoriesAlreadyExist() {
        service.add("Mũ nón");
        CategoryService again = new CategoryService(repo, products, env);
        again.init();
        assertEquals(Product.CATEGORIES.size() + 1, store.size());
    }

    @Test
    void namesFallBackToDefaultsWhenNothingLoaded() {
        // chưa nạp được gì (chưa gọi init) và tắt tự nạp lại -> dùng danh sách mặc định
        CategoryService notLoaded = new CategoryService(repo, products,
                new MockEnvironment().withProperty("kosis.settings.refresh-seconds", "0"));
        assertEquals(Product.CATEGORIES, notLoaded.names());
        assertEquals(Product.CATEGORIES, notLoaded.menuNames());
    }

    // ---------------------------------------------------------------- thêm

    @Test
    void addAppendsAtTheEndAndIsVisibleInMenu() {
        service.add("  Mũ   nón ");
        assertEquals("Mũ nón", service.names().get(service.names().size() - 1));
        assertTrue(service.menuNames().contains("Mũ nón"));
    }

    @Test
    void addRejectsDuplicatesIgnoringCase() {
        assertThrows(IllegalArgumentException.class, () -> service.add("áo khoác"));
    }

    @Test
    void addRejectsBadNames() {
        assertThrows(IllegalArgumentException.class, () -> service.add("   "));
        assertThrows(IllegalArgumentException.class, () -> service.add("<script>"));
        assertThrows(IllegalArgumentException.class, () -> service.add("a,b"));
        assertThrows(IllegalArgumentException.class, () -> service.add("x".repeat(41)));
        assertEquals(Product.CATEGORIES.size(), store.size());
    }

    @Test
    void addAcceptsVietnameseAndCommonSymbols() {
        service.add("Đồ lót & Vớ (nam)");
        assertTrue(service.names().contains("Đồ lót & Vớ (nam)"));
    }

    @Test
    void addIsLimitedToMaxCategories() {
        for (int i = store.size(); i < CategoryService.MAX_CATEGORIES; i++) {
            service.add("Nhóm " + i);
        }
        assertEquals(CategoryService.MAX_CATEGORIES, store.size());
        assertThrows(IllegalArgumentException.class, () -> service.add("Thêm một nhóm nữa"));
    }

    // ---------------------------------------------------------------- đổi tên

    @Test
    void renameUpdatesProductsAndTheList() {
        productCounts.add(new Object[]{"Giày", 4L});
        long id = byName("Giày").getId();

        int updated = service.rename(id, "Giày dép");

        assertEquals(4, updated);
        verify(products).renameCategory("Giày", "Giày dép");
        assertTrue(service.names().contains("Giày dép"));
        assertFalse(service.names().contains("Giày"));
    }

    @Test
    void renameKeepsPositionAndAllowsChangingOnlyCase() {
        long id = byName("Giày").getId();
        int index = service.names().indexOf("Giày");
        service.rename(id, "GIÀY");
        assertEquals(index, service.names().indexOf("GIÀY"));
    }

    @Test
    void renameToAnExistingNameIsRejected() {
        long id = byName("Giày").getId();
        assertThrows(IllegalArgumentException.class, () -> service.rename(id, "quần"));
        verify(products, never()).renameCategory(anyString(), anyString());
    }

    @Test
    void renameToTheSameNameDoesNothing() {
        long id = byName("Giày").getId();
        assertEquals(0, service.rename(id, "Giày"));
        verify(products, never()).renameCategory(anyString(), anyString());
    }

    // ---------------------------------------------------------------- ẩn / hiện, sắp xếp

    @Test
    void hiddenCategoryLeavesMenuButStaysInNames() {
        long id = byName("Túi").getId();
        service.setVisible(id, false);

        assertFalse(service.menuNames().contains("Túi"));
        assertTrue(service.names().contains("Túi"));

        service.setVisible(id, true);
        assertTrue(service.menuNames().contains("Túi"));
    }

    @Test
    void moveSwapsNeighboursAndStopsAtTheEnds() {
        String first = service.names().get(0);
        String second = service.names().get(1);

        service.move(byName(first).getId(), 1);
        assertEquals(second, service.names().get(0));
        assertEquals(first, service.names().get(1));

        service.move(byName(second).getId(), -1);          // đã ở đầu danh sách: không đổi
        assertEquals(second, service.names().get(0));

        String last = service.names().get(service.names().size() - 1);
        List<String> before = service.names();
        service.move(byName(last).getId(), 1);             // đã ở cuối: không đổi
        assertEquals(before, service.names());
    }

    // ---------------------------------------------------------------- xoá

    @Test
    void cannotDeleteACategoryThatStillHasProducts() {
        productCounts.add(new Object[]{"Giày", 3L});
        long id = byName("Giày").getId();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.delete(id));
        assertTrue(e.getMessage().contains("3"));
        assertTrue(service.names().contains("Giày"));
    }

    @Test
    void deleteWorksWhenNoProductsLeft() {
        long id = byName("Giày").getId();
        service.delete(id);
        assertFalse(service.names().contains("Giày"));
    }

    @Test
    void cannotDeleteTheLastCategory() {
        while (store.size() > 1) {
            service.delete(store.get(store.size() - 1).getId());
        }
        long lastId = store.get(0).getId();
        assertThrows(IllegalArgumentException.class, () -> service.delete(lastId));
        assertEquals(1, store.size());
    }

    @Test
    void unknownIdIsReported() {
        assertThrows(IllegalArgumentException.class, () -> service.delete(9999));
        assertThrows(IllegalArgumentException.class, () -> service.rename(9999, "Mới"));
        assertThrows(IllegalArgumentException.class, () -> service.move(9999, 1));
        assertThrows(IllegalArgumentException.class, () -> service.setVisible(9999, true));
    }

    // ---------------------------------------------------------------- trang quản lý + nhiều máy chủ

    @Test
    void itemsCarryProductCountsIgnoringCase() {
        productCounts.add(new Object[]{"giày", 2L});
        productCounts.add(new Object[]{"Giày", 3L});
        CategoryService.Item shoes = service.items().stream().filter(i -> i.getName().equals("Giày")).findFirst().orElseThrow();
        assertEquals(5, shoes.getProductCount());
    }

    @Test
    void picksUpChangesFromAnotherInstanceAfterRefreshInterval() {
        AtomicLong now = new AtomicLong(1_000_000);
        CategoryService s = new CategoryService(repo, products, env, now::get);
        s.init();
        int before = s.names().size();

        store.add(new ProductCategory("Từ máy khác", 99, true));
        store.get(store.size() - 1).setId(nextId++);

        now.addAndGet(29_000);
        assertEquals(before, s.names().size());
        now.addAndGet(2_000);
        assertEquals(before + 1, s.names().size());
    }
}
