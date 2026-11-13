package com.kennedy.wallet;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.domain.User;
import com.kennedy.wallet.service.JwtService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    @Test void issuedTokenCarriesValidatedIssuerAudienceExpiryAndJti() {
        WalletProperties properties = new WalletProperties();
        properties.getJwt().setSecret("test-key-with-more-than-32-bytes-long-for-hmac");
        JwtService service = new JwtService(properties);
        String token = service.issue(new User("+254712345678", "encoded"));

        var claims = service.parse(token);
        assertEquals("wallet-service", claims.getIssuer());
        assertTrue(claims.getAudience().contains("wallet-api"));
        assertNotNull(claims.getExpiration());
        assertNotNull(claims.getId());
        assertFalse(claims.getId().isBlank());
    }
}
