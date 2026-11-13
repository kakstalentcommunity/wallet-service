package com.kennedy.wallet.controller;

import com.kennedy.wallet.service.AccountQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {
    private final AccountQueryService queries;
    public AccountController(AccountQueryService queries) { this.queries = queries; }
    @GetMapping("/me") @Operation(summary = "Get the authenticated user's wallet balance")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Balance returned"), @ApiResponse(responseCode = "401", description = "Authentication required")})
    public AccountQueryService.AccountBalance myBalance(Authentication authentication) {
        return queries.myBalance(UUID.fromString(authentication.getName()));
    }
}
