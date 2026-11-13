package com.kennedy.wallet.service;

import com.kennedy.wallet.config.WalletProperties;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.util.Comparator;

@Service
public class FeeService {
    private final WalletProperties.Transfer config;
    public FeeService(WalletProperties properties) {
        config = properties.getTransfer();
        if (config.getFees() == null || config.getFees().isEmpty()) throw new IllegalStateException("At least one transfer fee tier is required");
        long prior = 0;
        for (var tier : config.getFees().stream().sorted(Comparator.comparingLong(WalletProperties.FeeTier::getUpToMinor)).toList()) {
            if (tier.getUpToMinor() <= prior || tier.getFlatFeeMinor() < 0 || tier.getBasisPoints() < 0 || tier.getBasisPoints() > 10_000)
                throw new IllegalStateException("Transfer fee tiers must have ascending thresholds and nonnegative fees");
            prior = tier.getUpToMinor();
        }
        if (prior < config.getMaximumAmountMinor()) throw new IllegalStateException("A transfer fee tier must cover the configured maximum amount");
    }

    /** Returns the configured flat plus proportional fee in minor units. */
    public long feeFor(long amountMinor) {
        if (amountMinor <= 0) throw new IllegalArgumentException("Transfer amount must be positive");
        var tier = config.getFees().stream().filter(fee -> amountMinor <= fee.getUpToMinor())
                .min(Comparator.comparingLong(WalletProperties.FeeTier::getUpToMinor))
                .orElseThrow(() -> new IllegalStateException("No fee tier covers the configured transfer amount"));
        long proportional = BigInteger.valueOf(amountMinor).multiply(BigInteger.valueOf(tier.getBasisPoints()))
                .divide(BigInteger.valueOf(10_000)).longValueExact();
        return Math.addExact(tier.getFlatFeeMinor(), proportional);
    }
}
