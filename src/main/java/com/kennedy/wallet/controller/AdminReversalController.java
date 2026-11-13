package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.AdminReversalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;import io.swagger.v3.oas.annotations.security.SecurityRequirement;import io.swagger.v3.oas.annotations.responses.ApiResponse;import io.swagger.v3.oas.annotations.responses.ApiResponses;import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/admin/transactions")
@Tag(name="Administration") @SecurityRequirement(name="bearerAuth")
public class AdminReversalController {
    private final AdminReversalService reversals;public AdminReversalController(AdminReversalService reversals){this.reversals=reversals;}
    @PostMapping("/{transactionId}/reverse") @PreAuthorize("hasRole('ADMIN')") @Operation(summary="Reverse a completed transaction")
    @ApiResponses({@ApiResponse(responseCode="200",description="Reversal receipt"),@ApiResponse(responseCode="400",description="Transaction cannot be reversed"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="403",description="ADMIN role required"),@ApiResponse(responseCode="404",description="Transaction not found"),@ApiResponse(responseCode="422",description="Idempotency key conflict")})
    public AdminReversalService.Receipt reverse(@PathVariable UUID transactionId,@RequestHeader("Idempotency-Key") String key,Authentication authentication){
        return reversals.reverse(UUID.fromString(authentication.getName()),key,transactionId);
    }
}
