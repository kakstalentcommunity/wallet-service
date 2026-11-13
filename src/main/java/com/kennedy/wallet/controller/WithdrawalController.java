package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.WithdrawalService;
import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.util.UUID;

@RestController @RequestMapping("/api/withdrawals")
public class WithdrawalController {
    private final WithdrawalService withdrawals;public WithdrawalController(WithdrawalService withdrawals){this.withdrawals=withdrawals;}
    @PostMapping public WithdrawalService.Receipt withdraw(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody WithdrawalRequest request,Authentication authentication){return withdrawals.withdraw(UUID.fromString(authentication.getName()),key,request.phone(),request.amountMinor());}
    public record WithdrawalRequest(@NotBlank String phone,@Positive long amountMinor){ }
}
