package com.kosis.shop.service;

import com.kosis.shop.entity.Voucher;
import com.kosis.shop.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherServiceTest {

    @Mock
    VoucherRepository repository;

    VoucherService service;

    @BeforeEach
    void setUp() {
        service = new VoucherService(repository);
        when(repository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Voucher voucher(String code) {
        Voucher v = new Voucher();
        v.setCode(code);
        v.setDiscountType(Voucher.FIXED);
        v.setDiscountValue(50_000);
        v.setActive(true);
        return v;
    }

    private IllegalStateException validateFails(String code, long subtotal) {
        return assertThrows(IllegalStateException.class, () -> service.validate(code, subtotal));
    }

    @Test
    void blankCodeIsRejected() {
        assertTrue(validateFails("   ", 100_000).getMessage().contains("nhập mã"));
    }

    @Test
    void unknownCodeIsRejected() {
        when(repository.findByCode("NOPE")).thenReturn(Optional.empty());
        assertTrue(validateFails("nope", 100_000).getMessage().contains("không tồn tại"));
    }

    @Test
    void inactiveCodeIsRejected() {
        Voucher v = voucher("OFF");
        v.setActive(false);
        when(repository.findByCode("OFF")).thenReturn(Optional.of(v));
        assertTrue(validateFails("off", 100_000).getMessage().contains("đã bị tắt"));
    }

    @Test
    void minimumOrderIsEnforced() {
        Voucher v = voucher("MIN");
        v.setMinOrder(300_000);
        when(repository.findByCode("MIN")).thenReturn(Optional.of(v));
        assertTrue(validateFails("MIN", 200_000).getMessage().contains("áp dụng cho đơn từ"));
        assertDoesNotThrow(() -> service.validate("MIN", 300_000));
    }

    @Test
    void exhaustedCodeIsRejected() {
        Voucher v = voucher("FULL");
        v.setUsageLimit(1);
        v.setUsedCount(1);
        when(repository.findByCode("FULL")).thenReturn(Optional.of(v));
        assertTrue(validateFails("FULL", 100_000).getMessage().contains("hết lượt"));
    }

    @Test
    void redeemIncrementsUsageCount() {
        Voucher v = voucher("USE");
        v.setUsedCount(3);
        when(repository.findByCodeForUpdate("USE")).thenReturn(Optional.of(v));
        Voucher result = service.redeem(" use ", 200_000);
        assertEquals(4, result.getUsedCount());
        verify(repository).save(v);
    }

    @Test
    void redeemFailsWhenLimitReachedAndDoesNotCount() {
        Voucher v = voucher("LAST");
        v.setUsageLimit(1);
        v.setUsedCount(1);
        when(repository.findByCodeForUpdate("LAST")).thenReturn(Optional.of(v));
        assertThrows(IllegalStateException.class, () -> service.redeem("LAST", 200_000));
        assertEquals(1, v.getUsedCount());
    }

    @Test
    void releaseGivesBackOneUse() {
        Voucher v = voucher("BACK");
        v.setUsedCount(2);
        when(repository.findByCodeForUpdate("BACK")).thenReturn(Optional.of(v));
        service.release("back");
        assertEquals(1, v.getUsedCount());
    }

    @Test
    void releaseNeverGoesBelowZero() {
        Voucher v = voucher("ZERO");
        v.setUsedCount(0);
        when(repository.findByCodeForUpdate("ZERO")).thenReturn(Optional.of(v));
        service.release("ZERO");
        assertEquals(0, v.getUsedCount());
        verify(repository, never()).save(any(Voucher.class));
    }

    @Test
    void releaseWithoutCodeDoesNothing() {
        service.release(null);
        service.release("  ");
        verify(repository, never()).findByCodeForUpdate(anyString());
    }
}
