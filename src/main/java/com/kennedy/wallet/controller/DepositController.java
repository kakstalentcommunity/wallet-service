package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.DepositService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/deposits")
@Tag(name = "Deposits")
@SecurityRequirement(name = "bearerAuth")
public class DepositController {
    private final DepositService deposits;
    public DepositController(DepositService deposits){this.deposits=deposits;}
    @PostMapping("/mpesa") @Operation(summary = "Start an M-Pesa STK Push deposit")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Deposit request receipt"), @ApiResponse(responseCode = "400", description = "Invalid phone, amount, or idempotency key"), @ApiResponse(responseCode = "401", description = "Authentication required"), @ApiResponse(responseCode = "422", description = "Idempotency key conflict")})
    public DepositService.Receipt start(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DepositRequest request,Authentication authentication){
        return deposits.start(UUID.fromString(authentication.getName()),key,request.phone(),request.amountMinor());
    }
    public record DepositRequest(@NotBlank String phone,@Positive long amountMinor){ }
}
