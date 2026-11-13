package com.kennedy.wallet.controller;

import com.kennedy.wallet.domain.TransactionStatus;
import com.kennedy.wallet.domain.TransactionType;
import com.kennedy.wallet.service.AccountQueryService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {
    private final AccountQueryService queries;
    public TransactionController(AccountQueryService queries) { this.queries = queries; }
    @GetMapping @Operation(summary = "List the authenticated user's wallet transactions")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Paged transaction history"), @ApiResponse(responseCode = "400", description = "Invalid date range or filter"), @ApiResponse(responseCode = "401", description = "Authentication required")})
    public Page<AccountQueryService.TransactionView> history(Authentication authentication,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return queries.history(UUID.fromString(authentication.getName()), type, status, from, to, page, size);
    }
}
