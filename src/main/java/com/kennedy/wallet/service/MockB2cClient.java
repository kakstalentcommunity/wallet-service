package com.kennedy.wallet.service;

import com.kennedy.wallet.config.WalletProperties;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Asynchronous local substitute for the not-yet-integrated Daraja B2C API. */
@Component
public class MockB2cClient implements B2cClient {
    private final WalletProperties.B2c settings;
    public MockB2cClient(WalletProperties properties){settings=properties.getB2c();}
    @Override public CompletableFuture<Result> submit(String phone,long amountMinor,String reference){
        return CompletableFuture.supplyAsync(()->new Result(settings.isMockSuccess(),"MOCK-"+reference),
                CompletableFuture.delayedExecutor(Math.max(0,settings.getMockDelayMillis()), TimeUnit.MILLISECONDS));
    }
}
