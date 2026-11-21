package com.kosis.shop.service;

import com.kosis.shop.entity.ShopSetting;
import com.kosis.shop.repository.ShopSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SettingsService với repository giả lập trong bộ nhớ (không cần database).
 */
class SettingsServiceTest {

    private final List<ShopSetting> store = new ArrayList<>();
    private ShopSettingRepository repo;
    private MockEnvironment env;
    private SettingsService service;

    @BeforeEach
    void setUp() {
        store.clear();
        repo = mock(ShopSettingRepository.class);
        when(repo.findAll()).thenAnswer(inv -> new ArrayList<>(store));
        doAnswer(inv -> {
            Iterable<ShopSetting> items = inv.getArgument(0);
            for (ShopSetting s : items) {
                store.removeIf(x -> x.getKey().equals(s.getKey()));
                store.add(s);
            }
            return null;
        }).when(repo).saveAll(any());
        doAnswer(inv -> {
            Iterable<String> keys = inv.getArgument(0);
            for (String k : keys) {
                store.removeIf(x -> x.getKey().equals(k));
            }
            return null;
        }).when(repo).deleteAllById(any());
        doAnswer(inv -> {
            store.clear();
            return null;
        }).when(repo).deleteAll();

        env = new MockEnvironment()
                .withProperty("shop.shipping.fee", "30000")
                .withProperty("shop.shipping.free-threshold", "500000")
                .withProperty("kosis.returns.window-days", "7")
                .withProperty("kosis.contact.email", "hello@kosis.example")
                .withProperty("kosis.contact.address", "Dia chi mac dinh")
                // application.properties: seller-address mặc định mượn địa chỉ liên hệ
                .withProperty("kosis.invoice.seller-address", "Dia chi mac dinh");
        service = new SettingsService(repo, env);
        service.reload();
    }

    // ---------------------------------------------------------------- đọc / thứ tự ưu tiên

    @Test
    void usesPropertyDefaultWhenNothingIsOverridden() {
        assertEquals(30_000, service.getLong(SettingsService.SHIPPING_FEE));
        assertEquals(7, service.getInt(SettingsService.RETURNS_WINDOW_DAYS));
        assertFalse(service.isCustomized(SettingsService.SHIPPING_FEE));
    }

    @Test
    void fallsBackToBuiltInDefaultWhenPropertyIsMissing() {
        // không khai báo kosis.listing.page-size trong env -> dùng giá trị dự phòng 20
        assertEquals(20, service.getInt(SettingsService.LISTING_PAGE_SIZE));
    }

    @Test
    void savedOverrideWinsOverProperty() {
        store.add(new ShopSetting(SettingsService.SHIPPING_FEE, "45000", "admin"));
        service.reload();
        assertEquals(45_000, service.getLong(SettingsService.SHIPPING_FEE));
        assertTrue(service.isCustomized(SettingsService.SHIPPING_FEE));
    }

    @Test
    void garbageOverrideFallsBackToDefault() {
        store.add(new ShopSetting(SettingsService.SHIPPING_FEE, "abc", "admin"));
        service.reload();
        assertEquals(30_000, service.getLong(SettingsService.SHIPPING_FEE));
    }

