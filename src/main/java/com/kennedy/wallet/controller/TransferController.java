package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {
    private final TransferService transfers;
    public TransferController(TransferService transfers) { this.transfers = transfers; }
    @PostMapping @Operation(summary = "Transfer funds to another wallet")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Transfer receipt"), @ApiResponse(responseCode = "400", description = "Invalid request, limits, or insufficient funds"), @ApiResponse(responseCode = "401", description = "Authentication required"), @ApiResponse(responseCode = "403", description = "Risk controls blocked the transfer"), @ApiResponse(responseCode = "404", description = "Recipient is unavailable"), @ApiResponse(responseCode = "422", description = "Idempotency key was reused with a different request")})
    public TransferService.Receipt transfer(Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody TransferRequest request) {
        return transfers.transfer(UUID.fromString(authentication.getName()), idempotencyKey,
                request.amountMinor(), request.receiverPhone());
    }
    public record TransferRequest(@NotBlank String receiverPhone, @Positive long amountMinor) { }
}
