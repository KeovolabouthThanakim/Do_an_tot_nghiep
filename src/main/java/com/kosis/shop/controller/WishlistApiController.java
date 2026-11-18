package com.kosis.shop.controller;

import com.kosis.shop.entity.WishlistItem;
import com.kosis.shop.repository.ProductRepository;
import com.kosis.shop.repository.WishlistItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Danh sách yêu thích gắn với tài khoản (lưu trong database) - đổi thiết bị vẫn còn, nhân viên xem được ở hồ sơ khách.
 * Bắt buộc đăng nhập (SecurityConfig). Khách chưa đăng nhập vẫn dùng bản lưu trên trình duyệt (main.js),
 * khi đăng nhập thì được gộp vào tài khoản qua /wishlist/api/merge.
 */
@RestController
@RequestMapping("/wishlist/api")
public class WishlistApiController {

    private static final int MAX_ITEMS = 200;

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;

    public WishlistApiController(WishlistItemRepository wishlistRepository, ProductRepository productRepository) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
    }

    private List<Long> idsOf(String username) {
        return wishlistRepository.findByUsernameOrderByCreatedAtDesc(username).stream().map(WishlistItem::getProductId).toList();
    }

    @GetMapping
    public Map<String, Object> list(Principal principal) {
        return Map.of("ids", idsOf(principal.getName()));
    }

    /** Bật/tắt yêu thích 1 sản phẩm. saved = trạng thái SAU khi bấm. */
    @PostMapping("/toggle")
    public Map<String, Object> toggle(@RequestParam Long productId, Principal principal) {
        String user = principal.getName();
        boolean saved;
        if (wishlistRepository.existsByUsernameAndProductId(user, productId)) {
            wishlistRepository.deleteByUsernameAndProductId(user, productId);
            saved = false;
        } else if (productRepository.existsListedById(productId) && wishlistRepository.countByUsername(user) < MAX_ITEMS) {
            try {
                wishlistRepository.save(new WishlistItem(user, productId));
            } catch (DataIntegrityViolationException ignored) {
                // bấm 2 lần cùng lúc: dòng đã có rồi
            }
            saved = true;
        } else {
            saved = false;
        }
        return Map.of("saved", saved, "ids", idsOf(user));
    }

    /** Gộp danh sách đang lưu trên trình duyệt vào tài khoản, trả về danh sách đầy đủ trên server. */
    @PostMapping("/merge")
    public Map<String, Object> merge(@RequestParam(name = "ids", required = false) List<Long> ids, Principal principal) {
        String user = principal.getName();
        if (ids != null) {
            long have = wishlistRepository.countByUsername(user);
            for (Long id : ids) {
                if (id == null || have >= MAX_ITEMS) continue;
                if (wishlistRepository.existsByUsernameAndProductId(user, id) || !productRepository.existsListedById(id)) continue;
                try {
                    wishlistRepository.save(new WishlistItem(user, id));
                    have++;
                } catch (DataIntegrityViolationException ignored) {
                    // đã có (chạy song song)
                }
            }
        }
        return Map.of("ids", idsOf(user));
    }
}
