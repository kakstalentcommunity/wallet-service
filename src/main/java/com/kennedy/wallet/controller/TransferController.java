package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    private final TransferService transfers;
    public TransferController(TransferService transfers) { this.transfers = transfers; }
    @PostMapping public TransferService.Receipt transfer(Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody TransferRequest request) {
        return transfers.transfer(UUID.fromString(authentication.getName()), idempotencyKey,
                request.amountMinor(), request.receiverPhone());
    }
    public record TransferRequest(@NotBlank String receiverPhone, @Positive long amountMinor) { }
}
