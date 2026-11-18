package com.kosis.shop.service;

import com.kosis.shop.entity.MenuLink;
import com.kosis.shop.repository.MenuLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** MenuLinkService với repository giả lập trong bộ nhớ. */
class MenuLinkServiceTest {

    private final List<MenuLink> store = new ArrayList<>();
    private long nextId = 1;
    private MenuLinkService service;

    @BeforeEach
    void setUp() {
        store.clear();
        nextId = 1;
        MenuLinkRepository repo = mock(MenuLinkRepository.class);
        when(repo.findAllByOrderBySortOrderAscIdAsc()).thenAnswer(inv -> {
            List<MenuLink> copy = new ArrayList<>(store);
            copy.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
            return copy;
        });
        when(repo.findById(anyLong())).thenAnswer(inv ->
                store.stream().filter(l -> l.getId().equals(inv.getArgument(0))).findFirst());
        when(repo.save(any(MenuLink.class))).thenAnswer(inv -> {
            MenuLink l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(nextId++);
                store.add(l);
            }
            return l;
        });
        doAnswer(inv -> null).when(repo).saveAll(any());
        doAnswer(inv -> {
            store.remove((MenuLink) inv.getArgument(0));
            return null;
        }).when(repo).delete(any(MenuLink.class));

        service = new MenuLinkService(repo, new MockEnvironment());
        service.init();
    }

    // ---------------------------------------------------------------- địa chỉ liên kết

    @Test
    void acceptsInternalPathsAndHttpLinks() {
        assertEquals("/about", MenuLinkService.cleanUrl("/about"));
        assertEquals("/collections?category=Giày", MenuLinkService.cleanUrl(" /collections?category=Giày "));
        assertEquals("https://facebook.com/kosis", MenuLinkService.cleanUrl("https://facebook.com/kosis"));
        assertEquals("http://example.com", MenuLinkService.cleanUrl("http://example.com"));
    }

    @Test
    void rejectsUnsafeOrMalformedUrls() {
        for (String bad : new String[]{"", "   ", "javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html,x",
                "//evil.com", "/\\evil.com", "\\\\evil.com", "about", "ftp://x.com", "https://", "/a b", "/a\nb"}) {
            assertThrows(IllegalArgumentException.class, () -> MenuLinkService.cleanUrl(bad), "phải từ chối: " + bad);
        }
        assertThrows(IllegalArgumentException.class, () -> MenuLinkService.cleanUrl("/" + "a".repeat(300)));
    }

    @Test
    void labelIsCleanedAndLimited() {
        assertEquals("Bộ sưu tập mới", MenuLinkService.cleanLabel("  Bộ   sưu tập\tmới "));
        assertThrows(IllegalArgumentException.class, () -> MenuLinkService.cleanLabel("  "));
        assertThrows(IllegalArgumentException.class, () -> MenuLinkService.cleanLabel("x".repeat(41)));
    }

    @Test
    void externalLinksAreDetected() {
        assertTrue(new MenuLink("FB", "https://facebook.com", 0, true).isExternal());
        assertFalse(new MenuLink("About", "/about", 0, true).isExternal());
    }

    // ---------------------------------------------------------------- quản lý

    @Test
    void addAppearsInVisibleLinksInOrder() {
        service.add("Giới thiệu", "/about");
        service.add("Facebook", "https://facebook.com/kosis");
        assertEquals(List.of("Giới thiệu", "Facebook"), service.visibleLinks().stream().map(MenuLink::getLabel).toList());
    }

    @Test
    void hiddenLinksAreNotShownInTheMenu() {
        service.add("Giới thiệu", "/about");
        service.setVisible(store.get(0).getId(), false);
        assertTrue(service.visibleLinks().isEmpty());
        assertEquals(1, service.items().size());
    }

    @Test
    void updateChangesLabelAndUrl() {
        service.add("Giới thiệu", "/about");
        service.update(store.get(0).getId(), "Về KOSIS", "/contact");
        assertEquals("Về KOSIS", service.visibleLinks().get(0).getLabel());
        assertEquals("/contact", service.visibleLinks().get(0).getUrl());
        assertThrows(IllegalArgumentException.class, () -> service.update(store.get(0).getId(), "X", "javascript:1"));
    }

    @Test
    void failedUpdateLeavesTheLinkUntouched() {
        service.add("Giới thiệu", "/about");
        long id = store.get(0).getId();
        assertThrows(IllegalArgumentException.class, () -> service.update(id, "Tên mới", "javascript:1"));
        assertEquals("Giới thiệu", store.get(0).getLabel());   // lỗi ở địa chỉ thì tên cũng không bị đổi
        assertEquals("/about", store.get(0).getUrl());
    }

    @Test
    void moveRenumbersSoOrderIsStable() {
        service.add("A", "/a");
        service.add("B", "/b");
        service.move(store.get(1).getId(), -1);          // B lên trên A
        assertEquals(List.of("B", "A"), service.items().stream().map(MenuLink::getLabel).toList());
    }

    @Test
    void deleteRemovesTheLink() {
        service.add("A", "/a");
        service.delete(store.get(0).getId());
        assertTrue(service.items().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.delete(12345));
    }

    @Test
    void addIsLimited() {
        for (int i = 0; i < MenuLinkService.MAX_LINKS; i++) {
            service.add("L" + i, "/l" + i);
        }
        assertThrows(IllegalArgumentException.class, () -> service.add("Thừa", "/x"));
    }
}
