package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.ReconciliationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;import io.swagger.v3.oas.annotations.security.SecurityRequirement;import io.swagger.v3.oas.annotations.responses.ApiResponse;import io.swagger.v3.oas.annotations.responses.ApiResponses;import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/reconciliation")
@Tag(name="Administration") @SecurityRequirement(name="bearerAuth")
public class ReconciliationController {
    private final ReconciliationService reconciliation;
    public ReconciliationController(ReconciliationService reconciliation) { this.reconciliation = reconciliation; }
    @PostMapping @PreAuthorize("hasRole('ADMIN')") @Operation(summary="Reconcile cached account balances against ledger entries") @ApiResponses({@ApiResponse(responseCode="200",description="Detected balance mismatches"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="403",description="ADMIN role required")}) public List<ReconciliationService.Mismatch> reconcile() {
        return reconciliation.reconcile();
    }
}
