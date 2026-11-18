package com.kosis.shop.controller;

import com.kosis.shop.cart.Cart;
import com.kosis.shop.entity.Order;
import com.kosis.shop.repository.ContactMessageRepository;
import com.kosis.shop.entity.ReturnRequest;
import com.kosis.shop.repository.OrderRepository;
import com.kosis.shop.repository.ReturnRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Dữ liệu dùng chung cho MỌI trang:
 *  - cartCount: số nhỏ trên icon giỏ hàng (đọc từ giỏ trong session)
 *  - pendingOrderCount: số đơn đang chờ nhân viên/admin kiểm tra (hiện ở sidebar dashboard; khách luôn = 0)
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final OrderRepository orderRepository;
    private final ContactMessageRepository contactRepository;
    private final ReturnRequestRepository returnRepository;
    private final com.kosis.shop.service.PricingService pricingService;
    private final com.kosis.shop.service.ReturnService returnService;
    private final com.kosis.shop.service.CategoryService categoryService;
    private final com.kosis.shop.service.MenuLinkService menuLinkService;

    public GlobalModelAdvice(OrderRepository orderRepository, ContactMessageRepository contactRepository,
                             ReturnRequestRepository returnRepository, com.kosis.shop.service.PricingService pricingService,
                             com.kosis.shop.service.ReturnService returnService,
                             com.kosis.shop.service.CategoryService categoryService,
                             com.kosis.shop.service.MenuLinkService menuLinkService) {
        this.categoryService = categoryService;
        this.menuLinkService = menuLinkService;
        this.returnService = returnService;
        this.pricingService = pricingService;
        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
        this.contactRepository = contactRepository;
    }

    /** Số tin nhắn liên hệ chưa xử lý (sidebar dashboard; khách luôn = 0) */
    @ModelAttribute("openContactCount")
    public int openContactCount(Authentication auth) {
        if (auth == null) return 0;
        boolean staff = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_NHANVIEN"));
        if (!staff) return 0;
        return (int) contactRepository.countByHandledFalse();
    }

    /** Số yêu cầu trả hàng đang chờ xử lý (sidebar dashboard; khách luôn = 0) */
    @ModelAttribute("pendingReturnCount")
    public int pendingReturnCount(Authentication auth) {
        if (auth == null) return 0;
        boolean staff = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_NHANVIEN"));
        if (!staff) return 0;
        return (int) returnRepository.countByStatusIn(List.of(ReturnRequest.REQUESTED, ReturnRequest.APPROVED));
    }

    /** Chính sách phí vận chuyển (thanh thông báo, giỏ hàng, trang sản phẩm đọc từ đây - đổi ở application.properties) */
    @ModelAttribute("pricing")
    public com.kosis.shop.service.PricingService pricing() {
        return pricingService;
    }

    /** Số ngày được đổi trả (hiện ở thanh thông báo, giỏ hàng, trang sản phẩm, hoá đơn - chỉnh ở /dashboard/cai-dat) */
    @ModelAttribute("returnWindowDays")
    public int returnWindowDays() {
        return returnService.getWindowDays();
    }

    /** Danh mục hiện trên thanh menu (do ADMIN quản lý ở /dashboard/cai-dat/menu) */
    @ModelAttribute("menuCategories")
    public List<String> menuCategories() {
        return categoryService.menuNames();
    }

    /** Liên kết thêm trên thanh menu (do ADMIN quản lý) */
    @ModelAttribute("menuLinks")
    public List<com.kosis.shop.entity.MenuLink> menuLinks() {
        return menuLinkService.visibleLinks();
    }

    @ModelAttribute("cartCount")
    public int cartCount(HttpServletRequest request) {
        return Cart.countIn(request.getSession(false));
    }

    /** Số đơn đã xác nhận đang chờ giao cho đơn vị vận chuyển (sidebar dashboard; khách luôn = 0) */
    @ModelAttribute("pendingShipCount")
    public int pendingShipCount(Authentication auth) {
        if (auth == null) return 0;
        boolean staff = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_NHANVIEN"));
        if (!staff) return 0;
        return (int) orderRepository.countAwaitingShipment(Order.STATUS_CONFIRMED, Order.SHIP_PREPARING);
    }

    @ModelAttribute("pendingOrderCount")
    public int pendingOrderCount(Authentication auth) {
        if (auth == null) return 0;
        boolean staff = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_NHANVIEN"));
        if (!staff) return 0;
        return (int) orderRepository.countByStatusIn(List.of(Order.STATUS_PENDING, Order.STATUS_PAYMENT_REVIEW));
    }
}
