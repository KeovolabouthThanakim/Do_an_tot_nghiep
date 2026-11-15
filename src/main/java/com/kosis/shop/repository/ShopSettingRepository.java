package com.kosis.shop.repository;

import com.kosis.shop.entity.ShopSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingRepository extends JpaRepository<ShopSetting, String> {
}
