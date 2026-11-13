package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.WithdrawalService;
import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;import io.swagger.v3.oas.annotations.security.SecurityRequirement;import io.swagger.v3.oas.annotations.responses.ApiResponse;import io.swagger.v3.oas.annotations.responses.ApiResponses;import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/withdrawals")
@Tag(name="Withdrawals") @SecurityRequirement(name="bearerAuth")
public class WithdrawalController {
    private final WithdrawalService withdrawals;public WithdrawalController(WithdrawalService withdrawals){this.withdrawals=withdrawals;}
    @PostMapping @Operation(summary="Request a wallet withdrawal to M-Pesa") @ApiResponses({@ApiResponse(responseCode="200",description="Pending withdrawal receipt"),@ApiResponse(responseCode="400",description="Invalid phone, amount, or insufficient funds"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="422",description="Idempotency key conflict")}) public WithdrawalService.Receipt withdraw(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody WithdrawalRequest request,Authentication authentication){return withdrawals.withdraw(UUID.fromString(authentication.getName()),key,request.phone(),request.amountMinor());}
    public record WithdrawalRequest(@NotBlank String phone,@Positive long amountMinor){ }
}
