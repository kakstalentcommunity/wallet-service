package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.DepositService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/deposits")
public class DepositController {
    private final DepositService deposits;
    public DepositController(DepositService deposits){this.deposits=deposits;}
    @PostMapping("/mpesa") public DepositService.Receipt start(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DepositRequest request,Authentication authentication){
        return deposits.start(UUID.fromString(authentication.getName()),key,request.phone(),request.amountMinor());
    }
    public record DepositRequest(@NotBlank String phone,@Positive long amountMinor){ }
}
