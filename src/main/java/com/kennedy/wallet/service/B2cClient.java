package com.kennedy.wallet.service;

import java.util.concurrent.CompletionStage;

public interface B2cClient {
    CompletionStage<Result> submit(String phone, long amountMinor, String reference);
    record Result(boolean successful, String providerReference) { }
}
