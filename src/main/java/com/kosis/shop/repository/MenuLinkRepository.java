package com.kosis.shop.repository;

import com.kosis.shop.entity.MenuLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuLinkRepository extends JpaRepository<MenuLink, Long> {

    List<MenuLink> findAllByOrderBySortOrderAscIdAsc();
}
