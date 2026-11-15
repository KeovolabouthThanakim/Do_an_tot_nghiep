package com.kosis.shop.service;

import com.kosis.shop.entity.PasswordResetToken;
import com.kosis.shop.entity.User;
import com.kosis.shop.repository.PasswordResetTokenRepository;
import com.kosis.shop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock
    PasswordResetTokenRepository tokenRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    EmailService emailService;

    PasswordResetService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokenRepository, userRepository, passwordEncoder, emailService);
        user = new User();
        user.setId(3L);
        user.setUsername("an");
        user.setEmail("an@example.com");
        user.setPassword("OLD");
        user.setEnabled(true);
        when(userRepository.findByEmailIgnoreCase("an@example.com")).thenReturn(List.of(user));
        when(userRepository.findByUsername("an")).thenReturn(Optional.of(user));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("ENCODED");
        when(tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.empty());
    }

    /** Gửi yêu cầu, trả về mã gốc nằm trong link của email + token đã lưu */
    private String[] requestAndCapture(String identifier) {
        service.requestReset(identifier);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("an@example.com"), anyString(), body.capture());
        String text = body.getValue();
        int i = text.indexOf("token=") + "token=".length();
        int end = text.indexOf('\n', i);
        String raw = text.substring(i, end < 0 ? text.length() : end).trim();

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        when(tokenRepository.findByTokenHash(saved.getValue().getTokenHash())).thenReturn(Optional.of(saved.getValue()));
        return new String[]{raw, saved.getValue().getTokenHash()};
    }

    @Test
    void knownEmailGetsALinkAndOnlyTheHashIsStored() {
        String[] r = requestAndCapture("an@example.com");
        assertFalse(r[0].isEmpty());
        assertNotEquals(r[0], r[1]);          // trong DB không lưu mã gốc
        assertEquals(64, r[1].length());      // SHA-256 dạng hex
    }

    @Test
    void usernameWorksToo() {
        service.requestReset("an");
        verify(emailService).send(eq("an@example.com"), anyString(), anyString());
    }

    @Test
    void unknownEmailSendsNothing() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(List.of());
        service.requestReset("ghost@example.com");
        verify(emailService, never()).send(anyString(), anyString(), anyString());
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void blankInputSendsNothing() {
        service.requestReset("   ");
        service.requestReset(null);
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void disabledAccountGetsNoEmail() {
        user.setEnabled(false);
        service.requestReset("an@example.com");
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void accountWithoutEmailGetsNoEmail() {
        user.setEmail("");
        service.requestReset("an");
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void secondRequestWithinAMinuteIsThrottled() {
        PasswordResetToken recent = new PasswordResetToken(3L, "h", LocalDateTime.now().plusMinutes(29));
        when(tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(recent));
        service.requestReset("an@example.com");
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void validTokenResetsThePasswordOnce() {
        String[] r = requestAndCapture("an@example.com");
        assertTrue(service.isValid(r[0]));

        assertTrue(service.resetPassword(r[0], "newSecret1"));

        assertEquals("ENCODED", user.getPassword());
        verify(userRepository).save(user);
        verify(tokenRepository, atLeastOnce()).deleteByUserId(3L);   // mã bị huỷ sau khi dùng
    }

    @Test
    void wrongTokenIsRejected() {
        requestAndCapture("an@example.com");
        assertFalse(service.isValid("not-the-token"));
        assertFalse(service.resetPassword("not-the-token", "newSecret1"));
        assertEquals("OLD", user.getPassword());
    }

    @Test
    void emptyTokenIsRejected() {
        assertFalse(service.isValid(""));
        assertFalse(service.isValid(null));
        assertFalse(service.resetPassword("  ", "newSecret1"));
    }

    @Test
    void expiredTokenIsRejected() {
        String[] r = requestAndCapture("an@example.com");
        PasswordResetToken expired = new PasswordResetToken(3L, r[1], LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(r[1])).thenReturn(Optional.of(expired));
        assertFalse(service.isValid(r[0]));
        assertFalse(service.resetPassword(r[0], "newSecret1"));
        assertEquals("OLD", user.getPassword());
    }

    @Test
    void tokenOfDisabledAccountIsRejected() {
        String[] r = requestAndCapture("an@example.com");
        user.setEnabled(false);
        assertFalse(service.resetPassword(r[0], "newSecret1"));
        assertEquals("OLD", user.getPassword());
    }
}
