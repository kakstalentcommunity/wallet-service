package com.kennedy.wallet;

import com.kennedy.wallet.domain.User;
import com.kennedy.wallet.repository.AccountRepository;
import com.kennedy.wallet.repository.RefreshTokenRepository;
import com.kennedy.wallet.repository.UserRepository;
import com.kennedy.wallet.service.AuthService;
import com.kennedy.wallet.service.JwtService;
import com.kennedy.wallet.service.FraudService;
import com.kennedy.wallet.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock AccountRepository accounts;
    @Mock RefreshTokenRepository tokens;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock FraudService fraud;
    @Mock AuditService audit;
    AuthService service;

    @BeforeEach void setUp() { service = new AuthService(users, accounts, tokens, encoder, jwt, fraud, audit); org.mockito.Mockito.lenient().when(fraud.evaluateFailedLogin(any())).thenReturn(new FraudService.Decision(FraudService.Outcome.ALLOW,java.util.List.of())); }

    @Test void registerNormalizesPhoneAndCreatesWalletAccount() {
        when(users.findByPhone("+254712345678")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("encoded");
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User registered = service.register("0712345678", "LongPass123");

        assertEquals("+254712345678", registered.getPhone());
        verify(accounts).save(any());
    }

    @Test void loginUsesSamePublicErrorForUnknownAndIncorrectCredentials() {
        when(users.findByPhone("+254712345678")).thenReturn(Optional.empty());
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.login("0712345678", "BadPassword"));
        assertEquals("Invalid credentials", error.getMessage());
        verify(encoder).matches(anyString(), anyString());
    }

    @Test void fifthConsecutiveFailureLocksAccountForAtLeastFifteenMinutes() {
        User user = new User("+254712345678", "encoded-password");
        when(users.findByPhone(user.getPhone())).thenReturn(Optional.of(user));
        when(encoder.matches(anyString(), eq("encoded-password"))).thenReturn(false);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(IllegalArgumentException.class, () -> service.login(user.getPhone(), "BadPassword"));
        }
        assertTrue(user.getLockedUntil().isAfter(Instant.now().plusSeconds(14 * 60)));
    }

    @Test void registrationRejectsPasswordsOutsideSupportedLength() {
        assertThrows(IllegalArgumentException.class, () -> service.register("0712345678", "short"));
        verifyNoInteractions(users, encoder, accounts);
    }
}