    @Test
    void unknownKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.get("khong.ton.tai"));
    }

    // ---------------------------------------------------------------- lưu

    @Test
    void saveStoresChangedValueAndAppliesImmediately() {
        SettingsService.SaveResult r = service.save(Map.of(SettingsService.SHIPPING_FEE, "45000"), "admin");

        assertTrue(r.isOk());
        assertEquals(1, r.getChanged());
        assertEquals(45_000, service.getLong(SettingsService.SHIPPING_FEE));
        assertEquals(1, store.size());
        assertEquals("admin", store.get(0).getUpdatedBy());
        assertTrue(service.lastChange().isPresent());
    }

    @Test
    void saveAcceptsVietnameseThousandsSeparators() {
        SettingsService.SaveResult r = service.save(Map.of(SettingsService.SHIPPING_FEE, "45.000"), "admin");
        assertTrue(r.isOk());
        assertEquals(45_000, service.getLong(SettingsService.SHIPPING_FEE));
    }

    @Test
    void unchangedValuesAreNotWritten() {
        SettingsService.SaveResult r = service.save(Map.of(
                SettingsService.SHIPPING_FEE, "30000",
                SettingsService.SHIPPING_FREE_THRESHOLD, "500.000",
                SettingsService.CONTACT_EMAIL, "  hello@kosis.example  "), "admin");

        assertTrue(r.isOk());
        assertEquals(0, r.getChanged());
        verify(repo, never()).saveAll(any());
        verify(repo, never()).deleteAllById(any());
        assertTrue(store.isEmpty());
    }

    @Test
    void settingBackToDefaultRemovesTheOverride() {
        service.save(Map.of(SettingsService.SHIPPING_FEE, "45000"), "admin");
        assertTrue(service.isCustomized(SettingsService.SHIPPING_FEE));

        SettingsService.SaveResult r = service.save(Map.of(SettingsService.SHIPPING_FEE, "30000"), "admin");

        assertTrue(r.isOk());
        assertEquals(1, r.getChanged());
        assertFalse(service.isCustomized(SettingsService.SHIPPING_FEE));
        assertTrue(store.isEmpty());
        assertEquals(30_000, service.getLong(SettingsService.SHIPPING_FEE));
    }

    @Test
    void anyInvalidFieldMeansNothingIsSaved() {
        SettingsService.SaveResult r = service.save(Map.of(
                SettingsService.SHIPPING_FEE, "45000",           // hợp lệ
                SettingsService.RETURNS_WINDOW_DAYS, "0"), "admin"); // sai: tối thiểu 1 ngày

        assertFalse(r.isOk());
        assertTrue(r.getFieldErrors().containsKey(SettingsService.RETURNS_WINDOW_DAYS));
        assertFalse(r.getFieldErrors().containsKey(SettingsService.SHIPPING_FEE));
        verify(repo, never()).saveAll(any());
        assertTrue(store.isEmpty());
        assertEquals(30_000, service.getLong(SettingsService.SHIPPING_FEE));
    }

    @Test
    void numbersAreRangeChecked() {
        assertFalse(service.save(Map.of(SettingsService.SHIPPING_FEE, "-5"), "a").isOk());
        assertFalse(service.save(Map.of(SettingsService.SHIPPING_FEE, "abc"), "a").isOk());
        assertFalse(service.save(Map.of(SettingsService.SHIPPING_FEE, "99999999999"), "a").isOk());
        assertFalse(service.save(Map.of(SettingsService.RETURNS_WINDOW_DAYS, "61"), "a").isOk());
        assertFalse(service.save(Map.of(SettingsService.LISTING_PAGE_SIZE, "3"), "a").isOk());
        assertFalse(service.save(Map.of(SettingsService.SHIPPING_AUTO_COMPLETE_DAYS, "31"), "a").isOk());

        assertTrue(service.save(Map.of(SettingsService.RETURNS_WINDOW_DAYS, "60"), "a").isOk());
        assertEquals(60, service.getInt(SettingsService.RETURNS_WINDOW_DAYS));
    }

    @Test
    void zeroIsAllowedForShippingFeeAndThreshold() {
        assertTrue(service.save(Map.of(
                SettingsService.SHIPPING_FEE, "0",
                SettingsService.SHIPPING_FREE_THRESHOLD, "0"), "a").isOk());
        assertEquals(0, service.getLong(SettingsService.SHIPPING_FEE));
        assertEquals(0, service.getLong(SettingsService.SHIPPING_FREE_THRESHOLD));
    }

    @Test
    void requiredTextCannotBeBlank() {
        SettingsService.SaveResult r = service.save(Map.of(SettingsService.CONTACT_EMAIL, "   "), "admin");
        assertFalse(r.isOk());
        assertTrue(r.getFieldErrors().containsKey(SettingsService.CONTACT_EMAIL));
    }

    @Test
    void emailPhoneAndTaxCodeAreValidated() {
        assertFalse(service.save(Map.of(SettingsService.CONTACT_EMAIL, "khong-phai-email"), "a").isOk());
        assertFalse(service.save(Map.of(SettingsService.CONTACT_PHONE, "abc"), "a").isOk());
        assertFalse(service.save(Map.of(SettingsService.INVOICE_TAX_CODE, "12345"), "a").isOk());

        assertTrue(service.save(Map.of(SettingsService.CONTACT_EMAIL, "shop@kosis.vn"), "a").isOk());
        assertTrue(service.save(Map.of(SettingsService.CONTACT_PHONE, "1900 1234"), "a").isOk());
        assertTrue(service.save(Map.of(SettingsService.INVOICE_TAX_CODE, "0123456789"), "a").isOk());
        assertTrue(service.save(Map.of(SettingsService.INVOICE_TAX_CODE, "0123456789-001"), "a").isOk());
    }

    @Test
    void controlCharactersAreStripped() {
        service.save(Map.of(SettingsService.CONTACT_HOURS, "Thu 2\r\nThu 7"), "admin");
        assertEquals("Thu 2  Thu 7", service.get(SettingsService.CONTACT_HOURS));
    }

    @Test
    void optionalFieldCanBeBlankedEvenWhenPropertyHasAValue() {
        env.setProperty("kosis.contact.notify-email", "boss@kosis.vn");
        assertEquals("boss@kosis.vn", service.get(SettingsService.CONTACT_NOTIFY_EMAIL));

        SettingsService.SaveResult r = service.save(Map.of(SettingsService.CONTACT_NOTIFY_EMAIL, ""), "admin");

        assertTrue(r.isOk());
        assertEquals("", service.get(SettingsService.CONTACT_NOTIFY_EMAIL));   // cố ý để trống, không quay về giá trị trong properties
        assertTrue(service.isCustomized(SettingsService.CONTACT_NOTIFY_EMAIL));
    }

    // ---------------------------------------------------------------- địa chỉ hoá đơn

    @Test
    void sellerAddressFollowsShopAddressUnlessSetSeparately() {
        assertEquals("Dia chi mac dinh", service.sellerAddress());

        service.save(Map.of(SettingsService.CONTACT_ADDRESS, "Dia chi moi"), "admin");
        assertEquals("Dia chi moi", service.sellerAddress());          // đi theo địa chỉ cửa hàng mới

        service.save(Map.of(SettingsService.INVOICE_ADDRESS, "Kho rieng"), "admin");
        assertEquals("Kho rieng", service.sellerAddress());            // đặt riêng thì dùng địa chỉ riêng
    }

    // ---------------------------------------------------------------- đặt lại

    @Test
    void resetAllRestoresDefaults() {
        service.save(Map.of(
                SettingsService.SHIPPING_FEE, "45000",
                SettingsService.RETURNS_WINDOW_DAYS, "14"), "admin");

        assertEquals(2, service.resetAll("admin"));

        assertEquals(30_000, service.getLong(SettingsService.SHIPPING_FEE));
        assertEquals(7, service.getInt(SettingsService.RETURNS_WINDOW_DAYS));
        assertTrue(service.customizedKeys().isEmpty());
        assertFalse(service.lastChange().isPresent());
    }

    // ---------------------------------------------------------------- nhiều máy chủ: tự nạp lại định kỳ

    @Test
    void picksUpChangesMadeByAnotherInstanceAfterRefreshInterval() {
        AtomicLong now = new AtomicLong(1_000_000);
        SettingsService s = new SettingsService(repo, env, now::get);
        s.reload();
        assertEquals(30_000, s.getLong(SettingsService.SHIPPING_FEE));

        // một máy chủ KHÁC ghi thẳng vào DB
        store.add(new ShopSetting(SettingsService.SHIPPING_FEE, "45000", "other-node"));

        now.addAndGet(29_000);                                        // chưa tới hạn nạp lại -> vẫn dùng bản trong bộ nhớ
        assertEquals(30_000, s.getLong(SettingsService.SHIPPING_FEE));

        now.addAndGet(2_000);                                         // quá 30 giây -> tự nạp lại
        assertEquals(45_000, s.getLong(SettingsService.SHIPPING_FEE));
        assertTrue(s.isCustomized(SettingsService.SHIPPING_FEE));
    }

    @Test
    void refreshIntervalIsConfigurableAndCanBeDisabled() {
        env.setProperty("kosis.settings.refresh-seconds", "0");
        AtomicLong now = new AtomicLong(1_000_000);
        SettingsService s = new SettingsService(repo, env, now::get);
        s.reload();

        store.add(new ShopSetting(SettingsService.SHIPPING_FEE, "45000", "other-node"));
        now.addAndGet(10_000_000);                                    // dù qua bao lâu cũng không tự nạp lại
        assertEquals(30_000, s.getLong(SettingsService.SHIPPING_FEE));

        s.reload();                                                   // chỉ khi nạp tay
        assertEquals(45_000, s.getLong(SettingsService.SHIPPING_FEE));
    }

    @Test
    void failedRefreshKeepsLastKnownValuesAndDoesNotRetryEveryCall() {
        AtomicLong now = new AtomicLong(1_000_000);
        store.add(new ShopSetting(SettingsService.SHIPPING_FEE, "45000", "admin"));
        SettingsService s = new SettingsService(repo, env, now::get);
        s.reload();

        clearInvocations(repo);                                            // chỉ đếm các lần nạp từ đây trở đi
        when(repo.findAll()).thenThrow(new RuntimeException("db down"));
        now.addAndGet(31_000);

        assertEquals(45_000, s.getLong(SettingsService.SHIPPING_FEE));   // giữ giá trị cũ
        assertEquals(45_000, s.getLong(SettingsService.SHIPPING_FEE));
        verify(repo, times(1)).findAll();                                  // thử nạp 1 lần; lần đọc thứ hai chưa tới hạn nên không gọi DB lại
    }

    // ---------------------------------------------------------------- nối với PricingService

    @Test
    void pricingServiceFollowsAdminSettings() {
        PricingService pricing = PricingService.of(30_000, 500_000);
        ReflectionTestUtils.setField(pricing, "settings", service);

        assertEquals(30_000, pricing.shippingFor(100_000));

        service.save(Map.of(
                SettingsService.SHIPPING_FEE, "45000",
                SettingsService.SHIPPING_FREE_THRESHOLD, "200000"), "admin");

        assertEquals(45_000, pricing.shippingFor(100_000));
        assertEquals(0, pricing.shippingFor(200_000));
        assertEquals(100_000, pricing.remainingForFreeShipping(100_000));
    }

    @Test
    void pricingServiceWithoutSettingsKeepsItsOwnValues() {
        PricingService pricing = PricingService.of(30_000, 500_000);
        assertEquals(30_000, pricing.getShippingFee());
        assertEquals(500_000, pricing.getFreeThreshold());
    }
}
