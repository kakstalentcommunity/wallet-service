package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.ReconciliationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reconciliation")
public class ReconciliationController {
    private final ReconciliationService reconciliation;
    public ReconciliationController(ReconciliationService reconciliation) { this.reconciliation = reconciliation; }
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public List<ReconciliationService.Mismatch> reconcile() {
        return reconciliation.reconcile();
    }
}
