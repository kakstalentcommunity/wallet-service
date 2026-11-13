package com.kennedy.wallet;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.service.FeeService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeeServiceTest {
    @Test void appliesConfiguredTierAndBasisPointFee() {
        WalletProperties properties = new WalletProperties();
        properties.getTransfer().setFees(java.util.List.of(
                new WalletProperties.FeeTier(10_000, 50, 100),
                new WalletProperties.FeeTier(150_000_000, 100, 200)));
        FeeService fees = new FeeService(properties);
        assertEquals(150, fees.feeFor(10_000));
        assertEquals(200_100, fees.feeFor(10_000_000));
    }

    @Test void rejectsNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> new FeeService(new WalletProperties()).feeFor(0));
    }
}
