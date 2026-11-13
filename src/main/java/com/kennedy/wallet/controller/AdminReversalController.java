package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.AdminReversalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/admin/transactions")
public class AdminReversalController {
    private final AdminReversalService reversals;public AdminReversalController(AdminReversalService reversals){this.reversals=reversals;}
    @PostMapping("/{transactionId}/reverse") @PreAuthorize("hasRole('ADMIN')")
    public AdminReversalService.Receipt reverse(@PathVariable UUID transactionId,@RequestHeader("Idempotency-Key") String key,Authentication authentication){
        return reversals.reverse(UUID.fromString(authentication.getName()),key,transactionId);
    }
}
